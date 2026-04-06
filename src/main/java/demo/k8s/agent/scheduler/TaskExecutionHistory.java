package demo.k8s.agent.scheduler;

import demo.k8s.agent.config.JsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Map;

/**
 * 任务执行历史实体
 */
@Entity
@Table(name = "task_execution_history", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_scheduled_time", columnList = "scheduled_time"),
    @Index(name = "idx_user_status", columnList = "user_id, status")
})
public class TaskExecutionHistory {

    @Id
    private String id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "scheduled_time")
    private Instant scheduledTime;

    @Column(name = "actual_start_time")
    private Instant actualStartTime;

    @Column(name = "actual_end_time")
    private Instant actualEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Column(name = "result", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> result;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "is_retry")
    private Boolean isRetry = false;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "retry_of_id", length = 64)
    private String retryOfId;

    @Column(name = "execution_context", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> executionContext;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // Constructors
    public TaskExecutionHistory() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

    public Instant getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(Instant actualStartTime) { this.actualStartTime = actualStartTime; }

    public Instant getActualEndTime() { return actualEndTime; }
    public void setActualEndTime(Instant actualEndTime) { this.actualEndTime = actualEndTime; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorStackTrace() { return errorStackTrace; }
    public void setErrorStackTrace(String errorStackTrace) { this.errorStackTrace = errorStackTrace; }

    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Boolean getIsRetry() { return isRetry; }
    public void setIsRetry(Boolean isRetry) { this.isRetry = isRetry; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getRetryOfId() { return retryOfId; }
    public void setRetryOfId(String retryOfId) { this.retryOfId = retryOfId; }

    public Map<String, Object> getExecutionContext() { return executionContext; }
    public void setExecutionContext(Map<String, Object> executionContext) { this.executionContext = executionContext; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // Enum
    public enum ExecutionStatus {
        PENDING,    // 等待执行
        RUNNING,    // 执行中
        SUCCESS,    // 执行成功
        FAILED,     // 执行失败
        CANCELLED,  // 已取消
        TIMEOUT     // 超时
    }
}
