package demo.k8s.agent.apikey;

import demo.k8s.agent.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Key 管理 API 控制器
 */
@RestController
@RequestMapping("/api/apikeys")
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final ApiKeyService apiKeyService;
    private final PremiumFeatureService premiumFeatureService;

    public ApiKeyController(ApiKeyService apiKeyService, PremiumFeatureService premiumFeatureService) {
        this.apiKeyService = apiKeyService;
        this.premiumFeatureService = premiumFeatureService;
    }

    /**
     * 创建新的 API Key
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createApiKey(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        log.info("User {} creating new API Key", userId);

        ApiKeyService.ApiKeyCreateRequest createRequest = new ApiKeyService.ApiKeyCreateRequest();
        createRequest.setKeyName((String) request.get("keyName"));

        if (request.get("keyType") != null) {
            createRequest.setKeyType(ApiKey.KeyType.valueOf((String) request.get("keyType")));
        }
        if (request.get("quotaPlanId") != null) {
            createRequest.setQuotaPlanId((String) request.get("quotaPlanId"));
        }
        if (request.get("expiresAt") != null) {
            createRequest.setExpiresAt(Instant.parse((String) request.get("expiresAt")));
        }

        ApiKeyService.ApiKeyCreateResult result = apiKeyService.createApiKey(userId, createRequest);

        // 返回 API Key 信息 (原始 Key 只显示一次)
        Map<String, Object> response = new HashMap<>();
        response.put("id", result.getApiKey().getId());
        response.put("keyPrefix", result.getApiKey().getKeyPrefix());
        response.put("rawKey", result.getRawKey());  // ⚠️ 仅在此处返回，请用户妥善保存
        response.put("keyName", result.getApiKey().getKeyName());
        response.put("keyType", result.getApiKey().getKeyType().name());
        response.put("quotaPlanId", result.getApiKey().getQuotaPlanId());
        response.put("createdAt", result.getApiKey().getCreatedAt().toString());

        // 警告用户只看到一次
        response.put("warning", "请安全保存此 API Key，它将不会再显示！");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户所有 API Key
     */
    @GetMapping
    public ResponseEntity<List<ApiKey>> getUserApiKeys(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        List<ApiKey> keys = apiKeyService.getUserApiKeys(userId);
        return ResponseEntity.ok(keys);
    }

    /**
     * 获取 API Key 详情
     */
    @GetMapping("/{keyId}")
    public ResponseEntity<ApiKey> getApiKey(
            @PathVariable String keyId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ApiKey key = apiKeyService.getApiKey(keyId, userId);
        return ResponseEntity.ok(key);
    }

    /**
     * 吊销 API Key
     */
    @PostMapping("/{keyId}/revoke")
    public ResponseEntity<ApiKey> revokeApiKey(
            @PathVariable String keyId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        String reason = request.getOrDefault("reason", "用户主动吊销");

        log.info("User {} revoking API Key {}: {}", userId, keyId, reason);
        ApiKey key = apiKeyService.revokeApiKey(keyId, userId, reason);

        return ResponseEntity.ok(key);
    }

    /**
     * 恢复已吊销的 API Key
     */
    @PostMapping("/{keyId}/restore")
    public ResponseEntity<ApiKey> restoreApiKey(
            @PathVariable String keyId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        log.info("User {} restoring API Key {}", userId, keyId);
        ApiKey key = apiKeyService.restoreApiKey(keyId, userId);

        return ResponseEntity.ok(key);
    }

    /**
     * 升级 API Key 配额计划
     */
    @PostMapping("/{keyId}/upgrade")
    public ResponseEntity<ApiKey> upgradeQuotaPlan(
            @PathVariable String keyId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        String newPlanId = request.get("planId");

        log.info("User {} upgrading API Key {} to plan {}", userId, keyId, newPlanId);
        ApiKey key = apiKeyService.upgradeQuotaPlan(keyId, userId, newPlanId);

        return ResponseEntity.ok(key);
    }

    /**
     * 获取可用配额计划列表
     */
    @GetMapping("/plans")
    public ResponseEntity<List<ApiKeyQuotaPlan>> getAvailablePlans() {
        List<ApiKeyQuotaPlan> plans = apiKeyService.getAvailablePlans();
        return ResponseEntity.ok(plans);
    }

    /**
     * 获取核心收益点功能列表
     */
    @GetMapping("/premium-features")
    public ResponseEntity<List<PremiumFeatureService.PremiumFeatureConfig>> getPremiumFeatures() {
        List<PremiumFeatureService.PremiumFeatureConfig> features = premiumFeatureService.getAllFeatures();
        return ResponseEntity.ok(features);
    }

    /**
     * 检查核心功能使用权限
     */
    @GetMapping("/premium-features/check")
    public ResponseEntity<PremiumFeatureService.PremiumFeatureCheckResult> checkPremiumFeature(
            @RequestParam String featureId,
            @RequestParam(defaultValue = "1") long amount,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        // 简化处理，实际需要从请求中获取 API Key ID
        PremiumFeatureService.PremiumFeatureCheckResult result =
                premiumFeatureService.checkFeaturePermission(userId, null, featureId, amount);

        if (!result.isAllowed()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
