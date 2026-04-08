package demo.k8s.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.user.QuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * 配额检查拦截器
 *
 * 在请求处理前检查用户配额是否充足
 */
@Component
public class QuotaCheckInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QuotaCheckInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final QuotaService quotaService;

    @Value("${demo.dev.skip-quota-check:false}")
    private boolean skipQuotaCheck;

    public QuotaCheckInterceptor(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 开发环境跳过配额检查（勿打 debug：前端轮询会每秒刷满日志）
        if (skipQuotaCheck) {
            return true;
        }

        // 跳过健康检查和静态资源
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator/") || uri.startsWith("/api/logs/") || uri.equals("/health")) {
            return true;
        }

        // 获取用户 ID（从请求头或参数）
        String userId = extractUserId(request);
        if (userId == null || userId.isBlank()) {
            // 允许没有用户 ID 的请求通过（未认证用户）
            // 认证拦截器会在更早阶段处理
            return true;
        }

        // 检查请求配额
        boolean quotaOk = quotaService.tryIncrementRequest(userId);
        if (!quotaOk) {
            log.warn("用户 {} 请求配额已用完，拒绝请求", userId);
            sendQuotaExceededResponse(response, userId);
            return false;
        }

        // 将用户 ID 放入请求属性，供后续使用
        request.setAttribute("userId", userId);

        return true;
    }

    /**
     * 从请求中提取用户 ID
     */
    private String extractUserId(HttpServletRequest request) {
        // 1. 从请求头获取
        String userId = request.getHeader("X-User-ID");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }

        // 2. 从 API Key 获取（如果有的话）
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            // TODO: 从 API Key 解析用户 ID
            return apiKey; // 暂时直接用 API Key 作为用户 ID
        }

        // 3. 从请求参数获取 (userId 或 accountId)
        userId = request.getParameter("userId");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        // 兼容 accountId 参数（前端 Happy 协议使用）
        userId = request.getParameter("accountId");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }

        // 4. 从 Session 获取（如果有）
        Object sessionUserId = request.getAttribute("userId");
        if (sessionUserId instanceof String str) {
            return str;
        }

        return null;
    }

    /**
     * 发送配额超限响应
     */
    private void sendQuotaExceededResponse(HttpServletResponse response, String userId) throws Exception {
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "QUOTA_EXCEEDED");
        error.put("message", "您的请求配额已用完，请等待明日重置或升级套餐");
        error.put("userId", userId);

        // 添加配额状态
        QuotaService.QuotaStatus status = quotaService.getQuotaStatus(userId);
        error.put("quota", Map.of(
                "requestsUsed", status.requestsUsed(),
                "maxRequests", status.maxRequests(),
                "usagePercent", status.requestUsagePercent()
        ));

        String json = objectMapper.writeValueAsString(error);
        response.getWriter().write(json);
    }
}
