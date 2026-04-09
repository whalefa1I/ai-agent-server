package demo.k8s.agent.subagent;

import java.time.Instant;

/**
 * 子 Agent 运行事件（v1 M4 契约冻结）。
 * <p>
 * 对齐 v2 双栈架构的 SubRunEvent 协议，支持本地/远端运行时同签名。
 */
public class SubRunEvent {

    private final String runId;
    private final String sessionId;
    private final EventType type;
    private final Instant timestamp;
    private final String content;
    private final String toolName;
    private final String toolArgs;

    public SubRunEvent(String runId, String sessionId, EventType type, Instant timestamp,
                       String content, String toolName, String toolArgs) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.type = type;
        this.timestamp = timestamp;
        this.content = content;
        this.toolName = toolName;
        this.toolArgs = toolArgs;
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public EventType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getContent() {
        return content;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolArgs() {
        return toolArgs;
    }

    /**
     * 事件类型（对齐 v2 终态语义）
     */
    public enum EventType {
        STARTED,
        RUNNING,
        WAITING,
        THOUGHT,
        TOOL_CALL,
        SUSPEND,
        MESSAGE,
        COMPLETED,
        FAILED,
        TIMEOUT,
        REJECTED
    }

    /**
     * 创建 STARTED 事件
     */
    public static SubRunEvent started(String runId, String sessionId) {
        return new SubRunEvent(runId, sessionId, EventType.STARTED, Instant.now(), null, null, null);
    }

    /**
     * 创建 COMPLETED 事件
     */
    public static SubRunEvent completed(String runId, String sessionId, String result) {
        return new SubRunEvent(runId, sessionId, EventType.COMPLETED, Instant.now(), result, null, null);
    }

    /**
     * 创建 FAILED 事件
     */
    public static SubRunEvent failed(String runId, String sessionId, String error) {
        return new SubRunEvent(runId, sessionId, EventType.FAILED, Instant.now(), error, null, null);
    }

    /**
     * 创建 TIMEOUT 事件
     */
    public static SubRunEvent timeout(String runId, String sessionId) {
        return new SubRunEvent(runId, sessionId, EventType.TIMEOUT, Instant.now(), "Task exceeded Wall-Clock TTL", null, null);
    }

    /**
     * 创建 REJECTED 事件
     */
    public static SubRunEvent rejected(String runId, String sessionId, String reason) {
        return new SubRunEvent(runId, sessionId, EventType.REJECTED, Instant.now(), reason, null, null);
    }
}
