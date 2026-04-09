/**
 * 与 Claude Code {@code src/Tool.ts} / {@code tools.ts} 对齐的「工具系统」骨架：
 * <ul>
 *   <li>每项能力 = {@link demo.k8s.agent.toolsystem.ClaudeLikeTool}：JSON Schema 描述、
 *       {@link ClaudeLikeTool#isEnabled()}、{@link ClaudeLikeTool#isReadOnly(com.fasterxml.jackson.databind.JsonNode)}、
 *       {@link ClaudeLikeTool#isConcurrencySafe(com.fasterxml.jackson.databind.JsonNode)} 等，默认值与 TS 侧
 *       {@code TOOL_DEFAULTS} / {@code buildTool} 一致。</li>
 *   <li>{@link demo.k8s.agent.toolsystem.ToolModule} 将元数据与 {@link org.springframework.ai.tool.ToolCallback} 绑定。</li>
 *   <li>{@link demo.k8s.agent.toolsystem.ToolDefPartial} + {@link demo.k8s.agent.toolsystem.ClaudeToolFactory} 对应 TS {@code ToolDef} / {@code buildTool}。</li>
 *   <li>{@link demo.k8s.agent.toolsystem.ToolAssembly} 对齐 {@code tools.ts} 中的 {@code filterToolsByDenyRules}、简单模式、{@code assembleToolPool}、{@code getMergedTools}。</li>
 *   <li>{@link demo.k8s.agent.toolsystem.ToolRegistry} 在注册表之上应用 {@link demo.k8s.agent.toolsystem.ToolAssembly#assembleFilteredToolPool} 与类别 / 只读过滤。</li>
 *   <li>分类见 {@link demo.k8s.agent.toolsystem.ToolCategory}（文件 / Shell / Agent / 外部 / 规划 / 调度）。</li>
 * </ul>
 */
package demo.k8s.agent.toolsystem;
