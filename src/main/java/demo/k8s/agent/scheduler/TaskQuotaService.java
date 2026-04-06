package demo.k8s.agent.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 任务配额服务层
 */
@Service
public class TaskQuotaService {

    private static final Logger log = LoggerFactory.getLogger(TaskQuotaService.class);

    private final UserTaskQuotaRepository userQuotaRepository;
    private final PlanTaskQuotaRepository planQuotaRepository;

    public TaskQuotaService(
            UserTaskQuotaRepository userQuotaRepository,
            PlanTaskQuotaRepository planQuotaRepository) {
        this.userQuotaRepository = userQuotaRepository;
        this.planQuotaRepository = planQuotaRepository;
    }

    // ==================== 配额检查 ====================

    /**
     * 检查用户能否创建新任务
     */
    @Transactional(readOnly = true)
    public QuotaCheckResult canCreateTask(String userId, TaskType type) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);

        if (!quota.getEnabled()) {
            return QuotaCheckResult.blocked("用户配额已禁用");
        }

        // 检查任务数量限制
        long currentTaskCount = getCurrentTaskCount(userId);

        int maxTasks = type == TaskType.HEARTBEAT
                ? quota.getMaxHeartbeatTasks()
                : quota.getMaxScheduledTasks();

        if (currentTaskCount >= maxTasks) {
            return QuotaCheckResult.blocked(
                    String.format("已达到最大任务数限制：%d/%d", currentTaskCount, maxTasks));
        }

        // 检查 Cron 精度
        // 实际检查在创建任务时进行

        return QuotaCheckResult.allowed();
    }

    /**
     * 检查用户能否执行任务
     */
    @Transactional(readOnly = true)
    public QuotaCheckResult canExecuteTask(String userId) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);

        if (!quota.getEnabled()) {
            return QuotaCheckResult.blocked("用户配额已禁用");
        }

        // 重置每日配额（如果需要）
        resetDailyQuotasIfNeeded(quota);

        // 检查每日执行次数
        if (quota.getTasksExecutedToday() >= quota.getMaxExecutionsPerDay()) {
            return QuotaCheckResult.blocked(
                    String.format("已达到每日执行次数限制：%d/%d",
                            quota.getTasksExecutedToday(), quota.getMaxExecutionsPerDay()));
        }

        // 检查每小时执行次数
        Integer hourlyExecutions = getHourlyExecutionCount(userId);
        if (hourlyExecutions >= quota.getMaxExecutionsPerHour()) {
            return QuotaCheckResult.blocked(
                    String.format("已达到每小时执行次数限制：%d/%d",
                            hourlyExecutions, quota.getMaxExecutionsPerHour()));
        }

        return QuotaCheckResult.allowed();
    }

    /**
     * 检查 Heartbeat 间隔是否符合配额
     */
    @Transactional(readOnly = true)
    public QuotaCheckResult isValidHeartbeatInterval(String userId, long intervalMs) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);

        if (intervalMs < quota.getHeartbeatIntervalMinMs()) {
            return QuotaCheckResult.blocked(
                    String.format("心跳间隔过小：%dms < %dms (最小限制)",
                            intervalMs, quota.getHeartbeatIntervalMinMs()));
        }

        return QuotaCheckResult.allowed();
    }

    /**
     * 检查 Cron 表达式是否符合精度配额
     */
    @Transactional(readOnly = true)
    public QuotaCheckResult isValidCronPrecision(String userId, String cronExpression) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);
        UserTaskQuota.CronPrecision allowed = quota.getAllowedCronPrecision();

        // 简单检查：根据 Cron 字段数量和内容判断精度
        String[] parts = cronExpression.trim().split("\\s+");

        // 6 字段或包含秒级 = SECOND 精度
        if (parts.length >= 6 || cronExpression.startsWith("*/") && parts[0].matches("\\d+")) {
            if (allowed != UserTaskQuota.CronPrecision.SECOND) {
                return QuotaCheckResult.blocked(
                        String.format("当前套餐不支持秒级 Cron 精度 (当前：%s)", allowed));
            }
        }

        // 5 字段 = MINUTE 精度
        if (parts.length == 5) {
            if (allowed == UserTaskQuota.CronPrecision.HOUR ||
                allowed == UserTaskQuota.CronPrecision.DAY) {
                return QuotaCheckResult.blocked(
                        String.format("当前套餐不支持分钟级 Cron 精度 (当前：%s)", allowed));
            }
        }

        return QuotaCheckResult.allowed();
    }

    /**
     * 检查 Payload 大小是否符合配额
     */
    @Transactional(readOnly = true)
    public QuotaCheckResult isValidPayloadSize(String userId, int payloadSizeBytes) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);

        if (payloadSizeBytes > quota.getMaxTaskPayloadSizeBytes()) {
            return QuotaCheckResult.blocked(
                    String.format("Payload 过大：%d bytes > %d bytes (最大限制)",
                            payloadSizeBytes, quota.getMaxTaskPayloadSizeBytes()));
        }

        return QuotaCheckResult.allowed();
    }

    /**
     * 检查任务执行时长是否符合配额
     */
    @Transactional(readOnly = true)
    public QuotaCheckResult isValidTaskDuration(String userId, long durationMs) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);

        if (durationMs > quota.getMaxTaskDurationMs()) {
            return QuotaCheckResult.blocked(
                    String.format("任务执行时长过长：%dms > %dms (最大限制)",
                            durationMs, quota.getMaxTaskDurationMs()));
        }

        return QuotaCheckResult.allowed();
    }

    // ==================== 配额更新 ====================

    /**
     * 增加执行计数
     */
    @Transactional
    public void incrementExecutionCount(String userId) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);
        resetDailyQuotasIfNeeded(quota);

        quota.setTasksExecutedToday(quota.getTasksExecutedToday() + 1);
        userQuotaRepository.save(quota);

        log.debug("User {} execution count incremented to {}",
                userId, quota.getTasksExecutedToday());
    }

    /**
     * 增加通知计数
     */
    @Transactional
    public void incrementNotificationCount(String userId) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);
        resetDailyQuotasIfNeeded(quota);

        quota.setNotificationsSentToday(quota.getNotificationsSentToday() + 1);
        userQuotaRepository.save(quota);
    }

    /**
     * 记录配额超限事件
     */
    @Transactional
    public void recordQuotaExceededEvent(String userId, String eventType,
                                          int currentValue, int limitValue, String actionTaken) {
        // 需要创建 QuotaExceededEventRepository 和实体
        // 这里简化处理
        log.warn("Quota exceeded for user {}: type={}, current={}, limit={}, action={}",
                userId, eventType, currentValue, limitValue, actionTaken);
    }

    // ==================== 配额管理 ====================

    /**
     * 升级用户套餐
     */
    @Transactional
    public UserTaskQuota upgradePlan(String userId, String newPlanId) {
        PlanTaskQuota newPlan = planQuotaRepository.findById(newPlanId)
                .orElseThrow(() -> new RuntimeException("套餐不存在：" + newPlanId));

        UserTaskQuota quota = getOrCreateUserQuota(userId);

        // 应用新套餐配额
        applyPlanQuota(quota, newPlan);

        quota.setPlanId(newPlanId);
        return userQuotaRepository.save(quota);
    }

    /**
     * 获取用户配额状态
     */
    @Transactional(readOnly = true)
    public QuotaStatus getQuotaStatus(String userId) {
        UserTaskQuota quota = getOrCreateUserQuota(userId);
        resetDailyQuotasIfNeeded(quota);

        long currentTaskCount = getCurrentTaskCount(userId);
        Integer hourlyExecutions = getHourlyExecutionCount(userId);

        return new QuotaStatus(
                userId,
                quota.getPlanId(),
                currentTaskCount,
                quota.getMaxScheduledTasks(),
                quota.getTasksExecutedToday(),
                quota.getMaxExecutionsPerDay(),
                hourlyExecutions,
                quota.getMaxExecutionsPerHour(),
                quota.getNotificationsSentToday(),
                quota.getMaxNotificationsPerDay(),
                quota.getEnabled(),
                getAllowedPlans()
        );
    }

    /**
     * 获取所有可用套餐
     */
    @Transactional(readOnly = true)
    public List<PlanTaskQuota> getAllowedPlans() {
        return planQuotaRepository.findByIsActiveTrueOrderByPriceCentsAsc();
    }

    /**
     * 重置每日配额
     */
    private void resetDailyQuotasIfNeeded(UserTaskQuota quota) {
        LocalDate today = LocalDate.now();

        if (quota.getLastResetDate() == null ||
            quota.getLastResetDate().isBefore(today)) {
            quota.setTasksExecutedToday(0);
            quota.setNotificationsSentToday(0);
            quota.setLastResetDate(today);
            quota.setLastQuotasResetAt(Instant.now());
            userQuotaRepository.save(quota);

            log.info("Reset daily quotas for user {}", quota.getUserId());
        }
    }

    /**
     * 获取或创建用户配额
     */
    private UserTaskQuota getOrCreateUserQuota(String userId) {
        return userQuotaRepository.findByUserId(userId)
                .orElseGet(() -> {
                    // 从默认套餐创建配额
                    PlanTaskQuota defaultPlan = planQuotaRepository.findById("free")
                            .orElseThrow(() -> new RuntimeException("默认套餐不存在"));

                    UserTaskQuota quota = new UserTaskQuota(userId);
                    applyPlanQuota(quota, defaultPlan);
                    return userQuotaRepository.save(quota);
                });
    }

    /**
     * 应用套餐配额到用户
     */
    private void applyPlanQuota(UserTaskQuota userQuota, PlanTaskQuota planQuota) {
        userQuota.setMaxScheduledTasks(planQuota.getMaxScheduledTasks());
        userQuota.setMaxScheduledTasksExecuting(planQuota.getMaxScheduledTasksExecuting());
        userQuota.setMaxHeartbeatTasks(planQuota.getMaxHeartbeatTasks());
        userQuota.setHeartbeatIntervalMinMs(planQuota.getHeartbeatIntervalMinMs());
        userQuota.setMaxExecutionsPerDay(planQuota.getMaxExecutionsPerDay());
        userQuota.setMaxExecutionsPerHour(planQuota.getMaxExecutionsPerHour());
        userQuota.setMaxTaskDurationMs(planQuota.getMaxTaskDurationMs());
        userQuota.setMaxTaskPayloadSizeBytes(planQuota.getMaxTaskPayloadSizeBytes());
        userQuota.setAllowedCronPrecision(planQuota.getAllowedCronPrecision());
        userQuota.setMaxNotificationsPerDay(planQuota.getMaxNotificationsPerDay());
    }

    /**
     * 获取当前任务数量（需要集成 ScheduledTaskRepository）
     */
    private long getCurrentTaskCount(String userId) {
        // 实际实现需要查询数据库
        // 这里返回 0 作为占位
        return 0;
    }

    /**
     * 获取每小时执行次数（需要集成 TaskExecutionHistoryRepository）
     */
    private Integer getHourlyExecutionCount(String userId) {
        // 实际实现需要查询数据库
        // 这里返回 0 作为占位
        return 0;
    }

    // ==================== DTO 类 ====================

    public enum TaskType {
        SCHEDULED,
        HEARTBEAT
    }

    public static class QuotaCheckResult {
        private final boolean allowed;
        private final String reason;

        private QuotaCheckResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static QuotaCheckResult allowed() {
            return new QuotaCheckResult(true, null);
        }

        public static QuotaCheckResult blocked(String reason) {
            return new QuotaCheckResult(false, reason);
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
    }

    public static class QuotaStatus {
        private final String userId;
        private final String planId;
        private final long currentTasks;
        private final int maxTasks;
        private final int executionsToday;
        private final int maxExecutionsPerDay;
        private final int executionsThisHour;
        private final int maxExecutionsPerHour;
        private final int notificationsToday;
        private final int maxNotificationsPerDay;
        private final boolean enabled;
        private final List<PlanTaskQuota> availablePlans;

        public QuotaStatus(String userId, String planId, long currentTasks, int maxTasks,
                           int executionsToday, int maxExecutionsPerDay,
                           int executionsThisHour, int maxExecutionsPerHour,
                           int notificationsToday, int maxNotificationsPerDay,
                           boolean enabled, List<PlanTaskQuota> availablePlans) {
            this.userId = userId;
            this.planId = planId;
            this.currentTasks = currentTasks;
            this.maxTasks = maxTasks;
            this.executionsToday = executionsToday;
            this.maxExecutionsPerDay = maxExecutionsPerDay;
            this.executionsThisHour = executionsThisHour;
            this.maxExecutionsPerHour = maxExecutionsPerHour;
            this.notificationsToday = notificationsToday;
            this.maxNotificationsPerDay = maxNotificationsPerDay;
            this.enabled = enabled;
            this.availablePlans = availablePlans;
        }

        public String getUserId() { return userId; }
        public String getPlanId() { return planId; }
        public long getCurrentTasks() { return currentTasks; }
        public int getMaxTasks() { return maxTasks; }
        public int getExecutionsToday() { return executionsToday; }
        public int getMaxExecutionsPerDay() { return maxExecutionsPerDay; }
        public int getExecutionsThisHour() { return executionsThisHour; }
        public int getMaxExecutionsPerHour() { return maxExecutionsPerHour; }
        public int getNotificationsToday() { return notificationsToday; }
        public int getMaxNotificationsPerDay() { return maxNotificationsPerDay; }
        public boolean isEnabled() { return enabled; }
        public List<PlanTaskQuota> getAvailablePlans() { return availablePlans; }

        /**
         * 计算任务使用率百分比
         */
        public double getTasksUsagePercent() {
            return maxTasks > 0 ? (currentTasks * 100.0 / maxTasks) : 0;
        }

        /**
         * 计算执行使用率百分比
         */
        public double getExecutionsUsagePercent() {
            return maxExecutionsPerDay > 0 ? (executionsToday * 100.0 / maxExecutionsPerDay) : 0;
        }
    }
}
