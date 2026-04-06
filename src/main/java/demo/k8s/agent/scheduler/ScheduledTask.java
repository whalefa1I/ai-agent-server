package demo.k8s.agent.scheduler;

import demo.k8s.agent.config.JsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Map;

/**
 * 定时任务实体
 */
@Entity
@Table(name = "scheduled_task", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_enabled", columnList = "enabled"),
    @Index(name = "idx_user_status", columnList = "user_id, status")
})
public class ScheduledTask {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "task_name", nullable = false, length = 128)
    private String taskName;

    @Column(name = "task_description", length = 512)
    private String taskDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskType taskType = TaskType.CHAT;

    @Column(name = "cron_expression", nullable = false, length = 64)
    private String cronExpression;

    @Column(name = "fixed_delay_ms")
    private Long fixedDelayMs;

    @Column(name = "fixed_rate_ms")
    private Long fixedRateMs;

    @Column(name = "task_payload", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> taskPayload;

    @Column(name = "timezone", length = 64)
    private String timezone = "UTC";

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "max_executions")
    private Integer maxExecutions = -1;

    @Column(name = "execution_count")
    private Integer executionCount = 0;

    @Column(name = "concurrent_execution")
    private Boolean concurrentExecution = false;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaskStatus status = TaskStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_handling", nullable = false, length = 32)
    private ErrorHandling errorHandling = ErrorHandling.CONTINUE;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "retry_delay_ms")
    private Integer retryDelayMs = 1000;

    @Column(name = "notify_on_success")
    private Boolean notifyOnSuccess = false;

    @Column(name = "notify_on_failure")
    private Boolean notifyOnFailure = true;

    @Column(name = "notification_channels", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> notificationChannels;

    @Column(name = "metadata", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    // Constructors
    public ScheduledTask() {}

    public ScheduledTask(String id, String userId, String taskName, String cronExpression, Map<String, Object> taskPayload) {
        this.id = id;
        this.userId = userId;
        this.taskName = taskName;
        this.cronExpression = cronExpression;
        this.taskPayload = taskPayload;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }

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

    public Integer getExecutionCount() { return executionCount; }
    public void setExecutionCount(Integer executionCount) { this.executionCount = executionCount; }

    public Boolean getConcurrentExecution() { return concurrentExecution; }
    public void setConcurrentExecution(Boolean concurrentExecution) { this.concurrentExecution = concurrentExecution; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public ErrorHandling getErrorHandling() { return errorHandling; }
    public void setErrorHandling(ErrorHandling errorHandling) { this.errorHandling = errorHandling; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    // Enums
    public enum TaskType {
        CHAT,       // 定时发送消息
        TOOL,       // 定时执行工具
        API,        // 定时调用 API
        CUSTOM      // 自定义任务
    }

    public enum TaskStatus {
        ACTIVE,     // 活跃状态
        PAUSED,     // 已暂停
        COMPLETED,  // 已完成
        CANCELLED,  // 已取消
        ERROR       // 错误状态
    }

    public enum ErrorHandling {
        CONTINUE,   // 继续执行下一次
        STOP,       // 停止任务
        RETRY       // 重试
    }
}
