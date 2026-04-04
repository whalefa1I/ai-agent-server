package demo.k8s.agent.observability.tracing;

import java.util.UUID;

/**
 * 链路追踪上下文
 */
public class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();

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
     * 初始化追踪上下文
     */
    public static void init(String traceId, String spanId) {
        TRACE_ID.set(traceId);
        SPAN_ID.set(spanId);
    }

    /**
     * 设置用户 ID
     */
    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    /**
     * 设置会话 ID
     */
    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
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
     * 清除上下文
     */
    public static void clear() {
        TRACE_ID.remove();
        SPAN_ID.remove();
        USER_ID.remove();
        SESSION_ID.remove();
    }

    /**
     * 获取追踪上下文的 Map 表示
     */
    public static TraceInfo getTraceInfo() {
        return new TraceInfo(
                getTraceId(),
                getSpanId(),
                getUserId(),
                getSessionId()
        );
    }

    /**
     * 追踪信息
     */
    public record TraceInfo(
            String traceId,
            String spanId,
            String userId,
            String sessionId
    ) {}
}
