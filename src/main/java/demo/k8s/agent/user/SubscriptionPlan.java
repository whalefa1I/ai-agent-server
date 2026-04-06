package demo.k8s.agent.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 订阅套餐配置实体
 *
 * 定义不同套餐的配额和限制
 */
@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlan {

    @Id
    @Column(name = "plan_id", length = 32)
    private String planId;

    /**
     * 套餐名称
     */
    @Column(name = "plan_name", length = 64, nullable = false)
    private String planName;

    /**
     * 套餐描述
     */
    @Column(name = "description", length = 256)
    private String description;

    /**
     * 每日最大请求次数
     */
    @Column(name = "max_requests_per_day", nullable = false)
    private Integer maxRequestsPerDay = 1000;

    /**
     * 每日最大 Token 使用量
     */
    @Column(name = "max_tokens_per_day", nullable = false)
    private Integer maxTokensPerDay = 100000;

    /**
     * 最大并发会话数
     */
    @Column(name = "max_concurrent_sessions", nullable = false)
    private Integer maxConcurrentSessions = 5;

    /**
     * 最大文件大小（字节）
     */
    @Column(name = "max_file_size_bytes", nullable = false)
    private Long maxFileSizeBytes = 10485760L;

    /**
     * 最大技能数量
     */
    @Column(name = "max_skills", nullable = false)
    private Integer maxSkills = 10;

    /**
     * 价格（美分）
     */
    @Column(name = "price_cents", nullable = false)
    private Integer priceCents = 0;

    /**
     * 货币类型
     */
    @Column(name = "currency", length = 3)
    private String currency = "USD";

    /**
     * 计费周期 (monthly, yearly)
     */
    @Column(name = "billing_cycle", length = 16)
    private String billingCycle = "monthly";

    /**
     * 套餐特性（JSON）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "JSON")
    private Map<String, Object> features;

    /**
     * 是否激活
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public SubscriptionPlan() {
    }

    public SubscriptionPlan(String planId, String planName, Integer maxRequestsPerDay,
                            Integer maxTokensPerDay, Integer priceCents) {
        this.planId = planId;
        this.planName = planName;
        this.maxRequestsPerDay = maxRequestsPerDay;
        this.maxTokensPerDay = maxTokensPerDay;
        this.priceCents = priceCents;
    }

    // Getters and Setters
    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMaxRequestsPerDay() {
        return maxRequestsPerDay;
    }

    public void setMaxRequestsPerDay(Integer maxRequestsPerDay) {
        this.maxRequestsPerDay = maxRequestsPerDay;
    }

    public Integer getMaxTokensPerDay() {
        return maxTokensPerDay;
    }

    public void setMaxTokensPerDay(Integer maxTokensPerDay) {
        this.maxTokensPerDay = maxTokensPerDay;
    }

    public Integer getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public void setMaxConcurrentSessions(Integer maxConcurrentSessions) {
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    public Long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(Long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public Integer getMaxSkills() {
        return maxSkills;
    }

    public void setMaxSkills(Integer maxSkills) {
        this.maxSkills = maxSkills;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Business methods

    /**
     * 获取每月价格（美元）
     */
    public double getMonthlyPriceUsd() {
        return priceCents / 100.0;
    }

    /**
     * 获取套餐等级（1=free, 2=pro, 3=enterprise）
     */
    public int getTier() {
        return switch (planId.toLowerCase()) {
            case "pro" -> 2;
            case "enterprise" -> 3;
            default -> 1;
        };
    }
}
