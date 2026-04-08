package demo.k8s.agent.observability.events;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 事件基类
 */
public abstract class Event {

    /**
     * 事件 ID
     */
    private final String id;

    /**
     * 事件时间戳
     */
    private final Instant timestamp;

    /**
     * 会话 ID
     */
    private final String sessionId;

    /**
     * 用户 ID
     */
    private final String userId;

    /**
     * 事件元数据
     */
    private final Map<String, Object> metadata;

    protected Event(String sessionId, String userId, Map<String, Object> metadata) {
        this.id = generateId();
        this.timestamp = Instant.now();
        this.sessionId = sessionId;
        this.userId = userId;
        this.metadata = metadata;
    }

    public String id() {
        return id;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String sessionId() {
        return sessionId;
    }

    public String userId() {
        return userId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * 获取事件类型名称
     */
    public abstract String getEventType();

    private static String generateId() {
        return "evt_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 用户登录事件
     */
    public static class UserLoginEvent extends Event {
        public UserLoginEvent(String userId, String username, String sessionId) {
            super(sessionId, userId, Map.of(
                    "username", username,
                    "loginTime", Instant.now().toString()
            ));
        }

        @Override
        public String getEventType() {
            return "USER_LOGIN";
        }
    }

    /**
     * 用户登出事件
     */
    public static class UserLogoutEvent extends Event {
        public UserLogoutEvent(String userId, String sessionId) {
            super(sessionId, userId, Map.of(
                    "logoutTime", Instant.now().toString()
            ));
        }

        @Override
        public String getEventType() {
            return "USER_LOGOUT";
        }
    }

    /**
     * Session 创建事件
     */
    public static class SessionCreatedEvent extends Event {
        public SessionCreatedEvent(String sessionId, String userId) {
            super(sessionId, userId, Map.of(
                    "createTime", Instant.now().toString()
            ));
        }

        @Override
        public String getEventType() {
            return "SESSION_CREATED";
        }
    }

    /**
     * Session 结束事件
     */
    public static class SessionEndedEvent extends Event {
        public SessionEndedEvent(String sessionId, String userId, String reason) {
            super(sessionId, userId, Map.of(
                    "endTime", Instant.now().toString(),
                    "reason", reason
            ));
        }

        @Override
        public String getEventType() {
            return "SESSION_ENDED";
        }
    }

    /**
     * 工具调用事件
     */
    public static class ToolCalledEvent extends Event {
        public ToolCalledEvent(String sessionId, String userId, String toolName,
                                String input, String output, long durationMs, boolean success) {
            super(sessionId, userId, buildPayload(toolName, input, output, durationMs, success));
        }

        @Override
        public String getEventType() {
            return "TOOL_CALLED";
        }

        private static String truncate(String s, int maxLen) {
            if (s == null) return "";
            return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
        }

        private static Map<String, Object> buildPayload(
                String toolName, String input, String output, long durationMs, boolean success) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("toolName", toolName == null ? "" : toolName);
            payload.put("input", truncate(input, 500));
            payload.put("output", truncate(output, 1000));
            payload.put("durationMs", durationMs);
            payload.put("success", success);
            return payload;
        }
    }

    /**
     * 权限请求事件
     */
    public static class PermissionRequestedEvent extends Event {
        public PermissionRequestedEvent(String sessionId, String userId, String toolName,
                                         String choice, long responseTimeMs) {
            super(sessionId, userId, Map.of(
                    "toolName", toolName,
                    "permissionChoice", choice,
                    "responseTimeMs", responseTimeMs
            ));
        }

        @Override
        public String getEventType() {
            return "PERMISSION_REQUESTED";
        }
    }

    /**
     * 模型调用事件
     */
    public static class ModelCalledEvent extends Event {
        public ModelCalledEvent(String sessionId, String userId, String model,
                                 int inputTokens, int outputTokens, long latencyMs) {
            super(sessionId, userId, Map.of(
                    "model", model,
                    "inputTokens", inputTokens,
                    "outputTokens", outputTokens,
                    "latencyMs", latencyMs
            ));
        }

        @Override
        public String getEventType() {
            return "MODEL_CALLED";
        }
    }

    /**
     * 错误事件
     */
    public static class ErrorEvent extends Event {
        public ErrorEvent(String sessionId, String userId, String errorType,
                           String errorMessage, String stackTrace) {
            super(sessionId, userId, Map.of(
                    "errorType", errorType,
                    "errorMessage", truncate(errorMessage, 500),
                    "stackTrace", truncate(stackTrace, 2000)
            ));
        }

        @Override
        public String getEventType() {
            return "ERROR";
        }

        private static String truncate(String s, int maxLen) {
            if (s == null) return null;
            return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
        }
    }

    /**
     * 配额超出事件
     */
    public static class QuotaExceededEvent extends Event {
        public QuotaExceededEvent(String userId, String quotaType, int limit, int current) {
            super(null, userId, Map.of(
                    "quotaType", quotaType,
                    "limit", limit,
                    "current", current
            ));
        }

        @Override
        public String getEventType() {
            return "QUOTA_EXCEEDED";
        }
    }
}
