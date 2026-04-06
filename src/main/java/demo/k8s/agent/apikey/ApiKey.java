package demo.k8s.agent.apikey;

import demo.k8s.agent.config.JsonConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * API Key 实体
 */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // ==================== Key 信息 ====================

    @Column(name = "key_hash", nullable = false, length = 256)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_name", length = 128)
    private String keyName;

    // ==================== 密钥类型 ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 32)
    private KeyType keyType = KeyType.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_scope", nullable = false, length = 32)
    private KeyScope keyScope = KeyScope.PERSONAL;

    // ==================== 状态管理 ====================

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KeyStatus status = KeyStatus.ACTIVE;

    // ==================== 时间控制 ====================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 256)
    private String revokeReason;

    // ==================== 使用统计 ====================

    @Column(name = "total_requests")
    private Long totalRequests = 0L;

    @Column(name = "requests_today")
    private Integer requestsToday = 0;

    @Column(name = "tokens_used_today")
    private Long tokensUsedToday = 0L;

    @Column(name = "last_reset_date")
    private LocalDate lastResetDate;

    // ==================== 配额关联 ====================

    @Column(name = "quota_plan_id", length = 32)
    private String quotaPlanId = "default";

    // ==================== 权限控制 ====================

    @Column(name = "permissions", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> permissions;

    @Column(name = "allowed_endpoints", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> allowedEndpoints;

    @Column(name = "blocked_endpoints", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> blockedEndpoints;

    // ==================== 速率限制 ====================

    @Column(name = "rate_limit_per_second")
    private Integer rateLimitPerSecond = 10;

    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute = 100;

    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour = 1000;

    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay = 10000;

    // ==================== 并发控制 ====================

    @Column(name = "max_concurrent_requests")
    private Integer maxConcurrentRequests = 5;

    // ==================== IP 限制 ====================

    @Column(name = "allowed_ip_ranges", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> allowedIpRanges;

    @Column(name = "blocked_ip_ranges", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> blockedIpRanges;

    // ==================== 元数据 ====================

    @Column(name = "metadata", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public ApiKey() {}

    public ApiKey(String id, String userId, String keyHash, String keyPrefix) {
        this.id = id;
        this.userId = userId;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }

    public KeyType getKeyType() { return keyType; }
    public void setKeyType(KeyType keyType) { this.keyType = keyType; }

    public KeyScope getKeyScope() { return keyScope; }
    public void setKeyScope(KeyScope keyScope) { this.keyScope = keyScope; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public KeyStatus getStatus() { return status; }
    public void setStatus(KeyStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }

    public Long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(Long totalRequests) { this.totalRequests = totalRequests; }

    public Integer getRequestsToday() { return requestsToday; }
    public void setRequestsToday(Integer requestsToday) { this.requestsToday = requestsToday; }

    public Long getTokensUsedToday() { return tokensUsedToday; }
    public void setTokensUsedToday(Long tokensUsedToday) { this.tokensUsedToday = tokensUsedToday; }

    public LocalDate getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDate lastResetDate) { this.lastResetDate = lastResetDate; }

    public String getQuotaPlanId() { return quotaPlanId; }
    public void setQuotaPlanId(String quotaPlanId) { this.quotaPlanId = quotaPlanId; }

    public Map<String, Object> getPermissions() { return permissions; }
    public void setPermissions(Map<String, Object> permissions) { this.permissions = permissions; }

    public Map<String, Object> getAllowedEndpoints() { return allowedEndpoints; }
    public void setAllowedEndpoints(Map<String, Object> allowedEndpoints) { this.allowedEndpoints = allowedEndpoints; }

    public Map<String, Object> getBlockedEndpoints() { return blockedEndpoints; }
    public void setBlockedEndpoints(Map<String, Object> blockedEndpoints) { this.blockedEndpoints = blockedEndpoints; }

    public Integer getRateLimitPerSecond() { return rateLimitPerSecond; }
    public void setRateLimitPerSecond(Integer rateLimitPerSecond) { this.rateLimitPerSecond = rateLimitPerSecond; }

    public Integer getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(Integer rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public Integer getRateLimitPerHour() { return rateLimitPerHour; }
    public void setRateLimitPerHour(Integer rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }

    public Integer getRateLimitPerDay() { return rateLimitPerDay; }
    public void setRateLimitPerDay(Integer rateLimitPerDay) { this.rateLimitPerDay = rateLimitPerDay; }

    public Integer getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(Integer maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }

    public Map<String, Object> getAllowedIpRanges() { return allowedIpRanges; }
    public void setAllowedIpRanges(Map<String, Object> allowedIpRanges) { this.allowedIpRanges = allowedIpRanges; }

    public Map<String, Object> getBlockedIpRanges() { return blockedIpRanges; }
    public void setBlockedIpRanges(Map<String, Object> blockedIpRanges) { this.blockedIpRanges = blockedIpRanges; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // Enums
    public enum KeyType {
        USER,       // 普通用户 Key
        SERVICE,    // 服务间调用 Key
        ADMIN       // 管理员 Key
    }

    public enum KeyScope {
        PERSONAL,   // 个人使用
        ORGANIZATION, // 组织共享
        GLOBAL      // 全局访问
    }

    public enum KeyStatus {
        ACTIVE,     // 活跃
        REVOKED,    // 已吊销
        EXPIRED,    // 已过期
        SUSPENDED   // 已暂停
    }
}
