package demo.k8s.agent.apikey;

import demo.k8s.agent.config.JsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Map;

/**
 * API Key 配额计划实体
 */
@Entity
@Table(name = "api_key_quota_plan")
public class ApiKeyQuotaPlan {

    @Id
    @Column(length = 32)
    private String planId;

    @Column(name = "plan_name", nullable = false, length = 64)
    private String planName;

    @Column(name = "description", length = 512)
    private String description;

    // ==================== 基础配额 ====================

    @Column(name = "requests_per_day")
    private Integer requestsPerDay = 1000;

    @Column(name = "tokens_per_day")
    private Long tokensPerDay = 100000L;

    @Column(name = "compute_minutes_per_day")
    private Integer computeMinutesPerDay = 60;

    // ==================== 速率限制 ====================

    @Column(name = "rate_limit_per_second")
    private Integer rateLimitPerSecond = 10;

    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute = 100;

    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour = 1000;

    // ==================== 并发限制 ====================

    @Column(name = "max_concurrent_requests")
    private Integer maxConcurrentRequests = 5;

    // ==================== 功能权限 ====================

    @Column(name = "allowed_features", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> allowedFeatures;

    @Column(name = "blocked_features", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> blockedFeatures;

    // ==================== 核心收益点权限 ====================

    @Column(name = "premium_features", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> premiumFeatures;

    @Column(name = "premium_feature_limit", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> premiumFeatureLimit;

    // ==================== 价格 ====================

    @Column(name = "price_cents")
    private Integer priceCents = 0;

    @Column(name = "currency", length = 3)
    private String currency = "CNY";

    @Column(name = "billing_cycle", length = 16)
    private String billingCycle = "monthly";

    // ==================== 状态 ====================

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_premium")
    private Boolean isPremium = false;

    // ==================== 时间戳 ====================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public ApiKeyQuotaPlan() {}

    public ApiKeyQuotaPlan(String planId, String planName) {
        this.planId = planId;
        this.planName = planName;
    }

    // Getters and Setters
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getRequestsPerDay() { return requestsPerDay; }
    public void setRequestsPerDay(Integer requestsPerDay) { this.requestsPerDay = requestsPerDay; }

    public Long getTokensPerDay() { return tokensPerDay; }
    public void setTokensPerDay(Long tokensPerDay) { this.tokensPerDay = tokensPerDay; }

    public Integer getComputeMinutesPerDay() { return computeMinutesPerDay; }
    public void setComputeMinutesPerDay(Integer computeMinutesPerDay) { this.computeMinutesPerDay = computeMinutesPerDay; }

    public Integer getRateLimitPerSecond() { return rateLimitPerSecond; }
    public void setRateLimitPerSecond(Integer rateLimitPerSecond) { this.rateLimitPerSecond = rateLimitPerSecond; }

    public Integer getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(Integer rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public Integer getRateLimitPerHour() { return rateLimitPerHour; }
    public void setRateLimitPerHour(Integer rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }

    public Integer getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(Integer maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }

    public Map<String, Object> getAllowedFeatures() { return allowedFeatures; }
    public void setAllowedFeatures(Map<String, Object> allowedFeatures) { this.allowedFeatures = allowedFeatures; }

    public Map<String, Object> getBlockedFeatures() { return blockedFeatures; }
    public void setBlockedFeatures(Map<String, Object> blockedFeatures) { this.blockedFeatures = blockedFeatures; }

    public Map<String, Object> getPremiumFeatures() { return premiumFeatures; }
    public void setPremiumFeatures(Map<String, Object> premiumFeatures) { this.premiumFeatures = premiumFeatures; }

    public Map<String, Object> getPremiumFeatureLimit() { return premiumFeatureLimit; }
    public void setPremiumFeatureLimit(Map<String, Object> premiumFeatureLimit) { this.premiumFeatureLimit = premiumFeatureLimit; }

    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getIsPremium() { return isPremium; }
    public void setIsPremium(Boolean isPremium) { this.isPremium = isPremium; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
