package demo.k8s.agent.scheduler;

import demo.k8s.agent.config.JsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Map;

/**
 * 套餐任务配额配置实体
 */
@Entity
@Table(name = "plan_task_quota")
public class PlanTaskQuota {

    @Id
    @Column(length = 32)
    private String planId;

    @Column(name = "plan_name", nullable = false, length = 64)
    private String planName;

    // ==================== 定时任务配额 ====================

    @Column(name = "max_scheduled_tasks")
    private Integer maxScheduledTasks = 10;

    @Column(name = "max_scheduled_tasks_executing")
    private Integer maxScheduledTasksExecuting = 3;

    // ==================== Heartbeat 任务配额 ====================

    @Column(name = "max_heartbeat_tasks")
    private Integer maxHeartbeatTasks = 5;

    @Column(name = "heartbeat_interval_min_ms")
    private Long heartbeatIntervalMinMs = 10000L;

    // ==================== 执行频率配额 ====================

    @Column(name = "max_executions_per_day")
    private Integer maxExecutionsPerDay = 100;

    @Column(name = "max_executions_per_hour")
    private Integer maxExecutionsPerHour = 20;

    // ==================== 资源配额 ====================

    @Column(name = "max_task_duration_ms")
    private Long maxTaskDurationMs = 300000L;

    @Column(name = "max_task_payload_size_bytes")
    private Integer maxTaskPayloadSizeBytes = 10240;

    // ==================== Cron 精度配额 ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "allowed_cron_precision", length = 16)
    private UserTaskQuota.CronPrecision allowedCronPrecision = UserTaskQuota.CronPrecision.MINUTE;

    // ==================== 通知配额 ====================

    @Column(name = "max_notifications_per_day")
    private Integer maxNotificationsPerDay = 50;

    // ==================== 价格 ====================

    @Column(name = "price_cents")
    private Integer priceCents = 0;

    @Column(name = "currency", length = 3)
    private String currency = "CNY";

    @Column(name = "billing_cycle", length = 16)
    private String billingCycle = "monthly";

    // ==================== 描述 ====================

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "features", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> features;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // ==================== 时间戳 ====================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public PlanTaskQuota() {}

    public PlanTaskQuota(String planId, String planName) {
        this.planId = planId;
        this.planName = planName;
    }

    // Getters and Setters
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

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

    public UserTaskQuota.CronPrecision getAllowedCronPrecision() { return allowedCronPrecision; }
    public void setAllowedCronPrecision(UserTaskQuota.CronPrecision allowedCronPrecision) { this.allowedCronPrecision = allowedCronPrecision; }

    public Integer getMaxNotificationsPerDay() { return maxNotificationsPerDay; }
    public void setMaxNotificationsPerDay(Integer maxNotificationsPerDay) { this.maxNotificationsPerDay = maxNotificationsPerDay; }

    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getFeatures() { return features; }
    public void setFeatures(Map<String, Object> features) { this.features = features; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
