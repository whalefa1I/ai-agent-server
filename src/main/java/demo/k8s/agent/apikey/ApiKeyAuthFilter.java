package demo.k8s.agent.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API Key 认证过滤器
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    // 需要 API Key 认证的端点前缀
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/scheduler/",
            "/api/agent/",
            "/api/tools/",
            "/api/models/",
            "/api/premium/"
    );

    // 不需要认证的端点
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api/auth/",
            "/api/health",
            "/api/public/",
            "/actuator/",
            "/error"
    );

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 检查是否需要 API Key 认证
        if (!requiresApiKeyAuth(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从 Header 获取 API Key
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader == null || apiKeyHeader.isEmpty()) {
            // 尝试从 Authorization Header 获取 (Bearer 格式)
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                apiKeyHeader = authHeader.substring(7);
            }
        }

        if (apiKeyHeader == null || apiKeyHeader.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "缺少 API Key，请在 X-API-Key Header 中提供有效的 API Key");
            return;
        }

        // 验证 API Key
        ApiKeyService.ApiKeyValidationResult result = apiKeyService.validateApiKey(apiKeyHeader);

        if (!result.isValid()) {
            log.warn("API Key 验证失败：{} (path={})", result.getErrorMessage(), path);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "API Key 验证失败：" + result.getErrorMessage());
            return;
        }

        ApiKey apiKey = result.getApiKey();
        ApiKeyQuotaPlan plan = result.getQuotaPlan();

        // 检查并发限制
        ApiKeyService.ConcurrentCheckResult concurrencyResult =
                apiKeyService.checkConcurrency(apiKey.getId(),
                        plan != null ? plan.getMaxConcurrentRequests() : 5);

        if (!concurrencyResult.isAllowed()) {
            log.warn("API Key 并发请求超限：{}/{} (key={})",
                    concurrencyResult.getCurrentCount(),
                    concurrencyResult.getMaxCount(),
                    apiKey.getKeyPrefix());
            sendErrorResponse(response, 429,
                    "并发请求数超限，请稍后重试");
            return;
        }

        // 检查速率限制
        ApiKeyService.RateLimitCheckResult rateLimitResult =
                apiKeyService.checkRateLimit(apiKey.getId(), plan);

        if (!rateLimitResult.isAllowed()) {
            log.warn("API Key 速率限制超限：{} (key={})",
                    rateLimitResult.getErrorMessage(),
                    apiKey.getKeyPrefix());
            // 释放并发计数
            apiKeyService.releaseConcurrency(apiKey.getId());
            sendErrorResponse(response, 429,
                    "请求频率过高：" + rateLimitResult.getErrorMessage());
            return;
        }

        // 检查端点权限
        ApiKeyService.EndpointCheckResult endpointResult =
                apiKeyService.checkEndpointPermission(apiKey, path, request.getMethod());

        if (!endpointResult.isAllowed()) {
            log.warn("API Key 无权访问此端点：{} (key={})",
                    endpointResult.getErrorMessage(),
                    apiKey.getKeyPrefix());
            apiKeyService.releaseConcurrency(apiKey.getId());
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "无权访问：" + endpointResult.getErrorMessage());
            return;
        }

        // 将 API Key 信息存入请求属性，供后续使用
        request.setAttribute("API_KEY", apiKey);
        request.setAttribute("API_KEY_PLAN", plan);

        // 高频路径（轮询等）：仅 TRACE，避免与 DEBUG 应用日志叠加刷屏
        log.trace("API Key 访问：{} (key={}, path={})",
                apiKey.getUserId(), apiKey.getKeyPrefix(), path);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 释放并发计数
            apiKeyService.releaseConcurrency(apiKey.getId());

            // 记录使用日志 (异步)
            logUsage(apiKey, request, response);
        }
    }

    /**
     * 检查是否需要 API Key 认证
     */
    private boolean requiresApiKeyAuth(String path) {
        // 检查排除列表
        for (String excluded : EXCLUDED_PREFIXES) {
            if (path.startsWith(excluded)) {
                return false;
            }
        }

        // 检查保护列表
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        // 默认不需要
        return false;
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> errorBody = Map.of(
                "error", true,
                "code", statusCode,
                "message", message,
                "timestamp", Instant.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), errorBody);
    }

    /**
     * 记录使用日志 (简化实现)
     */
    private void logUsage(ApiKey apiKey, HttpServletRequest request, HttpServletResponse response) {
        // 实际实现应该异步写入数据库
        // 这里仅做日志记录
        int status = response.getStatus();
        log.info("API 使用：key={}, path={}, status={}, method={}",
                apiKey.getKeyPrefix(),
                request.getRequestURI(),
                status,
                request.getMethod());
    }
}
