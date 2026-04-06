package demo.k8s.agent.scheduler;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 用户任务配额实体
 */
@Entity
@Table(name = "user_task_quota")
public class UserTaskQuota {

    @Id
    private String userId;

    // ==================== 定时任务配额 ====================

    /**
     * 最大定时任务数量
     */
    @Column(name = "max_scheduled_tasks")
    private Integer maxScheduledTasks = 10;

    /**
     * 最大并发执行数
     */
    @Column(name = "max_scheduled_tasks_executing")
    private Integer maxScheduledTasksExecuting = 3;

    // ==================== Heartbeat 任务配额 ====================

    /**
     * 最大 Heartbeat 任务数量
     */
    @Column(name = "max_heartbeat_tasks")
    private Integer maxHeartbeatTasks = 5;

    /**
     * 最小心跳间隔 (毫秒)
     */
    @Column(name = "heartbeat_interval_min_ms")
    private Long heartbeatIntervalMinMs = 10000L;

    // ==================== 执行频率配额 ====================

    /**
     * 每日最大执行次数
     */
    @Column(name = "max_executions_per_day")
    private Integer maxExecutionsPerDay = 100;

    /**
     * 每小时最大执行次数
     */
    @Column(name = "max_executions_per_hour")
    private Integer maxExecutionsPerHour = 20;

    // ==================== 资源配额 ====================

    /**
     * 单次任务最大执行时长 (毫秒)
     */
    @Column(name = "max_task_duration_ms")
    private Long maxTaskDurationMs = 300000L;

    /**
     * 最大 Payload 大小 (字节)
     */
    @Column(name = "max_task_payload_size_bytes")
    private Integer maxTaskPayloadSizeBytes = 10240;

    // ==================== Cron 精度配额 ====================

    /**
     * 允许的 Cron 精度
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "allowed_cron_precision", length = 16)
    private CronPrecision allowedCronPrecision = CronPrecision.MINUTE;

    // ==================== 通知配额 ====================

    /**
     * 每日最大通知数
     */
    @Column(name = "max_notifications_per_day")
    private Integer maxNotificationsPerDay = 50;

    // ==================== 使用统计 ====================

    /**
     * 今日已执行任务数
     */
    @Column(name = "tasks_executed_today")
    private Integer tasksExecutedToday = 0;

    /**
     * 今日已发送通知数
     */
    @Column(name = "notifications_sent_today")
    private Integer notificationsSentToday = 0;

    /**
     * 最后重置日期
     */
    @Column(name = "last_reset_date")
    private LocalDate lastResetDate;

    // ==================== 套餐关联 ====================

    /**
     * 套餐 ID
     */
    @Column(name = "plan_id", length = 32)
    private String planId = "free";

    // ==================== 状态 ====================

    /**
     * 是否启用
     */
    @Column(name = "enabled")
    private Boolean enabled = true;

    /**
     * 配额超限处理策略
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "quota_exceeded_action", length = 32)
    private QuotaExceededAction quotaExceededAction = QuotaExceededAction.BLOCK;

    // ==================== 时间戳 ====================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_quotas_reset_at")
    private Instant lastQuotasResetAt;

    // Constructors
    public UserTaskQuota() {}

    public UserTaskQuota(String userId) {
        this.userId = userId;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Integer getMaxScheduledTasks() { return maxScheduledTasks; }
    public void setMaxScheduledTasks(Integer maxScheduledTasks) { this.maxScheduledTasks = maxScheduledTasks; }

    public Integer getMaxScheduledTasksExecuting() { return maxScheduledTasksExecuting; }
    public void setMaxScheduledTasksExecuting(Integer maxScheduledTasksExecuting) { this.maxScheduledTasksExecuting = maxScheduledTasksExecuting; }

    public Integer getMaxHeartbeatTasks() { return maxHeartbeatTasks; }
    public void setMaxHeartbeatTasks(Integer maxHeartbeatTasks) { this.maxHeartbeatTasks = maxHeartbeatTasks; }

    public Long getHeartbeatIntervalMinMs() { return heartbeatIntervalMinMs; }
    public void setHeartbeatIntervalMinMs(Long heartbeatIntervalMinMs) { this.heartbeatIntervalMinMs = heartbeatIntervalMinMs; }

    public Integer getMaxExecutionsPerDay() { return maxExecutionsPerDay; }
    public void setMaxExecutionsPerDay(Integer maxExecutionsPerDay) { this.maxExecutionsPerDay = maxExecutionsPerDay; }

    public Integer getMaxExecutionsPerHour() { return maxExecutionsPerHour; }
    public void setMaxExecutionsPerHour(Integer maxExecutionsPerHour) { this.maxExecutionsPerHour = maxExecutionsPerHour; }

    public Long getMaxTaskDurationMs() { return maxTaskDurationMs; }
    public void setMaxTaskDurationMs(Long maxTaskDurationMs) { this.maxTaskDurationMs = maxTaskDurationMs; }

    public Integer getMaxTaskPayloadSizeBytes() { return maxTaskPayloadSizeBytes; }
    public void setMaxTaskPayloadSizeBytes(Integer maxTaskPayloadSizeBytes) { this.maxTaskPayloadSizeBytes = maxTaskPayloadSizeBytes; }

    public CronPrecision getAllowedCronPrecision() { return allowedCronPrecision; }
    public void setAllowedCronPrecision(CronPrecision allowedCronPrecision) { this.allowedCronPrecision = allowedCronPrecision; }

    public Integer getMaxNotificationsPerDay() { return maxNotificationsPerDay; }
    public void setMaxNotificationsPerDay(Integer maxNotificationsPerDay) { this.maxNotificationsPerDay = maxNotificationsPerDay; }

    public Integer getTasksExecutedToday() { return tasksExecutedToday; }
    public void setTasksExecutedToday(Integer tasksExecutedToday) { this.tasksExecutedToday = tasksExecutedToday; }

    public Integer getNotificationsSentToday() { return notificationsSentToday; }
    public void setNotificationsSentToday(Integer notificationsSentToday) { this.notificationsSentToday = notificationsSentToday; }

    public LocalDate getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDate lastResetDate) { this.lastResetDate = lastResetDate; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public QuotaExceededAction getQuotaExceededAction() { return quotaExceededAction; }
    public void setQuotaExceededAction(QuotaExceededAction quotaExceededAction) { this.quotaExceededAction = quotaExceededAction; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastQuotasResetAt() { return lastQuotasResetAt; }
    public void setLastQuotasResetAt(Instant lastQuotasResetAt) { this.lastQuotasResetAt = lastQuotasResetAt; }

    // Enums
    public enum CronPrecision {
        SECOND,   // 秒级精度 (企业版)
        MINUTE,   // 分钟级精度 (专业版)
        HOUR,     // 小时级精度 (免费版)
        DAY       // 天级精度 (受限免费版)
    }

    public enum QuotaExceededAction {
        BLOCK,    // 阻止新任务
        QUEUE,    // 加入队列等待
        NOTIFY    // 仅通知用户
    }
}
