/**
 * 是否需要「整套」子 Agent 系统？
 *
 * <p>若产品目标接近 Claude Code 的多智能体编排，概念上需要
 * <a href="https://zread.ai/instructkr/claude-code/8-agent-tool-and-sub-agent-spawning">Agent Tool and Sub-Agent Spawning</a>
 * 所述能力：委派原语、独立工具池、同步/异步生命周期、可选隔离（worktree）与权限边界。
 *
 * <p>不需要在单个 Spring demo 里 1:1 复刻 {@code AgentTool.tsx} / {@code runAgent.ts} / {@code spawnMultiAgent.ts}；应分阶段落地。
 *
 * <p>对应关系（摘要）：
 * <ul>
 *   <li>委派 + 子类型：Claude Code 为 {@code AgentTool} + {@code subagent_type}；本 demo 为 {@code TaskTool} + {@code ClaudeSubagentType}。</li>
 *   <li>子 Agent 工具池：CC 为 {@code assembleToolPool} + {@code filterToolsForAgent}；demo 由 spring-ai-agent-utils 子客户端配置。</li>
 *   <li>同步 / 异步 / 队友：见 {@link demo.k8s.agent.subagent.SpawnPath}；当前以同步子调用为主。</li>
 *   <li>Fork 缓存对齐、worktree 隔离：未实现；K8s 侧 {@code k8s_sandbox_run} 可作执行隔离占位。</li>
 * </ul>
 *
 * <p>扩展建议：先稳定类型化同步子 Agent 与工具白名单；再按需加异步任务、fork 或独立 worktree。
 */
package demo.k8s.agent.subagent;
