/**
 * 与 Claude Code {@code src/commands.ts} 对齐的<strong>短路</strong>命令系统：
 * <ul>
 *   <li>元数据：{@link demo.k8s.agent.commandsystem.SlashCommand}</li>
 *   <li>来源分区：{@link demo.k8s.agent.commandsystem.SlashCommandSource}</li>
 *   <li>合并 / 去重：{@link demo.k8s.agent.commandsystem.SlashCommandAssembly}</li>
 *   <li>多来源：{@link demo.k8s.agent.commandsystem.SlashCommandProvider}（Spring {@code @Order} 决定与 TS {@code loadAllCommands} 相同的拼接顺序）</li>
 *   <li>入口：{@link demo.k8s.agent.commandsystem.SlashCommandService#getCommands()}</li>
 * </ul>
 * 不包含 80+ 内置命令实现；仅占位 + 可插拔 Provider。HTTP：{@code GET /api/commands/slash}。
 */
package demo.k8s.agent.commandsystem;
