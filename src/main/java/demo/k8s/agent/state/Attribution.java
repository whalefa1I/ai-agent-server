package demo.k8s.agent.state;

import java.time.Instant;
import java.util.Map;

/**
 * 归因信息，记录某个修改是由哪个工具调用导致的。
 * 与 Claude Code 的 Attribution 类型对齐。
 *
 * @param id 归因记录 ID
 * @param messageId 消息 ID
 * @param toolCallId 工具调用 ID（如果有）
 * @param toolName 工具名称
 * @param timestamp 时间戳
 * @param metadata 额外元数据
 */
public record Attribution(
        String id,
        String messageId,
        String toolCallId,
        String toolName,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public Attribution {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(messageId);
        java.util.Objects.requireNonNull(timestamp);
    }

    public static Attribution create(
            String messageId,
            String toolCallId,
            String toolName) {
        return new Attribution(
                generateId(),
                messageId,
                toolCallId,
                toolName,
                Instant.now(),
                Map.of()
        );
    }

    private static String generateId() {
        return "attr_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 是否为工具调用导致的修改
     */
    public boolean isFromToolCall() {
        return toolCallId != null && !toolCallId.isBlank();
    }
}
