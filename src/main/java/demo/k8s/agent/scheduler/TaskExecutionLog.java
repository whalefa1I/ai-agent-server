package demo.k8s.agent.scheduler;

import demo.k8s.agent.config.JsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Map;

/**
 * 任务执行日志实体
 */
@Entity
@Table(name = "task_execution_log", indexes = {
    @Index(name = "idx_execution_id", columnList = "execution_id"),
    @Index(name = "idx_task_id", columnList = "task_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_logged_at", columnList = "logged_at")
})
public class TaskExecutionLog {

    @Id
    private String id;

    @Column(name = "execution_id", nullable = false, length = 64)
    private String executionId;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "log_level", length = 16)
    private String logLevel = "INFO";

    @Column(name = "log_message", nullable = false, columnDefinition = "TEXT")
    private String logMessage;

    @Column(name = "log_data", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> logData;

    @CreationTimestamp
    @Column(name = "logged_at", updatable = false)
    private Instant loggedAt;

    // Constructors
    public TaskExecutionLog() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

    public String getLogMessage() { return logMessage; }
    public void setLogMessage(String logMessage) { this.logMessage = logMessage; }

    public Map<String, Object> getLogData() { return logData; }
    public void setLogData(Map<String, Object> logData) { this.logData = logData; }

    public Instant getLoggedAt() { return loggedAt; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
}
