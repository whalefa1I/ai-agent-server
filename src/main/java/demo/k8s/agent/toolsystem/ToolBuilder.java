package demo.k8s.agent.toolsystem;

/**
 * 与 Claude Code {@code buildTool(def)} 的工厂入口已移至 {@link ClaudeToolFactory#buildTool(ToolDefPartial)}；
 * {@link ClaudeLikeTool} 接口上的 {@code default} 方法仍承担 TS {@code TOOL_DEFAULTS} 中未在 {@link ToolDefPartial} 显式给出的字段。
 */
public final class ToolBuilder {

    private ToolBuilder() {}
}
