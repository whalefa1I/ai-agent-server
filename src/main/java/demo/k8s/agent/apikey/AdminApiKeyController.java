package demo.k8s.agent.apikey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员 API - 成本控制和并发管理
 */
@RestController
@RequestMapping("/api/admin/apikey")
public class AdminApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyController.class);

    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository;
    private final PremiumFeatureService premiumFeatureService;

    public AdminApiKeyController(
            ApiKeyService apiKeyService,
            ApiKeyRepository apiKeyRepository,
            PremiumFeatureService premiumFeatureService) {
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository;
        this.premiumFeatureService = premiumFeatureService;
    }

    /**
     * 获取系统整体 API Key 统计
     */
    @GetMapping("/stats/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSummaryStats() {
        List<ApiKey> allKeys = apiKeyRepository.findAll();

        long totalKeys = allKeys.size();
        long activeKeys = allKeys.stream().filter(ApiKey::getEnabled).count();
        long revokedKeys = allKeys.stream()
                .filter(k -> k.getStatus() == ApiKey.KeyStatus.REVOKED)
                .count();
        long expiredKeys = allKeys.stream()
                .filter(k -> k.getStatus() == ApiKey.KeyStatus.EXPIRED)
                .count();

        // 按类型统计
        Map<ApiKey.KeyType, Long> byType = new HashMap<>();
        for (ApiKey key : allKeys) {
            byType.merge(key.getKeyType(), 1L, Long::sum);
        }

        // 按套餐统计
        Map<String, Long> byPlan = new HashMap<>();
        for (ApiKey key : allKeys) {
            byPlan.merge(key.getQuotaPlanId(), 1L, Long::sum);
        }

        // 今日总请求数
        long totalRequestsToday = allKeys.stream()
                .mapToLong(ApiKey::getRequestsToday)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKeys", totalKeys);
        stats.put("activeKeys", activeKeys);
        stats.put("revokedKeys", revokedKeys);
        stats.put("expiredKeys", expiredKeys);
        stats.put("byType", byType);
        stats.put("byPlan", byPlan);
        stats.put("totalRequestsToday", totalRequestsToday);
        stats.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(stats);
    }

    /**
     * 查询高使用量 API Key (防滥用)
     */
    @GetMapping("/high-usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ApiKey>> getHighUsageKeys(
            @RequestParam(defaultValue = "1000") int threshold) {

        List<ApiKey> highUsageKeys = apiKeyRepository.findHighUsageKeys(threshold);
        return ResponseEntity.ok(highUsageKeys);
    }

    /**
     * 查询即将过期的 API Key
     */
    @GetMapping("/expiring")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ApiKey>> getExpiringKeys(
            @RequestParam(defaultValue = "7") int days) {

        Instant futureDate = Instant.now().plusSeconds(days * 86400L);
        List<ApiKey> expiringKeys = apiKeyRepository.findExpiringKeys(futureDate);
        return ResponseEntity.ok(expiringKeys);
    }

    /**
     * 强制禁用 API Key (管理员特权)
     */
    @PostMapping("/{keyId}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiKey> disableApiKey(
            @PathVariable String keyId,
            @RequestBody Map<String, String> request) {

        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("API Key 不存在：" + keyId));

        apiKey.setEnabled(false);
        apiKey.setStatus(ApiKey.KeyStatus.SUSPENDED);
        apiKey.setRevokeReason(request.getOrDefault("reason", "管理员强制禁用"));

        ApiKey updated = apiKeyRepository.save(apiKey);
        log.warn("Admin disabled API Key: {} (user={})", keyId, apiKey.getUserId());

        return ResponseEntity.ok(updated);
    }

    /**
     * 强制启用 API Key (管理员特权)
     */
    @PostMapping("/{keyId}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiKey> enableApiKey(
            @PathVariable String keyId) {

        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("API Key 不存在：" + keyId));

        apiKey.setEnabled(true);
        apiKey.setStatus(ApiKey.KeyStatus.ACTIVE);
        apiKey.setRevokedAt(null);
        apiKey.setRevokeReason(null);

        ApiKey updated = apiKeyRepository.save(apiKey);
        log.info("Admin enabled API Key: {} (user={})", keyId, apiKey.getUserId());

        return ResponseEntity.ok(updated);
    }

    /**
     * 查看可疑活动列表
     */
    @GetMapping("/suspicious")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getSuspiciousActivities() {
        // 实际实现需要查询 suspicious_activity 表
        return ResponseEntity.ok(List.of());
    }

    /**
     * 标记可疑活动为已处理
     */
    @PostMapping("/suspicious/{activityId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> resolveSuspiciousActivity(
            @PathVariable String activityId,
            @RequestBody Map<String, String> request) {

        String status = request.getOrDefault("status", "RESOLVED");
        String action = request.get("action");

        log.info("Admin resolved suspicious activity: {} -> {}, action={}",
                activityId, status, action);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("activityId", activityId);
        result.put("newStatus", status);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取核心收益点使用统计
     */
    @GetMapping("/premium/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPremiumFeatureStats() {
        List<PremiumFeatureService.PremiumFeatureConfig> features = premiumFeatureService.getAllFeatures();

        Map<String, Object> stats = new HashMap<>();
        stats.put("features", features);
        stats.put("timestamp", Instant.now().toString());

        // 实际实现应该加入各功能的使用量统计

        return ResponseEntity.ok(stats);
    }

    /**
     * 管理员配额覆盖 (针对特定用户设置特殊配额)
     */
    @PostMapping("/quota-override")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> setQuotaOverride(
            @RequestBody Map<String, Object> request) {

        String userId = (String) request.get("userId");
        String apiKeyId = (String) request.get("apiKeyId");
        Integer requestsPerDay = (Integer) request.get("requestsPerDay");
        Integer rateLimit = (Integer) request.get("rateLimit");
        Boolean forceEnabled = (Boolean) request.get("forceEnabled");
        Boolean forceDisabled = (Boolean) request.get("forceDisabled");
        String reason = (String) request.get("reason");

        log.info("Admin setting quota override for user {}: requestsPerDay={}, rateLimit={}, reason={}",
                userId, requestsPerDay, rateLimit, reason);

        // 实际实现需要写入 admin_quota_override 表

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("userId", userId);
        result.put("message", "配额覆盖已设置");

        return ResponseEntity.ok(result);
    }

    /**
     * 获取服务熔断状态
     */
    @GetMapping("/circuit-breaker")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        // 实际实现需要查询 service_circuit_breaker 表
        Map<String, Object> status = new HashMap<>();
        status.put("status", "CLOSED");
        status.put("services", List.of("ai-model", "remote-tool", "scheduler"));
        return ResponseEntity.ok(status);
    }

    /**
     * 手动触发服务熔断 (紧急情况下保护系统)
     */
    @PostMapping("/circuit-breaker/trip")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> tripCircuitBreaker(
            @RequestBody Map<String, String> request) {

        String serviceName = request.get("serviceName");
        String reason = request.get("reason");

        log.warn("ADMIN TRIPPING circuit breaker for service: {} - Reason: {}",
                serviceName, reason);

        // 实际实现需要更新 service_circuit_breaker 表

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("serviceName", serviceName);
        result.put("newStatus", "OPEN");

        return ResponseEntity.ok(result);
    }
}
