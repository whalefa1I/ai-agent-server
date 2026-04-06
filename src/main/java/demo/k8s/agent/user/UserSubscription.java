package demo.k8s.agent.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 用户订阅实体
 *
 * 用于管理用户的订阅计划、Stripe 集成等
 */
@Entity
@Table(name = "user_subscription")
public class UserSubscription {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    /**
     * 套餐 ID (free, pro, enterprise)
     */
    @Column(name = "plan_id", length = 32, nullable = false)
    private String planId = "free";

    /**
     * 套餐名称
     */
    @Column(name = "plan_name", length = 64)
    private String planName;

    /**
     * 订阅开始时间
     */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * 订阅过期时间
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Stripe 客户 ID
     */
    @Column(name = "stripe_customer_id", length = 128)
    private String stripeCustomerId;

    /**
     * Stripe 订阅 ID
     */
    @Column(name = "stripe_subscription_id", length = 128)
    private String stripeSubscriptionId;

    /**
     * 是否自动续费
     */
    @Column(name = "auto_renew")
    private Boolean autoRenew = true;

    /**
     * 取消订阅时间
     */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * 取消订阅原因
     */
    @Column(name = "cancel_reason", length = 256)
    private String cancelReason;

    /**
     * 元数据（JSON 存储扩展信息）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private Map<String, Object> metadata;

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
    public UserSubscription() {
    }

    public UserSubscription(String userId, String planId) {
        this.userId = userId;
        this.planId = planId;
        this.startedAt = Instant.now();
    }

    public UserSubscription(String userId, String planId, String planName) {
        this.userId = userId;
        this.planId = planId;
        this.planName = planName;
        this.startedAt = Instant.now();
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public Boolean getAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(Boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
     * 检查订阅是否有效
     */
    public boolean isActive() {
        return cancelledAt == null && (expiresAt == null || Instant.now().isBefore(expiresAt));
    }

    /**
     * 检查是否是免费套餐
     */
    public boolean isFreePlan() {
        return "free".equals(planId);
    }

    /**
     * 检查是否是付费套餐
     */
    public boolean isPaidPlan() {
        return "pro".equals(planId) || "enterprise".equals(planId);
    }

    /**
     * 取消订阅
     */
    public void cancel(String reason) {
        this.cancelledAt = Instant.now();
        this.cancelReason = reason;
        this.autoRenew = false;
    }
}
