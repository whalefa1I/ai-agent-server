package demo.k8s.agent.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 用户配额实体
 *
 * 用于管理每个用户的请求配额、Token 配额、并发会话数等限制
 */
@Entity
@Table(name = "user_quota")
public class UserQuota {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

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
    private Long maxFileSizeBytes = 10485760L; // 10MB

    /**
     * 配额重置时间
     */
    @Column(name = "quota_reset_at")
    private Instant quotaResetAt;

    /**
     * 今日已用请求数
     */
    @Column(name = "requests_used_today", nullable = false)
    private Integer requestsUsedToday = 0;

    /**
     * 今日已用 Token 数
     */
    @Column(name = "tokens_used_today", nullable = false)
    private Integer tokensUsedToday = 0;

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

    //  constructors
    public UserQuota() {
    }

    public UserQuota(String userId) {
        this.userId = userId;
    }

    public UserQuota(String userId, Integer maxRequestsPerDay, Integer maxTokensPerDay,
                     Integer maxConcurrentSessions, Long maxFileSizeBytes) {
        this.userId = userId;
        this.maxRequestsPerDay = maxRequestsPerDay;
        this.maxTokensPerDay = maxTokensPerDay;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public Instant getQuotaResetAt() {
        return quotaResetAt;
    }

    public void setQuotaResetAt(Instant quotaResetAt) {
        this.quotaResetAt = quotaResetAt;
    }

    public Integer getRequestsUsedToday() {
        return requestsUsedToday;
    }

    public void setRequestsUsedToday(Integer requestsUsedToday) {
        this.requestsUsedToday = requestsUsedToday;
    }

    public Integer getTokensUsedToday() {
        return tokensUsedToday;
    }

    public void setTokensUsedToday(Integer tokensUsedToday) {
        this.tokensUsedToday = tokensUsedToday;
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
     * 检查并增加请求计数
     * @return true 如果未超限，false 如果已超限
     */
    public boolean tryIncrementRequest() {
        if (requestsUsedToday >= maxRequestsPerDay) {
            return false;
        }
        requestsUsedToday++;
        return true;
    }

    /**
     * 检查并增加 Token 计数
     * @param tokens 要增加的 Token 数
     * @return true 如果未超限，false 如果已超限
     */
    public boolean tryIncrementTokens(int tokens) {
        if (tokensUsedToday + tokens > maxTokensPerDay) {
            return false;
        }
        tokensUsedToday += tokens;
        return true;
    }

    /**
     * 重置每日配额
     */
    public void resetDailyQuota() {
        this.requestsUsedToday = 0;
        this.tokensUsedToday = 0;
        this.quotaResetAt = Instant.now().plusSeconds(86400); // 24 小时后
    }

    /**
     * 检查配额是否需要重置
     */
    public boolean needsReset() {
        return quotaResetAt != null && Instant.now().isAfter(quotaResetAt);
    }

    /**
     * 获取配额使用百分比
     */
    public double getRequestUsagePercent() {
        return (double) requestsUsedToday / maxRequestsPerDay * 100;
    }

    public double getTokenUsagePercent() {
        return (double) tokensUsedToday / maxTokensPerDay * 100;
    }
}
