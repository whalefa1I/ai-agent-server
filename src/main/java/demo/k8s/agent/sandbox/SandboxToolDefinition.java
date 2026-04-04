package demo.k8s.agent.sandbox;

import java.util.Map;

/**
 * AgentScope 沙盒工具定义。
 *
 * @param name        工具名称
 * @param description 工具描述
 * @param inputSchema 输入参数 JSON Schema
 */
public record SandboxToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
    public SandboxToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }
}
