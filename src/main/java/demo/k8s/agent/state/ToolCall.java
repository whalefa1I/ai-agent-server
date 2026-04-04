package demo.k8s.agent.state;

/**
 * 工具调用记录。
 */
public record ToolCall(
        String id,
        String name,
        String arguments,
        String result,
        boolean success,
        String errorMessage
) {}
