package demo.k8s.agent.apikey;

import demo.k8s.agent.auth.ApiKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API Key 服务层
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyQuotaPlanRepository quotaPlanRepository;

    // 内存中的并发请求计数 (key: apiKeyId)
    private final Map<String, AtomicInteger> concurrentRequests = new ConcurrentHashMap<>();

    // 内存中的速率限制桶 (key: apiKeyId:bucketType:bucketWindow)
    private final Map<String, RateLimitBucket> rateLimitBuckets = new ConcurrentHashMap<>();

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            ApiKeyQuotaPlanRepository quotaPlanRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.quotaPlanRepository = quotaPlanRepository;
    }

    // ==================== API Key 生成和管理 ====================

    /**
     * 生成新的 API Key
     */
    @Transactional
    public ApiKeyCreateResult createApiKey(String userId, ApiKeyCreateRequest request) {
        // 生成原始 Key
        String rawKey = ApiKeyGenerator.generateSecureKey(32);
        String keyHash = BCrypt.hashpw(rawKey, BCrypt.gensalt(12));
        String keyPrefix = "sk-" + rawKey.substring(0, 8);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID().toString());
        apiKey.setUserId(userId);
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setKeyName(request.getKeyName());
        apiKey.setKeyType(request.getKeyType() != null ? request.getKeyType() : ApiKey.KeyType.USER);
        apiKey.setKeyScope(request.getKeyScope() != null ? request.getKeyScope() : ApiKey.KeyScope.PERSONAL);

        // 设置配额计划
        String planId = request.getQuotaPlanId() != null ? request.getQuotaPlanId() : "default";
        ApiKeyQuotaPlan plan = quotaPlanRepository.findById(planId).orElse(null);

        // 如果配额计划不存在，使用 null（后续会应用默认值或自定义速率限制）
        if (plan == null) {
            log.warn("配额计划 [{}] 不存在，使用默认配置", planId);
        }

        apiKey.setQuotaPlanId(planId);

        // 应用配额计划限制（如果计划存在）
        if (plan != null) {
            applyQuotaPlan(apiKey, plan);
        }

        // 如果有自定义速率限制，覆盖配额计划（用于匿名 Key）
        if (request.getRateLimitPerSecond() != null) {
            apiKey.setRateLimitPerSecond(request.getRateLimitPerSecond());
        }
        if (request.getRateLimitPerMinute() != null) {
            apiKey.setRateLimitPerMinute(request.getRateLimitPerMinute());
        }
        if (request.getRateLimitPerHour() != null) {
            apiKey.setRateLimitPerHour(request.getRateLimitPerHour());
        }

        // 设置过期时间
        if (request.getExpiresAt() != null) {
            apiKey.setExpiresAt(request.getExpiresAt());
        }

        // 设置权限
        apiKey.setPermissions(request.getPermissions());
        apiKey.setAllowedEndpoints(request.getAllowedEndpoints());
        apiKey.setBlockedEndpoints(request.getBlockedEndpoints());

        // IP 限制
        apiKey.setAllowedIpRanges(request.getAllowedIpRanges());
        apiKey.setBlockedIpRanges(request.getBlockedIpRanges());

        apiKey.setMetadata(request.getMetadata());
        apiKey.setCreatedBy(userId);

        ApiKey saved = apiKeyRepository.save(apiKey);

        log.info("Created API Key for user {}: prefix={}", userId, keyPrefix);

        // 返回结果 (只返回一次原始 Key)
        return new ApiKeyCreateResult(saved, rawKey);
    }

    /**
     * 吊销 API Key
     */
    @Transactional
    public ApiKey revokeApiKey(String apiKeyId, String userId, String reason) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new RuntimeException("API Key 不存在：" + apiKeyId));

        if (!apiKey.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此 API Key");
        }

        apiKey.setStatus(ApiKey.KeyStatus.REVOKED);
        apiKey.setEnabled(false);
        apiKey.setRevokedAt(Instant.now());
        apiKey.setRevokeReason(reason);

        // 清理并发计数
        concurrentRequests.remove(apiKeyId);

        return apiKeyRepository.save(apiKey);
    }

    /**
     * 恢复 API Key
     */
    @Transactional
    public ApiKey restoreApiKey(String apiKeyId, String userId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new RuntimeException("API Key 不存在：" + apiKeyId));

        if (!apiKey.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此 API Key");
        }

        if (apiKey.getStatus() != ApiKey.KeyStatus.REVOKED) {
            throw new RuntimeException("API Key 状态不是已吊销");
        }

        apiKey.setStatus(ApiKey.KeyStatus.ACTIVE);
        apiKey.setEnabled(true);
        apiKey.setRevokedAt(null);
        apiKey.setRevokeReason(null);

        return apiKeyRepository.save(apiKey);
    }

    /**
     * 获取用户所有 API Key
     */
    @Transactional(readOnly = true)
    public List<ApiKey> getUserApiKeys(String userId) {
        return apiKeyRepository.findByUserId(userId);
    }

    /**
     * 获取 API Key 详情
     */
    @Transactional(readOnly = true)
    public ApiKey getApiKey(String apiKeyId, String userId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new RuntimeException("API Key 不存在：" + apiKeyId));

        if (!apiKey.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看此 API Key");
        }

        return apiKey;
    }

    // ==================== API Key 验证和配额检查 ====================

    /**
     * 验证 API Key 并返回验证结果
     */
    @Transactional
    public ApiKeyValidationResult validateApiKey(String rawKey) {
        // 1. Hash 原始 Key
        String keyHash = BCrypt.hashpw(rawKey, BCrypt.gensalt(12));

        // 2. 查找 Key (实际应该先按前缀查询再比对)
        // 这里简化处理，实际生产环境需要优化
        List<ApiKey> allKeys = apiKeyRepository.findActiveKeys();
        ApiKey apiKey = null;

        for (ApiKey key : allKeys) {
            if (BCrypt.checkpw(rawKey, key.getKeyHash())) {
                apiKey = key;
                break;
            }
        }

        if (apiKey == null) {
            return ApiKeyValidationResult.invalid("无效的 API Key");
        }

        // 3. 检查状态
        if (!apiKey.getEnabled()) {
            return ApiKeyValidationResult.invalid("API Key 已禁用");
        }

        if (apiKey.getStatus() != ApiKey.KeyStatus.ACTIVE) {
            return ApiKeyValidationResult.invalid("API Key 状态异常：" + apiKey.getStatus());
        }

        // 4. 检查过期时间
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            apiKey.setStatus(ApiKey.KeyStatus.EXPIRED);
            apiKey.setEnabled(false);
            apiKeyRepository.save(apiKey);
            return ApiKeyValidationResult.invalid("API Key 已过期");
        }

        // 5. 重置每日统计 (如果需要)
        resetDailyStatsIfNeeded(apiKey);

        // 6. 更新最后使用时间
        apiKey.setLastUsedAt(Instant.now());
        apiKey.setTotalRequests(apiKey.getTotalRequests() + 1);
        apiKey.setRequestsToday(apiKey.getRequestsToday() + 1);
        apiKeyRepository.save(apiKey);

        // 7. 获取配额计划
        ApiKeyQuotaPlan plan = quotaPlanRepository.findById(apiKey.getQuotaPlanId())
                .orElse(null);

        return ApiKeyValidationResult.valid(apiKey, plan);
    }

    /**
     * 检查速率限制
     */
    public RateLimitCheckResult checkRateLimit(String apiKeyId, ApiKeyQuotaPlan plan) {
        Instant now = Instant.now();

        // 检查每秒限制
        if (plan != null && plan.getRateLimitPerSecond() != null) {
            RateLimitBucket secondBucket = getOrCreateBucket(
                    apiKeyId, "SECOND", now.getEpochSecond(), plan.getRateLimitPerSecond());
            if (secondBucket.getCount() >= plan.getRateLimitPerSecond()) {
                return RateLimitExceeded("每秒", plan.getRateLimitPerSecond());
            }
            secondBucket.increment();
        }

        // 检查每分钟限制
        if (plan != null && plan.getRateLimitPerMinute() != null) {
            RateLimitBucket minuteBucket = getOrCreateBucket(
                    apiKeyId, "MINUTE", now.toEpochMilli() / 60000, plan.getRateLimitPerMinute());
            if (minuteBucket.getCount() >= plan.getRateLimitPerMinute()) {
                return RateLimitExceeded("每分钟", plan.getRateLimitPerMinute());
            }
            minuteBucket.increment();
        }

        // 检查每小时限制
        if (plan != null && plan.getRateLimitPerHour() != null) {
            RateLimitBucket hourBucket = getOrCreateBucket(
                    apiKeyId, "HOUR", now.toEpochMilli() / 3600000, plan.getRateLimitPerHour());
            if (hourBucket.getCount() >= plan.getRateLimitPerHour()) {
                return RateLimitExceeded("每小时", plan.getRateLimitPerHour());
            }
            hourBucket.increment();
        }

        return RateLimitCheckResult.allowed();
    }

    /**
     * 检查并发请求数
     */
    public ConcurrentCheckResult checkConcurrency(String apiKeyId, int maxConcurrent) {
        AtomicInteger count = concurrentRequests.computeIfAbsent(apiKeyId, k -> new AtomicInteger(0));
        int currentCount = count.get();

        if (currentCount >= maxConcurrent) {
            return ConcurrentCheckResult.exceeded(currentCount, maxConcurrent);
        }

        count.incrementAndGet();
        return ConcurrentCheckResult.allowed();
    }

    /**
     * 释放并发计数
     */
    public void releaseConcurrency(String apiKeyId) {
        AtomicInteger count = concurrentRequests.get(apiKeyId);
        if (count != null) {
            count.decrementAndGet();
        }
    }

    /**
     * 检查端点权限
     */
    public EndpointCheckResult checkEndpointPermission(ApiKey apiKey, String endpoint, String method) {
        // 检查阻止列表
        Map<String, Object> blockedEndpoints = apiKey.getBlockedEndpoints();
        if (blockedEndpoints != null && blockedEndpoints.containsKey(endpoint)) {
            return EndpointCheckResult.blocked("此端点已被阻止访问");
        }

        // 检查允许列表 (如果有设置)
        Map<String, Object> allowedEndpoints = apiKey.getAllowedEndpoints();
        if (allowedEndpoints != null && !allowedEndpoints.isEmpty()) {
            if (!allowedEndpoints.containsKey(endpoint)) {
                return EndpointCheckResult.blocked("此端点不在允许列表中");
            }
        }

        return EndpointCheckResult.allowed();
    }

    // ==================== 配额管理 ====================

    /**
     * 获取可用配额计划列表
     */
    @Transactional(readOnly = true)
    public List<ApiKeyQuotaPlan> getAvailablePlans() {
        return quotaPlanRepository.findByIsActiveTrueOrderByPriceCentsAsc();
    }

    /**
     * 升级 API Key 配额计划
     */
    @Transactional
    public ApiKey upgradeQuotaPlan(String apiKeyId, String userId, String newPlanId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new RuntimeException("API Key 不存在：" + apiKeyId));

        if (!apiKey.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此 API Key");
        }

        ApiKeyQuotaPlan newPlan = quotaPlanRepository.findById(newPlanId)
                .orElseThrow(() -> new RuntimeException("配额计划不存在：" + newPlanId));

        apiKey.setQuotaPlanId(newPlanId);
        applyQuotaPlan(apiKey, newPlan);

        return apiKeyRepository.save(apiKey);
    }

    // ==================== 私有方法 ====================

    private void applyQuotaPlan(ApiKey apiKey, ApiKeyQuotaPlan plan) {
        if (plan == null) {
            // 使用默认配额
            apiKey.setRateLimitPerSecond(10);
            apiKey.setRateLimitPerMinute(100);
            apiKey.setRateLimitPerHour(1000);
            apiKey.setMaxConcurrentRequests(5);
            return;
        }
        apiKey.setRateLimitPerSecond(plan.getRateLimitPerSecond());
        apiKey.setRateLimitPerMinute(plan.getRateLimitPerMinute());
        apiKey.setRateLimitPerHour(plan.getRateLimitPerHour());
        apiKey.setMaxConcurrentRequests(plan.getMaxConcurrentRequests());
        apiKey.setPermissions(plan.getAllowedFeatures());
    }

    private void resetDailyStatsIfNeeded(ApiKey apiKey) {
        LocalDate today = LocalDate.now();

        if (apiKey.getLastResetDate() == null ||
            apiKey.getLastResetDate().isBefore(today)) {
            apiKey.setRequestsToday(0);
            apiKey.setTokensUsedToday(0L);
            apiKey.setLastResetDate(today);
            apiKeyRepository.save(apiKey);

            log.debug("Reset daily stats for API Key: {}", apiKey.getKeyPrefix());
        }
    }

    private RateLimitBucket getOrCreateBucket(String apiKeyId, String bucketType,
                                               long bucketWindow, int limit) {
        String key = apiKeyId + ":" + bucketType + ":" + bucketWindow;
        return rateLimitBuckets.computeIfAbsent(key, k ->
                new RateLimitBucket(limit));
    }

    private RateLimitCheckResult RateLimitExceeded(String period, int limit) {
        return new RateLimitCheckResult(false,
                String.format("超过速率限制：%d 请求/%s", limit, period));
    }

    // ==================== 内部类 ====================

    private static class RateLimitBucket {
        private final int limit;
        private int count;
        private final Instant createdAt;

        RateLimitBucket(int limit) {
            this.limit = limit;
            this.count = 0;
            this.createdAt = Instant.now();
        }

        int getCount() { return count; }
        void increment() { count++; }
    }

    public static class ApiKeyCreateRequest {
        private String keyName;
        private ApiKey.KeyType keyType;
        private ApiKey.KeyScope keyScope;
        private String quotaPlanId;
        private Instant expiresAt;
        private Map<String, Object> permissions;
        private Map<String, Object> allowedEndpoints;
        private Map<String, Object> blockedEndpoints;
        private Map<String, Object> allowedIpRanges;
        private Map<String, Object> blockedIpRanges;
        private Map<String, Object> metadata;

        // 速率限制（用于匿名 Key）
        private Integer rateLimitPerSecond;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerHour;

        // Getters and Setters
        public String getKeyName() { return keyName; }
        public void setKeyName(String keyName) { this.keyName = keyName; }

        public ApiKey.KeyType getKeyType() { return keyType; }
        public void setKeyType(ApiKey.KeyType keyType) { this.keyType = keyType; }

        public ApiKey.KeyScope getKeyScope() { return keyScope; }
        public void setKeyScope(ApiKey.KeyScope keyScope) { this.keyScope = keyScope; }

        public String getQuotaPlanId() { return quotaPlanId; }
        public void setQuotaPlanId(String quotaPlanId) { this.quotaPlanId = quotaPlanId; }

        public Instant getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

        public Map<String, Object> getPermissions() { return permissions; }
        public void setPermissions(Map<String, Object> permissions) { this.permissions = permissions; }

        public Map<String, Object> getAllowedEndpoints() { return allowedEndpoints; }
        public void setAllowedEndpoints(Map<String, Object> allowedEndpoints) { this.allowedEndpoints = allowedEndpoints; }

        public Map<String, Object> getBlockedEndpoints() { return blockedEndpoints; }
        public void setBlockedEndpoints(Map<String, Object> blockedEndpoints) { this.blockedEndpoints = blockedEndpoints; }

        public Map<String, Object> getAllowedIpRanges() { return allowedIpRanges; }
        public void setAllowedIpRanges(Map<String, Object> allowedIpRanges) { this.allowedIpRanges = allowedIpRanges; }

        public Map<String, Object> getBlockedIpRanges() { return blockedIpRanges; }
        public void setBlockedIpRanges(Map<String, Object> blockedIpRanges) { this.blockedIpRanges = blockedIpRanges; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        // 速率限制设置器（用于匿名 Key）
        public Integer getRateLimitPerSecond() { return rateLimitPerSecond; }
        public void setRateLimitPerSecond(Integer rateLimitPerSecond) {
            this.rateLimitPerSecond = rateLimitPerSecond;
        }

        public Integer getRateLimitPerMinute() { return rateLimitPerMinute; }
        public void setRateLimitPerMinute(Integer rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }

        public Integer getRateLimitPerHour() { return rateLimitPerHour; }
        public void setRateLimitPerHour(Integer rateLimitPerHour) {
            this.rateLimitPerHour = rateLimitPerHour;
        }
    }

    public static class ApiKeyCreateResult {
        private final ApiKey apiKey;
        private final String rawKey;  // 只返回一次

        public ApiKeyCreateResult(ApiKey apiKey, String rawKey) {
            this.apiKey = apiKey;
            this.rawKey = rawKey;
        }

        public ApiKey getApiKey() { return apiKey; }
        public String getRawKey() { return rawKey; }
    }

    public static class ApiKeyValidationResult {
        private final boolean valid;
        private final ApiKey apiKey;
        private final ApiKeyQuotaPlan quotaPlan;
        private final String errorMessage;

        private ApiKeyValidationResult(boolean valid, ApiKey apiKey,
                                        ApiKeyQuotaPlan quotaPlan, String errorMessage) {
            this.valid = valid;
            this.apiKey = apiKey;
            this.quotaPlan = quotaPlan;
            this.errorMessage = errorMessage;
        }

        public static ApiKeyValidationResult valid(ApiKey apiKey, ApiKeyQuotaPlan quotaPlan) {
            return new ApiKeyValidationResult(true, apiKey, quotaPlan, null);
        }

        public static ApiKeyValidationResult invalid(String errorMessage) {
            return new ApiKeyValidationResult(false, null, null, errorMessage);
        }

        public boolean isValid() { return valid; }
        public ApiKey getApiKey() { return apiKey; }
        public ApiKeyQuotaPlan getQuotaPlan() { return quotaPlan; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class RateLimitCheckResult {
        private final boolean allowed;
        private final String errorMessage;

        private RateLimitCheckResult(boolean allowed, String errorMessage) {
            this.allowed = allowed;
            this.errorMessage = errorMessage;
        }

        public static RateLimitCheckResult allowed() {
            return new RateLimitCheckResult(true, null);
        }

        public static RateLimitCheckResult exceeded(String errorMessage) {
            return new RateLimitCheckResult(false, errorMessage);
        }

        public boolean isAllowed() { return allowed; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class ConcurrentCheckResult {
        private final boolean allowed;
        private final int currentCount;
        private final int maxCount;

        private ConcurrentCheckResult(boolean allowed, int currentCount, int maxCount) {
            this.allowed = allowed;
            this.currentCount = currentCount;
            this.maxCount = maxCount;
        }

        public static ConcurrentCheckResult allowed() {
            return new ConcurrentCheckResult(true, 0, 0);
        }

        public static ConcurrentCheckResult exceeded(int currentCount, int maxCount) {
            return new ConcurrentCheckResult(false, currentCount, maxCount);
        }

        public boolean isAllowed() { return allowed; }
        public int getCurrentCount() { return currentCount; }
        public int getMaxCount() { return maxCount; }
    }

    public static class EndpointCheckResult {
        private final boolean allowed;
        private final String errorMessage;

        private EndpointCheckResult(boolean allowed, String errorMessage) {
            this.allowed = allowed;
            this.errorMessage = errorMessage;
        }

        public static EndpointCheckResult allowed() {
            return new EndpointCheckResult(true, null);
        }

        public static EndpointCheckResult blocked(String errorMessage) {
            return new EndpointCheckResult(false, errorMessage);
        }

        public boolean isAllowed() { return allowed; }
        public String getErrorMessage() { return errorMessage; }
    }
}
