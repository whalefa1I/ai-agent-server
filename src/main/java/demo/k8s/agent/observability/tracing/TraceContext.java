package demo.k8s.agent.observability.tracing;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪上下文 - 扩展支持多租户（对齐 v1 M2 要求）。
 * <p>
 * 三维身份模型：
 * - {@code tenantId}: 租户标识（企业/业务线隔离）
 * - {@code appId}: 应用标识（同一租户下不同应用）
 * - {@code sessionId}: 会话标识（用户对话会话）
 * - {@code userId}: 用户标识（可选，单点登录场景）
 */
public class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> APP_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> RUN_ID = new ThreadLocal<>();

    /**
     * 生成新的 TraceID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成新的 SpanID
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 生成请求级 ID（用于全链路检索）
     */
    public static String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 初始化追踪上下文
     */
    public static void init(String traceId, String spanId) {
        TRACE_ID.set(traceId);
        MDC.put("traceId", traceId != null ? traceId : "-");
        SPAN_ID.set(spanId);
        MDC.put("spanId", spanId != null ? spanId : "-");
        if (REQUEST_ID.get() == null) {
            REQUEST_ID.set(generateRequestId());
        }
        MDC.put("requestId", getRequestId());
    }

    /**
     * 初始化完整的三维身份上下文（v1 M2 新增）
     */
    public static void init(String traceId, String spanId, String tenantId, String appId, String sessionId, String userId) {
        init(traceId, spanId);
        setTenantId(tenantId);
        setAppId(appId);
        setSessionId(sessionId);
        setUserId(userId);
    }

    /**
     * 设置租户 ID（v1 M2 新增）
     */
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
        MDC.put("tenantId", tenantId != null ? tenantId : "-");
    }

    /**
     * 设置应用 ID（v1 M2 新增）
     */
    public static void setAppId(String appId) {
        APP_ID.set(appId);
        MDC.put("appId", appId != null ? appId : "-");
    }

    /**
     * 设置运行 ID（用于子 agent 派生时追溯父子关系）
     */
    public static void setRunId(String runId) {
        RUN_ID.set(runId);
        MDC.put("runId", runId != null ? runId : "-");
    }

    /**
     * 获取当前运行 ID
     */
    public static String getRunId() {
        return RUN_ID.get();
    }

    /**
     * 设置用户 ID
     */
    public static void setUserId(String userId) {
        USER_ID.set(userId);
        MDC.put("userId", userId != null ? userId : "-");
    }

    /**
     * 设置会话 ID
     */
    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
        MDC.put("sessionId", sessionId != null ? sessionId : "-");
    }

    /**
     * 设置请求 ID（全链路关联键）
     */
    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    /**
     * 获取 TraceID
     */
    public static String getTraceId() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = generateTraceId();
            TRACE_ID.set(traceId);
        }
        return traceId;
    }

    /**
     * 获取 SpanID
     */
    public static String getSpanId() {
        return SPAN_ID.get();
    }

    /**
     * 获取请求 ID
     */
    public static String getRequestId() {
        String requestId = REQUEST_ID.get();
        if (requestId == null || requestId.isBlank()) {
            requestId = generateRequestId();
            REQUEST_ID.set(requestId);
        }
        return requestId;
    }

    /**
     * 创建新的 Span
     */
    public static String newSpan() {
        String newSpanId = generateSpanId();
        SPAN_ID.set(newSpanId);
        return newSpanId;
    }

    /**
     * 获取用户 ID
     */
    public static String getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取会话 ID
     */
    public static String getSessionId() {
        return SESSION_ID.get();
    }

    /**
     * 获取租户 ID（v1 M2 新增）
     */
    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 获取应用 ID（v1 M2 新增）
     */
    public static String getAppId() {
        return APP_ID.get();
    }

    /**
     * 获取完整的三维身份上下文（v1 M2 新增）
     */
    public static IdentityContext getIdentityContext() {
        return new IdentityContext(
                getTenantId(),
                getAppId(),
                getSessionId(),
                getUserId()
        );
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        TRACE_ID.remove();
        SPAN_ID.remove();
        REQUEST_ID.remove();
        USER_ID.remove();
        SESSION_ID.remove();
        TENANT_ID.remove();
        APP_ID.remove();
        RUN_ID.remove();
        MDC.clear();
    }

    /**
     * 获取追踪上下文的 Map 表示
     */
    public static TraceInfo getTraceInfo() {
        return new TraceInfo(
                getTraceId(),
                getSpanId(),
                getRequestId(),
                getUserId(),
                getSessionId(),
                getTenantId(),
                getAppId()
        );
    }

    /**
     * 三维身份上下文（v1 M2 新增）
     */
    public record IdentityContext(
            String tenantId,
            String appId,
            String sessionId,
            String userId
    ) {}

    /**
     * 追踪信息
     */
    public record TraceInfo(
            String traceId,
            String spanId,
            String requestId,
            String userId,
            String sessionId,
            String tenantId,
            String appId
    ) {}
}
