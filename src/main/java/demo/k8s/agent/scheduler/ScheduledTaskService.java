package demo.k8s.agent.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务服务层
 */
@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final ScheduledTaskRepository taskRepository;
    private final TaskExecutionHistoryRepository executionHistoryRepository;
    private final TaskExecutionLogRepository executionLogRepository;
    private final ObjectMapper objectMapper;
    private final TaskQuotaService quotaService;

    // 内存中的调度任务映射 (taskId -> ScheduledFuture)
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public ScheduledTaskService(
            ScheduledTaskRepository taskRepository,
            TaskExecutionHistoryRepository executionHistoryRepository,
            TaskExecutionLogRepository executionLogRepository,
            ObjectMapper objectMapper,
            TaskQuotaService quotaService) {
        this.taskRepository = taskRepository;
        this.executionHistoryRepository = executionHistoryRepository;
        this.executionLogRepository = executionLogRepository;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
    }

    // ==================== 任务 CRUD ====================

    /**
     * 创建定时任务
     */
    @Transactional
    public ScheduledTask createTask(TaskCreateRequest request, String userId) {
        // 配额检查 1: 能否创建新任务
        TaskQuotaService.TaskType quotaTaskType = TaskQuotaService.TaskType.SCHEDULED;
        TaskQuotaService.QuotaCheckResult createCheck = quotaService.canCreateTask(userId, quotaTaskType);
        if (!createCheck.isAllowed()) {
            throw new RuntimeException("配额检查失败：" + createCheck.getReason());
        }

        // 配额检查 2: Cron 精度
        if (request.getCronExpression() != null) {
            TaskQuotaService.QuotaCheckResult cronCheck = quotaService.isValidCronPrecision(userId, request.getCronExpression());
            if (!cronCheck.isAllowed()) {
                throw new RuntimeException("Cron 精度不符合配额：" + cronCheck.getReason());
            }
        }

        // 配额检查 3: Payload 大小
        if (request.getTaskPayload() != null) {
            try {
                int payloadSize = objectMapper.writeValueAsString(request.getTaskPayload()).length();
                TaskQuotaService.QuotaCheckResult payloadCheck = quotaService.isValidPayloadSize(userId, payloadSize);
                if (!payloadCheck.isAllowed()) {
                    throw new RuntimeException("Payload 大小不符合配额：" + payloadCheck.getReason());
                }
            } catch (Exception e) {
                log.warn("Failed to calculate payload size", e);
            }
        }

        String taskId = UUID.randomUUID().toString();

        ScheduledTask task = new ScheduledTask();
        task.setId(taskId);
        task.setUserId(userId);
        task.setSessionId(request.getSessionId());
        task.setTaskName(request.getTaskName());
        task.setTaskDescription(request.getTaskDescription());
        task.setTaskType(request.getTaskType() != null ? request.getTaskType() : ScheduledTask.TaskType.CHAT);
        task.setCronExpression(request.getCronExpression());
        task.setFixedDelayMs(request.getFixedDelayMs());
        task.setFixedRateMs(request.getFixedRateMs());
        task.setTaskPayload(request.getTaskPayload());
        task.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
        task.setStartAt(request.getStartAt());
        task.setEndAt(request.getEndAt());
        task.setMaxExecutions(request.getMaxExecutions() != null ? request.getMaxExecutions() : -1);
        task.setExecutionCount(0);
        task.setConcurrentExecution(request.getConcurrentExecution() != null ? request.getConcurrentExecution() : false);
        task.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        task.setStatus(ScheduledTask.TaskStatus.ACTIVE);
        task.setErrorHandling(request.getErrorHandling() != null ? request.getErrorHandling() : ScheduledTask.ErrorHandling.CONTINUE);
        task.setMaxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3);
        task.setRetryDelayMs(request.getRetryDelayMs() != null ? request.getRetryDelayMs() : 1000);
        task.setNotifyOnSuccess(request.getNotifyOnSuccess() != null ? request.getNotifyOnSuccess() : false);
        task.setNotifyOnFailure(request.getNotifyOnFailure() != null ? request.getNotifyOnFailure() : true);
        task.setNotificationChannels(request.getNotificationChannels());
        task.setMetadata(request.getMetadata());
        task.setCreatedBy(userId);

        ScheduledTask saved = taskRepository.save(task);
        log.info("Created scheduled task: {} for user: {}", taskId, userId);

        // 自动调度任务
        if (task.getEnabled()) {
            scheduleTask(task);
        }

        return saved;
    }

    /**
     * 更新定时任务
     */
    @Transactional
    public ScheduledTask updateTask(String taskId, TaskUpdateRequest request, String userId) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: Task belongs to another user");
        }

        // 更新字段
        if (request.getTaskName() != null) {
            task.setTaskName(request.getTaskName());
        }
        if (request.getTaskDescription() != null) {
            task.setTaskDescription(request.getTaskDescription());
        }
        if (request.getCronExpression() != null) {
            task.setCronExpression(request.getCronExpression());
        }
        if (request.getFixedDelayMs() != null) {
            task.setFixedDelayMs(request.getFixedDelayMs());
        }
        if (request.getFixedRateMs() != null) {
            task.setFixedRateMs(request.getFixedRateMs());
        }
        if (request.getTaskPayload() != null) {
            task.setTaskPayload(request.getTaskPayload());
        }
        if (request.getTimezone() != null) {
            task.setTimezone(request.getTimezone());
        }
        if (request.getStartAt() != null) {
            task.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            task.setEndAt(request.getEndAt());
        }
        if (request.getMaxExecutions() != null) {
            task.setMaxExecutions(request.getMaxExecutions());
        }
        if (request.getConcurrentExecution() != null) {
            task.setConcurrentExecution(request.getConcurrentExecution());
        }
        if (request.getErrorHandling() != null) {
            task.setErrorHandling(request.getErrorHandling());
        }
        if (request.getMaxRetries() != null) {
            task.setMaxRetries(request.getMaxRetries());
        }
        if (request.getRetryDelayMs() != null) {
            task.setRetryDelayMs(request.getRetryDelayMs());
        }
        if (request.getNotifyOnSuccess() != null) {
            task.setNotifyOnSuccess(request.getNotifyOnSuccess());
        }
        if (request.getNotifyOnFailure() != null) {
            task.setNotifyOnFailure(request.getNotifyOnFailure());
        }
        if (request.getNotificationChannels() != null) {
            task.setNotificationChannels(request.getNotificationChannels());
        }
        if (request.getMetadata() != null) {
            task.setMetadata(request.getMetadata());
        }

        ScheduledTask saved = taskRepository.save(task);
        log.info("Updated scheduled task: {} for user: {}", taskId, userId);

        // 重新调度任务
        unscheduleTask(taskId);
        if (task.getEnabled() && task.getStatus() == ScheduledTask.TaskStatus.ACTIVE) {
            scheduleTask(task);
        }

        return saved;
    }

    /**
     * 删除定时任务
     */
    @Transactional
    public void deleteTask(String taskId, String userId) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: Task belongs to another user");
        }

        // 取消调度
        unscheduleTask(taskId);

        taskRepository.delete(task);
        log.info("Deleted scheduled task: {} for user: {}", taskId, userId);
    }

    /**
     * 获取任务详情
     */
    @Transactional(readOnly = true)
    public ScheduledTask getTask(String taskId, String userId) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: Task belongs to another user");
        }

        return task;
    }

    /**
     * 分页查询用户任务
     */
    @Transactional(readOnly = true)
    public Page<ScheduledTask> getUserTasks(String userId, int page, int size, String sortBy, String direction) {
        Sort sort = createSort(sortBy, direction);
        Pageable pageable = PageRequest.of(page, size, sort);
        return taskRepository.findByUserId(userId, pageable);
    }

    /**
     * 查询用户所有任务（不分页）
     */
    @Transactional(readOnly = true)
    public List<ScheduledTask> getAllUserTasks(String userId) {
        return taskRepository.findByUserIdAndEnabledTrue(userId);
    }

    // ==================== 任务控制 ====================

    /**
     * 启用任务
     */
    @Transactional
    public ScheduledTask enableTask(String taskId, String userId) {
        return updateTaskStatus(taskId, userId, true, ScheduledTask.TaskStatus.ACTIVE);
    }

    /**
     * 暂停任务
     */
    @Transactional
    public ScheduledTask pauseTask(String taskId, String userId) {
        ScheduledTask task = updateTaskStatus(taskId, userId, false, ScheduledTask.TaskStatus.PAUSED);
        unscheduleTask(taskId);
        return task;
    }

    /**
     * 恢复任务
     */
    @Transactional
    public ScheduledTask resumeTask(String taskId, String userId) {
        ScheduledTask task = updateTaskStatus(taskId, userId, true, ScheduledTask.TaskStatus.ACTIVE);
        scheduleTask(task);
        return task;
    }

    /**
     * 取消任务
     */
    @Transactional
    public ScheduledTask cancelTask(String taskId, String userId) {
        ScheduledTask task = updateTaskStatus(taskId, userId, false, ScheduledTask.TaskStatus.CANCELLED);
        unscheduleTask(taskId);
        return task;
    }

    private ScheduledTask updateTaskStatus(String taskId, String userId, boolean enabled, ScheduledTask.TaskStatus status) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: Task belongs to another user");
        }

        task.setEnabled(enabled);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    // ==================== 试运行 ====================

    /**
     * 试运行任务（不创建定时调度）
     */
    @Transactional
    public TaskExecutionResult dryRunTask(String taskId, String userId) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: Task belongs to another user");
        }

        log.info("Dry run task: {} for user: {}", taskId, userId);
        return executeTaskInternal(task, true);
    }

    // ==================== 执行历史 ====================

    /**
     * 查询任务执行历史
     */
    @Transactional(readOnly = true)
    public Page<TaskExecutionHistory> getTaskExecutionHistory(String taskId, String userId, int page, int size) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: Task belongs to another user");
        }

        return executionHistoryRepository.findByTaskId(taskId, PageRequest.of(page, size));
    }

    /**
     * 查询用户执行历史
     */
    @Transactional(readOnly = true)
    public Page<TaskExecutionHistory> getUserExecutionHistory(String userId, int page, int size) {
        return executionHistoryRepository.findByUserId(userId, PageRequest.of(page, size));
    }

    /**
     * 获取执行日志
     */
    @Transactional(readOnly = true)
    public List<TaskExecutionLog> getExecutionLogs(String executionId) {
        return executionLogRepository.findByExecutionIdOrderByLoggedAt(executionId);
    }

    // ==================== 统计信息 ====================

    /**
     * 获取用户任务统计
     */
    @Transactional(readOnly = true)
    public TaskStatistics getUserStatistics(String userId) {
        long totalTasks = taskRepository.countByUserId(userId);
        long activeTasks = taskRepository.countByUserIdAndStatus(userId, ScheduledTask.TaskStatus.ACTIVE);
        long pausedTasks = taskRepository.countByUserIdAndStatus(userId, ScheduledTask.TaskStatus.PAUSED);
        long totalExecutions = executionHistoryRepository.countByUserId(userId);
        Double successRate = executionHistoryRepository.getSuccessRateByUser(userId);

        return new TaskStatistics(
                userId,
                totalTasks,
                activeTasks,
                pausedTasks,
                totalExecutions,
                successRate != null ? successRate : 0.0
        );
    }

    /**
     * 获取所有用户任务汇总统计
     */
    @Transactional(readOnly = true)
    public AggregateStatistics getAggregateStatistics() {
        long totalTasks = taskRepository.count();
        long enabledTasks = taskRepository.findByEnabledTrue().size();
        long totalExecutions = executionHistoryRepository.count();

        // 按状态统计
        Map<String, Long> tasksByStatus = new HashMap<>();
        for (ScheduledTask task : taskRepository.findAll()) {
            String status = task.getStatus().name();
            tasksByStatus.put(status, tasksByStatus.getOrDefault(status, 0L) + 1);
        }

        // 按类型统计
        Map<String, Long> tasksByType = new HashMap<>();
        for (Object[] result : taskRepository.countTaskType()) {
            String type = ((ScheduledTask.TaskType) result[0]).name();
            Long count = (Long) result[1];
            tasksByType.put(type, count);
        }

        // 按用户统计
        Map<String, Map<String, Long>> tasksByUser = new HashMap<>();
        for (Object[] result : taskRepository.countTaskStatusByUser()) {
            String userId = (String) result[0];
            String status = ((ScheduledTask.TaskStatus) result[1]).name();
            Long count = (Long) result[2];

            tasksByUser.computeIfAbsent(userId, k -> new HashMap<>()).put(status, count);
        }

        return new AggregateStatistics(
                totalTasks,
                enabledTasks,
                totalExecutions,
                tasksByStatus,
                tasksByType,
                tasksByUser
        );
    }

    // ==================== 内部方法 ====================

    /**
     * 调度任务
     */
    private void scheduleTask(ScheduledTask task) {
        // TODO: 实现任务调度逻辑
        log.info("Scheduling task: {} with cron: {}", task.getId(), task.getCronExpression());
        // 实际实现需要集成 Spring @Scheduled 或 Quartz
    }

    /**
     * 取消调度任务
     */
    private void unscheduleTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("Uns scheduled task: {}", taskId);
        }
    }

    /**
     * 执行任务内部实现
     */
    private TaskExecutionResult executeTaskInternal(ScheduledTask task, boolean dryRun) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        // 创建执行记录
        TaskExecutionHistory execution = new TaskExecutionHistory();
        execution.setId(executionId);
        execution.setTaskId(task.getId());
        execution.setUserId(task.getUserId());
        execution.setScheduledTime(Instant.now());
        execution.setActualStartTime(startTime);
        execution.setStatus(dryRun ? TaskExecutionHistory.ExecutionStatus.SUCCESS : TaskExecutionHistory.ExecutionStatus.RUNNING);
        execution.setIsRetry(dryRun);

        try {
            // 设置 TraceContext
            TraceContext.setUserId(task.getUserId());
            TraceContext.setSessionId(task.getSessionId());

            // 根据任务类型执行
            Object result = executeTaskByType(task);

            Instant endTime = Instant.now();
            execution.setActualEndTime(endTime);
            execution.setStatus(TaskExecutionHistory.ExecutionStatus.SUCCESS);
            execution.setResult(objectMapper.convertValue(result, Map.class));
            execution.setDurationMs(Duration.between(startTime, endTime).toMillis());

            if (!dryRun) {
                executionHistoryRepository.save(execution);
                updateTaskExecutionCount(task.getId(), task.getExecutionCount() + 1);
            }

            logExecutionSuccess(task, executionId, dryRun);
            return new TaskExecutionResult(true, executionId, result, execution.getDurationMs());

        } catch (Exception e) {
            Instant endTime = Instant.now();
            execution.setActualEndTime(endTime);
            execution.setStatus(TaskExecutionHistory.ExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setErrorStackTrace(getStackTrace(e));
            execution.setDurationMs(Duration.between(startTime, endTime).toMillis());

            if (!dryRun) {
                executionHistoryRepository.save(execution);
            }

            logExecutionFailure(task, executionId, e, dryRun);
            return new TaskExecutionResult(false, executionId, null, execution.getDurationMs(), e.getMessage());
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 根据任务类型执行
     */
    private Object executeTaskByType(ScheduledTask task) {
        switch (task.getTaskType()) {
            case CHAT:
                return executeChatTask(task);
            case TOOL:
                return executeToolTask(task);
            case API:
                return executeApiTask(task);
            default:
                throw new RuntimeException("Unknown task type: " + task.getTaskType());
        }
    }

    /**
     * 执行聊天任务
     */
    private Object executeChatTask(ScheduledTask task) {
        // TODO: 集成到 Agent 聊天系统
        Map<String, Object> payload = task.getTaskPayload();
        String message = payload != null ? (String) payload.get("message") : "";
        log.info("Executing chat task: {} for user: {}", task.getTaskName(), task.getUserId());
        return Map.of("message_sent", message);
    }

    /**
     * 执行工具任务
     */
    private Object executeToolTask(ScheduledTask task) {
        // TODO: 集成到工具系统
        Map<String, Object> payload = task.getTaskPayload();
        log.info("Executing tool task: {} for user: {}", task.getTaskName(), task.getUserId());
        return Map.of("tool_executed", payload != null ? payload.get("tool_name") : "unknown");
    }

    /**
     * 执行 API 任务
     */
    private Object executeApiTask(ScheduledTask task) {
        // TODO: 实现 API 调用
        Map<String, Object> payload = task.getTaskPayload();
        log.info("Executing API task: {} for user: {}", task.getTaskName(), task.getUserId());
        return Map.of("api_called", payload != null ? payload.get("endpoint") : "unknown");
    }

    /**
     * 更新任务执行次数
     */
    private void updateTaskExecutionCount(String taskId, int newCount) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setExecutionCount(newCount);
            // 检查是否达到最大执行次数
            if (task.getMaxExecutions() > 0 && newCount >= task.getMaxExecutions()) {
                task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
                task.setEnabled(false);
            }
            taskRepository.save(task);
        });
    }

    private void logExecutionSuccess(ScheduledTask task, String executionId, boolean dryRun) {
        log.info("Task {} executed successfully. Execution ID: {}, Dry run: {}",
                task.getTaskName(), executionId, dryRun);
    }

    private void logExecutionFailure(ScheduledTask task, String executionId, Exception e, boolean dryRun) {
        log.error("Task {} failed. Execution ID: {}, Dry run: {}, Error: {}",
                task.getTaskName(), executionId, dryRun, e.getMessage());
    }

    private String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    private Sort createSort(String sortBy, String direction) {
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "createdAt";
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, sortBy);
    }

    // ==================== DTO 类 ====================

    public static class TaskCreateRequest {
        private String sessionId;
        private String taskName;
        private String taskDescription;
        private ScheduledTask.TaskType taskType;
        private String cronExpression;
        private Long fixedDelayMs;
        private Long fixedRateMs;
        private Map<String, Object> taskPayload;
        private String timezone;
        private Instant startAt;
        private Instant endAt;
        private Integer maxExecutions;
        private Boolean concurrentExecution;
        private Boolean enabled;
        private ScheduledTask.ErrorHandling errorHandling;
        private Integer maxRetries;
        private Integer retryDelayMs;
        private Boolean notifyOnSuccess;
        private Boolean notifyOnFailure;
        private Map<String, Object> notificationChannels;
        private Map<String, Object> metadata;

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }

        public String getTaskDescription() { return taskDescription; }
        public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

        public ScheduledTask.TaskType getTaskType() { return taskType; }
        public void setTaskType(ScheduledTask.TaskType taskType) { this.taskType = taskType; }

        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

        public Long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(Long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }

        public Long getFixedRateMs() { return fixedRateMs; }
        public void setFixedRateMs(Long fixedRateMs) { this.fixedRateMs = fixedRateMs; }

        public Map<String, Object> getTaskPayload() { return taskPayload; }
        public void setTaskPayload(Map<String, Object> taskPayload) { this.taskPayload = taskPayload; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }

        public Instant getStartAt() { return startAt; }
        public void setStartAt(Instant startAt) { this.startAt = startAt; }

        public Instant getEndAt() { return endAt; }
        public void setEndAt(Instant endAt) { this.endAt = endAt; }

        public Integer getMaxExecutions() { return maxExecutions; }
        public void setMaxExecutions(Integer maxExecutions) { this.maxExecutions = maxExecutions; }

        public Boolean getConcurrentExecution() { return concurrentExecution; }
        public void setConcurrentExecution(Boolean concurrentExecution) { this.concurrentExecution = concurrentExecution; }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public ScheduledTask.ErrorHandling getErrorHandling() { return errorHandling; }
        public void setErrorHandling(ScheduledTask.ErrorHandling errorHandling) { this.errorHandling = errorHandling; }

        public Integer getMaxRetries() { return maxRetries; }
        public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

        public Integer getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(Integer retryDelayMs) { this.retryDelayMs = retryDelayMs; }

        public Boolean getNotifyOnSuccess() { return notifyOnSuccess; }
        public void setNotifyOnSuccess(Boolean notifyOnSuccess) { this.notifyOnSuccess = notifyOnSuccess; }

        public Boolean getNotifyOnFailure() { return notifyOnFailure; }
        public void setNotifyOnFailure(Boolean notifyOnFailure) { this.notifyOnFailure = notifyOnFailure; }

        public Map<String, Object> getNotificationChannels() { return notificationChannels; }
        public void setNotificationChannels(Map<String, Object> notificationChannels) { this.notificationChannels = notificationChannels; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class TaskUpdateRequest {
        private String taskName;
        private String taskDescription;
        private String cronExpression;
        private Long fixedDelayMs;
        private Long fixedRateMs;
        private Map<String, Object> taskPayload;
        private String timezone;
        private Instant startAt;
        private Instant endAt;
        private Integer maxExecutions;
        private Boolean concurrentExecution;
        private ScheduledTask.ErrorHandling errorHandling;
        private Integer maxRetries;
        private Integer retryDelayMs;
        private Boolean notifyOnSuccess;
        private Boolean notifyOnFailure;
        private Map<String, Object> notificationChannels;
        private Map<String, Object> metadata;

        // Getters and Setters
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }

        public String getTaskDescription() { return taskDescription; }
        public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

        public Long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(Long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }

        public Long getFixedRateMs() { return fixedRateMs; }
        public void setFixedRateMs(Long fixedRateMs) { this.fixedRateMs = fixedRateMs; }

        public Map<String, Object> getTaskPayload() { return taskPayload; }
        public void setTaskPayload(Map<String, Object> taskPayload) { this.taskPayload = taskPayload; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }

        public Instant getStartAt() { return startAt; }
        public void setStartAt(Instant startAt) { this.startAt = startAt; }

        public Instant getEndAt() { return endAt; }
        public void setEndAt(Instant endAt) { this.endAt = endAt; }

        public Integer getMaxExecutions() { return maxExecutions; }
        public void setMaxExecutions(Integer maxExecutions) { this.maxExecutions = maxExecutions; }

        public Boolean getConcurrentExecution() { return concurrentExecution; }
        public void setConcurrentExecution(Boolean concurrentExecution) { this.concurrentExecution = concurrentExecution; }

        public ScheduledTask.ErrorHandling getErrorHandling() { return errorHandling; }
        public void setErrorHandling(ScheduledTask.ErrorHandling errorHandling) { this.errorHandling = errorHandling; }

        public Integer getMaxRetries() { return maxRetries; }
        public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

        public Integer getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(Integer retryDelayMs) { this.retryDelayMs = retryDelayMs; }

        public Boolean getNotifyOnSuccess() { return notifyOnSuccess; }
        public void setNotifyOnSuccess(Boolean notifyOnSuccess) { this.notifyOnSuccess = notifyOnSuccess; }

        public Boolean getNotifyOnFailure() { return notifyOnFailure; }
        public void setNotifyOnFailure(Boolean notifyOnFailure) { this.notifyOnFailure = notifyOnFailure; }

        public Map<String, Object> getNotificationChannels() { return notificationChannels; }
        public void setNotificationChannels(Map<String, Object> notificationChannels) { this.notificationChannels = notificationChannels; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class TaskExecutionResult {
        private final boolean success;
        private final String executionId;
        private final Object result;
        private final Long durationMs;
        private final String errorMessage;

        public TaskExecutionResult(boolean success, String executionId, Object result, Long durationMs) {
            this(success, executionId, result, durationMs, null);
        }

        public TaskExecutionResult(boolean success, String executionId, Object result, Long durationMs, String errorMessage) {
            this.success = success;
            this.executionId = executionId;
            this.result = result;
            this.durationMs = durationMs;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public String getExecutionId() { return executionId; }
        public Object getResult() { return result; }
        public Long getDurationMs() { return durationMs; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class TaskStatistics {
        private final String userId;
        private final long totalTasks;
        private final long activeTasks;
        private final long pausedTasks;
        private final long totalExecutions;
        private final double successRate;

        public TaskStatistics(String userId, long totalTasks, long activeTasks, long pausedTasks, long totalExecutions, double successRate) {
            this.userId = userId;
            this.totalTasks = totalTasks;
            this.activeTasks = activeTasks;
            this.pausedTasks = pausedTasks;
            this.totalExecutions = totalExecutions;
            this.successRate = successRate;
        }

        public String getUserId() { return userId; }
        public long getTotalTasks() { return totalTasks; }
        public long getActiveTasks() { return activeTasks; }
        public long getPausedTasks() { return pausedTasks; }
        public long getTotalExecutions() { return totalExecutions; }
        public double getSuccessRate() { return successRate; }
    }

    public static class AggregateStatistics {
        private final long totalTasks;
        private final long enabledTasks;
        private final long totalExecutions;
        private final Map<String, Long> tasksByStatus;
        private final Map<String, Long> tasksByType;
        private final Map<String, Map<String, Long>> tasksByUser;

        public AggregateStatistics(long totalTasks, long enabledTasks, long totalExecutions,
                                   Map<String, Long> tasksByStatus, Map<String, Long> tasksByType,
                                   Map<String, Map<String, Long>> tasksByUser) {
            this.totalTasks = totalTasks;
            this.enabledTasks = enabledTasks;
            this.totalExecutions = totalExecutions;
            this.tasksByStatus = tasksByStatus;
            this.tasksByType = tasksByType;
            this.tasksByUser = tasksByUser;
        }

        public long getTotalTasks() { return totalTasks; }
        public long getEnabledTasks() { return enabledTasks; }
        public long getTotalExecutions() { return totalExecutions; }
        public Map<String, Long> getTasksByStatus() { return tasksByStatus; }
        public Map<String, Long> getTasksByType() { return tasksByType; }
        public Map<String, Map<String, Long>> getTasksByUser() { return tasksByUser; }
    }
}
