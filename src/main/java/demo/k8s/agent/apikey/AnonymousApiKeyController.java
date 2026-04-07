package demo.k8s.agent.apikey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 匿名 API Key 生成控制器
 *
 * 允许前端用户无需登录即可生成匿名 API Key
 * 生成的 Key 存储在 localStorage 中供后续使用
 */
@RestController
@RequestMapping("/api/auth")
public class AnonymousApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(AnonymousApiKeyController.class);

    private final ApiKeyService apiKeyService;

    public AnonymousApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * 生成匿名 API Key
     *
     * 前端首次访问时调用此接口，将返回的 API Key 存储到 localStorage
     * 后续请求在 X-API-Key Header 中携带此 Key 即可
     */
    @PostMapping("/apikey/generate")
    public ResponseEntity<Map<String, Object>> generateAnonymousApiKey() {
        // 生成匿名用户 ID
        String anonymousUserId = "anon-" + UUID.randomUUID();

        log.info("Generating anonymous API Key for user: {}", anonymousUserId);

        // 构建创建请求（使用默认配额计划）
        ApiKeyService.ApiKeyCreateRequest createRequest = new ApiKeyService.ApiKeyCreateRequest();
        createRequest.setKeyName("Anonymous Key");
        createRequest.setKeyType(ApiKey.KeyType.USER);
        createRequest.setKeyScope(ApiKey.KeyScope.PERSONAL);
        createRequest.setQuotaPlanId("default");  // 使用默认配额计划

        // 开发模式 - 无速率限制
        createRequest.setRateLimitPerSecond(1000);  // 每秒 1000 次（实际无限制）
        createRequest.setRateLimitPerMinute(60000); // 每分钟 60000 次
        createRequest.setRateLimitPerHour(1000000); // 每小时 100 万次

        // 设置过期时间（7 天后过期）
        createRequest.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));

        // 设置权限（开发模式开放所有权限）
        Map<String, Object> permissions = new HashMap<>();
        permissions.put("happy_protocol", true);
        permissions.put("logs_read", true);
        permissions.put("scheduler", true);
        permissions.put("admin", true);
        permissions.put("tools", true);
        permissions.put("models", true);
        createRequest.setPermissions(permissions);

        // 设置允许的端点前缀
        Map<String, Object> allowedEndpoints = new HashMap<>();
        allowedEndpoints.put("/api/v1/**", true);
        allowedEndpoints.put("/api/health", true);
        allowedEndpoints.put("/actuator/**", true);
        createRequest.setAllowedEndpoints(allowedEndpoints);

        // 设置元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "anonymous");
        metadata.put("created_via", "auto-generation");
        createRequest.setMetadata(metadata);

        // 创建 API Key
        ApiKeyService.ApiKeyCreateResult result = apiKeyService.createApiKey(anonymousUserId, createRequest);

        // 返回 API Key 信息
        Map<String, Object> response = new HashMap<>();
        response.put("apiKey", result.getRawKey());
        response.put("keyPrefix", result.getApiKey().getKeyPrefix());
        response.put("userId", anonymousUserId);
        response.put("expiresAt", result.getApiKey().getExpiresAt().toString());
        response.put("warning", "请妥善保存此 API Key，建议存储到 localStorage。刷新页面后将无法再次获取。");

        log.info("Generated anonymous API Key: prefix={}, userId={}",
                result.getApiKey().getKeyPrefix(), anonymousUserId);

        return ResponseEntity.ok(response);
    }
}
