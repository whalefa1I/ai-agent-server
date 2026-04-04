package demo.k8s.agent.ws.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 消息协议 v2 - 支持多前端兼容。
 * <p>
 * 所有消息使用 JSON 格式，通过 WebSocket 双向通信。
 * 协议版本：2.0.0
 *
 * <h3>消息类型总览</h3>
 * <ul>
 *   <li>{@link ClientMessage} - 客户端 → 服务端</li>
 *   <li>{@link ServerMessage} - 服务端 → 客户端</li>
 * </ul>
 *
 * <h3>前端集成示例 (TypeScript)</h3>
 * <pre>{@code
 * const ws = new WebSocket('ws://localhost:8080/ws/agent');
 * ws.onopen = () => {
 *   ws.send(JSON.stringify({ type: 'USER_MESSAGE', content: 'hello' }));
 * };
 * ws.onmessage = (event) => {
 *   const msg = JSON.parse(event.data);
 *   if (msg.type === 'PERMISSION_REQUEST') {
 *     // 显示权限确认对话框
 *     showPermissionDialog(msg);
 *   }
 * };
 * }</pre>
 */
public class WsProtocol {

    /** 协议版本 */
    public static final String PROTOCOL_VERSION = "2.0.0";

    /**
     * 工具风险等级（与 PermissionLevel 对应）
     */
    public enum RiskLevel {
        READ_ONLY("只读", "📖", "#22c55e"),
        MODIFY_STATE("修改状态", "✏️", "#f59e0b"),
        NETWORK("网络访问", "🌐", "#3b82f6"),
        DESTRUCTIVE("破坏性", "⚠️", "#ef4444"),
        AGENT_SPAWN("子代理", "🤖", "#8b5cf6");

        public final String label;
        public final String icon;
        public final String color;  // 前端显示用的颜色

        RiskLevel(String label, String icon, String color) {
            this.label = label;
            this.icon = icon;
            this.color = color;
        }

        public static RiskLevel from(String name) {
            try {
                return valueOf(name);
            } catch (Exception e) {
                return MODIFY_STATE;
            }
        }

        /**
         * 转换为前端友好的 JSON 对象
         */
        public Map<String, String> toJSON() {
            return Map.of(
                "value", this.name(),
                "label", this.label,
                "icon", this.icon,
                "color", this.color
            );
        }
    }
    /**
     * 客户端发送的消息类型
     */
    public enum ClientMessageType {
        /** 用户输入消息 */
        USER_MESSAGE,
        /** 权限响应 */
        PERMISSION_RESPONSE,
        /** 获取历史 */
        GET_HISTORY,
        /** 获取统计 */
        GET_STATS,
        /** 心跳 */
        PING,
        /** 停止任务 */
        STOP_TASK
    }

    /**
     * 客户端消息基类
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    public static abstract class ClientMessage {
        public abstract ClientMessageType getType();
        public String requestId;  // 用于关联请求/响应
        public Instant timestamp;

        public ClientMessage() {
            this.requestId = generateRequestId();
            this.timestamp = Instant.now();
        }

        private static String generateRequestId() {
            return "req_" + System.currentTimeMillis() + "_" +
                   java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 用户输入消息
     */
    public static class UserMessage extends ClientMessage {
        public ClientMessageType getType() { return ClientMessageType.USER_MESSAGE; }

        @JsonProperty(required = true)
        public String content;  // 用户输入内容

        public UserMessage() {}

        public UserMessage(String content) {
            this.content = content;
        }
    }

    /**
     * 权限响应消息
     */
    public static class PermissionResponseMessage extends ClientMessage {
        public ClientMessageType getType() { return ClientMessageType.PERMISSION_RESPONSE; }

        @JsonProperty(required = true)
        public String requestId;  // 权限请求 ID

        @JsonProperty(required = true)
        public String choice;  // ALLOW_ONCE, ALLOW_SESSION, ALLOW_ALWAYS, DENY

        public Integer sessionDurationMinutes;  // 会话时长（可选）

        public PermissionResponseMessage() {}

        public PermissionResponseMessage(String requestId, String choice) {
            this.requestId = requestId;
            this.choice = choice;
        }
    }

    /**
     * 心跳消息
     */
    public static class PingMessage extends ClientMessage {
        public ClientMessageType getType() { return ClientMessageType.PING; }
    }

    /**
     * 获取历史消息
     */
    public static class GetHistoryMessage extends ClientMessage {
        public ClientMessageType getType() { return ClientMessageType.GET_HISTORY; }

        public Integer limit;  // 获取消息数量，默认 20

        public GetHistoryMessage() {}

        public GetHistoryMessage(Integer limit) {
            this.limit = limit;
        }
    }

    /**
     * 获取统计信息
     */
    public static class GetStatsMessage extends ClientMessage {
        public ClientMessageType getType() { return ClientMessageType.GET_STATS; }
    }

    // ===== 服务端 → 客户端消息 =====

    /**
     * 服务端发送的消息类型
     */
    public enum ServerMessageType {
        /** 连接确认 */
        CONNECTED,
        /** 响应开始 */
        RESPONSE_START,
        /** 文本增量（流式） */
        TEXT_DELTA,
        /** 工具调用通知 */
        TOOL_CALL,
        /** 权限请求 */
        PERMISSION_REQUEST,
        /** 响应完成 */
        RESPONSE_COMPLETE,
        /** 错误 */
        ERROR,
        /** 心跳响应 */
        PONG,
        /** 历史消息 */
        HISTORY,
        /** 统计信息 */
        STATS,
        /** 任务状态更新 */
        TASK_UPDATE
    }

    /**
     * 服务端消息基类
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static abstract class ServerMessage {
        public abstract ServerMessageType getType();
        public String requestId;      // 关联的请求 ID
        public Instant timestamp;

        public ServerMessage() {
            this.timestamp = Instant.now();
        }

        public ServerMessage(String requestId) {
            this.requestId = requestId;
            this.timestamp = Instant.now();
        }
    }

    /**
     * 连接确认消息
     */
    public static class ConnectedMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.CONNECTED; }

        public String sessionId;      // 会话 ID
        public String serverVersion;  // 服务端版本

        public ConnectedMessage() {}

        public ConnectedMessage(String sessionId) {
            this.sessionId = sessionId;
            this.serverVersion = "0.1.0";
        }
    }

    /**
     * 响应开始消息
     */
    public static class ResponseStartMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.RESPONSE_START; }

        public String turnId;  // 回合 ID

        public ResponseStartMessage() {}

        public ResponseStartMessage(String requestId, String turnId) {
            super(requestId);
            this.turnId = turnId;
        }
    }

    /**
     * 文本增量消息（流式输出）
     */
    public static class TextDeltaMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.TEXT_DELTA; }

        public String delta;  // 增量文本

        public TextDeltaMessage() {}

        public TextDeltaMessage(String delta) {
            this.delta = delta;
        }
    }

    /**
     * 工具调用通知消息（增强版）
     * <p>
     * 前端展示建议：
     * <ul>
     *   <li>status="started": 显示加载中动画 + 工具图标</li>
     *   <li>status="completed": 显示成功图标 + 可折叠的输出内容</li>
     *   <li>status="failed": 显示错误图标 + 错误详情</li>
     * </ul>
     */
    public static class ToolCallMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.TOOL_CALL; }

        public String toolCallId;       // 工具调用唯一 ID（用于关联多次状态更新）
        public String toolName;         // 工具名称
        public String toolDisplayName;  // 工具显示名称（前端友好）
        public String icon;             // 工具图标
        public Map<String, Object> input;  // 工具输入
        public String inputDisplay;     // 输入的可读展示
        public String status;           // "started", "in_progress", "completed", "failed"
        public String output;           // 工具输出（完成时）
        public String outputType;       // 输出类型："text", "json", "file", "image"
        public String error;            // 错误信息（失败时）
        public Long durationMs;         // 执行耗时（毫秒）
        public Integer progress;        // 进度百分比（0-100，用于长时间任务）

        public ToolCallMessage() {}

        public ToolCallMessage(String toolName, String status) {
            this.toolName = toolName;
            this.status = status;
            this.toolCallId = "tc_" + System.currentTimeMillis();
        }

        public ToolCallMessage(String toolName, Map<String, Object> input, String status) {
            this(toolName, status);
            this.input = input;
        }

        /**
         * 创建前端友好的工具调用消息
         */
        public static ToolCallMessage create(String toolName, String status) {
            ToolCallMessage msg = new ToolCallMessage(toolName, status);
            msg.toolDisplayName = formatDisplayName(toolName);
            msg.icon = getDefaultIcon(toolName);
            return msg;
        }

        private static String formatDisplayName(String toolName) {
            // 将 snake_case 转换为可读名称
            String result = toolName.replace('_', ' ')
                          .replaceAll("(?<!^)([A-Z])", " $1")
                          .toLowerCase();
            return result.substring(0, 1).toUpperCase() + result.substring(1);
        }

        private static String getDefaultIcon(String toolName) {
            return switch (toolName.toLowerCase()) {
                case "bash", "shell", "run_command" -> "⌨️";
                case "read", "read_file" -> "📄";
                case "write", "write_file" -> "✏️";
                case "edit", "edit_file" -> "📝";
                case "delete", "delete_file" -> "🗑️";
                case "grep", "search" -> "🔍";
                case "glob", "find" -> "📁";
                case "agent", "task", "spawn" -> "🤖";
                case "fetch", "http", "request" -> "🌐";
                default -> "🔧";
            };
        }
    }

    /**
     * 权限请求消息（增强版）
     * <p>
     * 前端展示建议：
     * <ul>
     *   <li>使用模态对话框或卡片展示</li>
     *   <li>根据 level.color 显示边框或背景色</li>
     *   <li>四个按钮：本次允许 / 会话允许 / 始终允许 / 拒绝</li>
     * </ul>
     */
    public static class PermissionRequestMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.PERMISSION_REQUEST; }

        public String id;                 // 权限请求 ID
        public String toolName;           // 工具名称
        public String toolDisplayName;    // 工具显示名称
        public String toolDescription;    // 工具描述
        public String icon;               // 工具图标

        // 风险等级信息
        public String level;              // 风险等级枚举名
        public String levelLabel;         // 等级显示文本
        public String levelIcon;          // 等级图标
        public String levelColor;         // 等级颜色（十六进制）

        // 输入信息
        public Map<String, Object> input; // 完整工具输入（可选，用于详细展示）
        public String inputSummary;       // 输入摘要
        public String inputDisplay;       // 输入的可读展示

        // 风险说明
        public String riskExplanation;    // 风险说明
        public List<String> riskDetails;  // 风险详情列表（可选）

        // 用户可选的权限选项
        public List<PermissionOption> permissionOptions;

        public PermissionRequestMessage() {}

        public PermissionRequestMessage(demo.k8s.agent.toolsystem.PermissionRequest request) {
            this.id = request.id();
            this.toolName = request.toolName();
            this.toolDisplayName = formatDisplayName(request.toolName());
            this.toolDescription = request.toolDescription();
            this.icon = getDefaultIcon(request.toolName());

            // 风险等级信息
            this.level = request.level().name();
            this.levelLabel = request.level().getLabel();
            this.levelIcon = request.level().getIcon();
            this.levelColor = request.level().name();  // 前端通过枚举名映射颜色

            this.inputSummary = request.inputSummary();
            this.riskExplanation = request.riskExplanation();

            // 设置默认权限选项
            this.permissionOptions = getDefaultPermissionOptions();
        }

        /**
         * 权限选项（前端按钮配置）
         */
        public static class PermissionOption {
            public String value;          // 选项值（发送到后端）
            public String label;          // 按钮显示文本
            public String shortcut;       // 快捷键
            public String style;          // 按钮样式："default", "primary", "warning", "danger"
            public String description;    // 选项说明

            public PermissionOption() {}

            public PermissionOption(String value, String label, String style) {
                this.value = value;
                this.label = label;
                this.style = style;
            }

            public PermissionOption(String value, String label, String style, String shortcut, String description) {
                this.value = value;
                this.label = label;
                this.style = style;
                this.shortcut = shortcut;
                this.description = description;
            }
        }

        private static List<PermissionOption> getDefaultPermissionOptions() {
            return List.of(
                new PermissionOption("ALLOW_ONCE", "本次允许", "default", "1", "仅允许本次调用"),
                new PermissionOption("ALLOW_SESSION", "会话允许", "primary", "2", "当前会话内不再询问"),
                new PermissionOption("ALLOW_ALWAYS", "始终允许", "primary", "3", "永久允许此工具"),
                new PermissionOption("DENY", "拒绝", "danger", "4", "拒绝此调用")
            );
        }

        private static String formatDisplayName(String toolName) {
            String result = toolName.replace('_', ' ')
                          .replaceAll("(?<!^)([A-Z])", " $1")
                          .toLowerCase();
            return result.substring(0, 1).toUpperCase() + result.substring(1);
        }

        private static String getDefaultIcon(String toolName) {
            return switch (toolName.toLowerCase()) {
                case "bash", "shell", "run_command" -> "⌨️";
                case "read", "read_file" -> "📄";
                case "write", "write_file" -> "✏️";
                case "edit", "edit_file" -> "📝";
                case "delete", "delete_file" -> "🗑️";
                case "grep", "search" -> "🔍";
                case "glob", "find" -> "📁";
                case "agent", "task", "spawn" -> "🤖";
                case "fetch", "http", "request" -> "🌐";
                default -> "🔧";
            };
        }
    }

    /**
     * 响应完成消息
     */
    public static class ResponseCompleteMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.RESPONSE_COMPLETE; }

        public String content;            // 完整响应内容
        public long inputTokens;          // 输入 token 数
        public long outputTokens;         // 输出 token 数
        public long durationMs;           // 耗时（毫秒）
        public int toolCalls;             // 工具调用次数

        public ResponseCompleteMessage() {}

        public ResponseCompleteMessage(String content, long inputTokens, long outputTokens,
                                        long durationMs, int toolCalls) {
            this.content = content;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.durationMs = durationMs;
            this.toolCalls = toolCalls;
        }
    }

    /**
     * 错误消息
     */
    public static class ErrorMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.ERROR; }

        public String code;               // 错误代码
        public String message;            // 错误信息
        public Map<String, Object> details; // 详细信息

        public ErrorMessage() {}

        public ErrorMessage(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    /**
     * 心跳响应消息
     */
    public static class PongMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.PONG; }

        public Instant serverTime;        // 服务端时间

        public PongMessage() {}

        public PongMessage(Instant serverTime) {
            this.serverTime = serverTime;
        }
    }

    /**
     * 历史消息
     */
    public static class HistoryMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.HISTORY; }

        public List<ChatMessage> messages;

        public HistoryMessage() {}

        public HistoryMessage(List<ChatMessage> messages) {
            this.messages = messages;
        }
    }

    /**
     * 统计信息消息
     */
    public static class StatsMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.STATS; }

        public SessionStats stats;

        public StatsMessage() {}

        public StatsMessage(SessionStats stats) {
            this.stats = stats;
        }
    }

    /**
     * 任务状态更新消息
     */
    public static class TaskUpdateMessage extends ServerMessage {
        public ServerMessageType getType() { return ServerMessageType.TASK_UPDATE; }

        public String taskId;
        public String name;
        public String status;  // PENDING, RUNNING, COMPLETED, FAILED, STOPPED
        public String assignedTo;

        public TaskUpdateMessage() {}

        public TaskUpdateMessage(String taskId, String name, String status, String assignedTo) {
            this.taskId = taskId;
            this.name = name;
            this.status = status;
            this.assignedTo = assignedTo;
        }
    }

    // ===== 数据传输对象 =====

    /**
     * 聊天消息 DTO
     */
    public static class ChatMessage {
        public String id;
        public String type;         // USER, ASSISTANT, SYSTEM, TOOL
        public String content;
        public Instant timestamp;
        public long inputTokens;
        public long outputTokens;

        public ChatMessage() {}

        public ChatMessage(String id, String type, String content, Instant timestamp) {
            this.id = id;
            this.type = type;
            this.content = content;
            this.timestamp = timestamp;
        }

        public static ChatMessage from(demo.k8s.agent.state.ChatMessage internal) {
            return new ChatMessage(
                    internal.id(),
                    internal.type().name(),
                    internal.content(),
                    internal.timestamp()
            );
        }
    }

    /**
     * 会话统计 DTO
     */
    public static class SessionStats {
        public String sessionId;
        public Instant sessionStartedAt;
        public long durationSeconds;
        public long totalModelCalls;
        public long totalToolCalls;
        public long totalInputTokens;
        public long totalOutputTokens;
        public double averageModelLatencyMs;
        public double toolSuccessRate;

        public SessionStats() {}

        public static SessionStats from(demo.k8s.agent.state.ConversationSession.SessionSnapshot snapshot) {
            SessionStats stats = new SessionStats();
            stats.sessionId = snapshot.sessionId();
            stats.sessionStartedAt = snapshot.createdAt();
            stats.durationSeconds = snapshot.sessionDuration().getSeconds();
            stats.totalInputTokens = snapshot.totalInputTokens();
            stats.totalOutputTokens = snapshot.totalOutputTokens();
            return stats;
        }
    }
}
