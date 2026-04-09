package demo.k8s.agent.toolsystem;

import org.springframework.ai.tool.ToolCallback;

/**
 * 一项可注册能力：Claude 式元数据 + Spring AI 可调用实现。
 */
public record ToolModule(ClaudeLikeTool spec, ToolCallback callback) {}
