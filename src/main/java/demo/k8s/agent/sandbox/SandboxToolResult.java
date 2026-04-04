package demo.k8s.agent.sandbox;

import java.util.List;
import java.util.Map;

/**
 * AgentScope 沙盒工具调用结果。
 *
 * @param success  是否成功
 * @param output   输出文本
 * @param content  内容列表（MCP 格式）
 * @param metadata 元数据
 * @param error    错误信息
 */
public record SandboxToolResult(
        boolean success,
        String output,
        List<Object> content,
        Map<String, Object> metadata,
        String error
) {
    /**
     * 创建成功结果。
     *
     * @param output 输出文本
     * @return 成功结果
     */
    public static SandboxToolResult success(String output) {
        return new SandboxToolResult(true, output, List.of(), Map.of(), null);
    }

    /**
     * 创建成功结果（带内容列表）。
     *
     * @param output  输出文本
     * @param content 内容列表
     * @return 成功结果
     */
    public static SandboxToolResult success(String output, List<Object> content) {
        return new SandboxToolResult(true, output, content, Map.of(), null);
    }

    /**
     * 创建错误结果。
     *
     * @param error 错误信息
     * @return 错误结果
     */
    public static SandboxToolResult error(String error) {
        return new SandboxToolResult(false, null, List.of(), Map.of(), error);
    }
}
