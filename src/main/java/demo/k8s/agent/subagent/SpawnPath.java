package demo.k8s.agent.subagent;

/**
 * 与 Claude Code {@code AgentTool} 的派发路径对齐的概念枚举（见仓库 {@code src/tools/AgentTool/}、
 * <a href="https://zread.ai/instructkr/claude-code/8-agent-tool-and-sub-agent-spawning">Zread: Agent Tool and Sub-Agent Spawning</a>）。
 * <p>
 * 本 demo 不实现全部路径；仅用于架构对齐与后续扩展命名。
 */
public enum SpawnPath {

    /**
     * 阻塞父回合直到子 Agent 结束；通常对应 {@code runAgent()} 同步迭代。
     * 当前 demo：{@link org.springaicommunity.agent.tools.task.TaskTool} + {@code ClaudeSubagentType} 近似此路径（库内多为同步
     * {@code call()}，非完整 {@code runAgent} 生成器）。
     */
    SYNCHRONOUS_TYPED_AGENT,

    /** {@code run_in_background} / coordinator / fork 背景任务等触发的异步子 Agent */
    ASYNC_BACKGROUND,

    /** {@code name} + {@code team_name} 触发的队友 / 多 Agent 终端派发（tmux / in-process 等） */
    TEAMMATE,

    /**
     * {@code FORK_SUBAGENT} 且省略 {@code subagent_type} 时的 fork 实验路径（提示缓存对齐、{@code useExactTools}）。
     */
    FORK_CACHE_ALIGNED
}
