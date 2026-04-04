package demo.k8s.agent.state;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息记录，与 Claude Code 的 Message 类型对齐。
 */
public record ChatMessage(
        String id,
        MessageType type,
        String content,
        Instant timestamp,
        Map<String, Object> metadata,
        List<ToolCall> toolCalls,
        String toolResponseId,
        long inputTokens,
        long outputTokens
) {
    public static ChatMessage user(String content) {
        return new ChatMessage(
                generateId(),
                MessageType.USER,
                content,
                Instant.now(),
                Map.of(),
                null,
                null,
                0,
                0
        );
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(
                generateId(),
                MessageType.ASSISTANT,
                content,
                Instant.now(),
                Map.of(),
                toolCalls,
                null,
                0,
                0
        );
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(
                generateId(),
                MessageType.SYSTEM,
                content,
                Instant.now(),
                Map.of(),
                null,
                null,
                0,
                0
        );
    }

    public static ChatMessage tool(String content, String toolResponseId) {
        return new ChatMessage(
                generateId(),
                MessageType.TOOL,
                content,
                Instant.now(),
                Map.of(),
                null,
                toolResponseId,
                0,
                0
        );
    }

    private static String generateId() {
        return "msg_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    public ChatMessage withTokenCounts(long input, long output) {
        return new ChatMessage(
                this.id,
                this.type,
                this.content,
                this.timestamp,
                this.metadata,
                this.toolCalls,
                this.toolResponseId,
                input,
                output
        );
    }
}
