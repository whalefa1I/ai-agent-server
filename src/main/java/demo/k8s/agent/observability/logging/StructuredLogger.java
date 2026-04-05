package demo.k8s.agent.observability.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 结构化日志记录器
 */
public class StructuredLogger {

    private static final Logger log = LoggerFactory.getLogger(StructuredLogger.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 记录通用事件
     */
    public static void logEvent(String eventType, String sessionId, String userId, Map<String, Object> metadata) {
        Map<String, Object> data = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        data.put("eventType", eventType);

        LogEntry entry = LogEntry.builder()
                .event("event")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.info(encode(entry));
    }

    /**
     * 记录用户输入事件（简化重载）
     */
    public static void logUserInput(String sessionId, String userId, String input) {
        logUserInput(sessionId, userId, input, 0);
    }

    /**
     * 记录模型响应事件（简化重载，带 model 参数）
     */
    public static void logModelResponse(String sessionId, String userId, String model,
                                         int inputTokens, int outputTokens, long latencyMs, String response) {
        logModelResponse(sessionId, userId, response, inputTokens, outputTokens, latencyMs);
    }

    /**
     * 记录工具调用事件（简化重载，input 为 String）
     */
    public static void logToolCall(String sessionId, String userId, String toolName,
                                    String input, String output, long durationMs, boolean success) {
        Map<String, Object> data = new HashMap<>();
        data.put("toolName", toolName);
        data.put("input", truncate(input, 500));
        data.put("output", truncate(output, 1000));
        data.put("durationMs", durationMs);
        data.put("success", success);

        LogEntry entry = LogEntry.builder()
                .event("tool_call")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.info(encode(entry));
    }

    /**
     * 记录用户输入事件
     */
    public static void logUserInput(String sessionId, String userId, String input, int tokenCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("input", truncate(input, 500));
        data.put("inputLength", input != null ? input.length() : 0);
        data.put("estimatedTokens", tokenCount);

        LogEntry entry = LogEntry.builder()
                .event("user_input")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.info(encode(entry));
    }

    /**
     * 记录模型响应事件
     */
    public static void logModelResponse(String sessionId, String userId, String response,
                                         int inputTokens, int outputTokens, long latencyMs) {
        Map<String, Object> data = new HashMap<>();
        data.put("response", truncate(response, 1000));
        data.put("responseLength", response != null ? response.length() : 0);
        data.put("inputTokens", inputTokens);
        data.put("outputTokens", outputTokens);
        data.put("latencyMs", latencyMs);

        LogEntry entry = LogEntry.builder()
                .event("model_response")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.info(encode(entry));
    }

    /**
     * 记录工具调用事件
     */
    public static void logToolCall(String sessionId, String userId, String toolName,
                                    Map<String, Object> input, String output,
                                    long durationMs, boolean success) {
        Map<String, Object> data = new HashMap<>();
        data.put("toolName", toolName);
        data.put("input", truncate(toString(input), 500));
        data.put("output", truncate(output, 1000));
        data.put("durationMs", durationMs);
        data.put("success", success);

        LogEntry entry = LogEntry.builder()
                .event("tool_call")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.info(encode(entry));
    }

    /**
     * 记录权限请求事件
     */
    public static void logPermissionRequest(String sessionId, String userId, String toolName,
                                             String permissionChoice, long durationMs) {
        Map<String, Object> data = new HashMap<>();
        data.put("toolName", toolName);
        data.put("permissionChoice", permissionChoice);
        data.put("durationMs", durationMs);

        LogEntry entry = LogEntry.builder()
                .event("permission_request")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.info(encode(entry));
    }

    /**
     * 记录错误事件
     */
    public static void logError(String sessionId, String userId, String eventType,
                                 String errorMessage, String stackTrace) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventType", eventType);
        data.put("errorMessage", errorMessage);
        data.put("stackTrace", truncate(stackTrace, 2000));

        LogEntry entry = LogEntry.builder()
                .event("error")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.error(encode(entry));
    }

    /**
     * 记录 Session 事件
     */
    public static void logSessionEvent(String sessionId, String userId, String eventType,
                                        Map<String, Object> metadata) {
        Map<String, Object> data = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        data.put("eventType", eventType);

        LogEntry entry = LogEntry.builder()
                .event("session_event")
                .sessionId(sessionId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.info(encode(entry));
    }

    /**
     * 记录配额事件
     */
    public static void logQuotaEvent(String userId, String eventType, Map<String, Object> quotaData) {
        Map<String, Object> data = new HashMap<>(quotaData);
        data.put("eventType", eventType);

        LogEntry entry = LogEntry.builder()
                .event("quota_event")
                .userId(userId)
                .timestamp(Instant.now())
                .data(data)
                .build();
        log.warn(encode(entry));
    }

    /**
     * 编码为 JSON
     */
    private static String encode(LogEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to encode log entry: " + e.getMessage() + "\"}";
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private static String toString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    /**
     * 日志条目
     */
    public static class LogEntry {
        public String timestamp;
        public String level = "INFO";
        public String traceId;
        public String spanId;
        public String event;
        public String sessionId;
        public String userId;
        public Map<String, Object> data = new HashMap<>();

        public static LogEntryBuilder builder() {
            return new LogEntryBuilder();
        }

        public static class LogEntryBuilder {
            private final LogEntry entry = new LogEntry();

            public LogEntryBuilder timestamp(Instant timestamp) {
                entry.timestamp = timestamp.toString();
                return this;
            }

            public LogEntryBuilder event(String event) {
                entry.event = event;
                return this;
            }

            public LogEntryBuilder sessionId(String sessionId) {
                entry.sessionId = sessionId;
                return this;
            }

            public LogEntryBuilder userId(String userId) {
                entry.userId = userId;
                return this;
            }

            public LogEntryBuilder data(Map<String, Object> data) {
                entry.data = data;
                return this;
            }

            public LogEntry build() {
                // 添加追踪上下文
                entry.traceId = TraceContext.getTraceId();
                entry.spanId = TraceContext.getSpanId();
                return entry;
            }
        }
    }
}
