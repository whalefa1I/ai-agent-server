package demo.k8s.agent.toolsystem;

/**
 * 与 Claude Code 工具分类对齐的枚举（见仓库 {@code src/tools.ts} 中的能力划分）。
 */
public enum ToolCategory {

    /** 读取、写入、编辑与搜索代码库 */
    FILE,

    /** 文件系统操作（glob, file_read, file_write, grep 等） */
    FILE_SYSTEM,

    /** Bash / PowerShell 等 */
    SHELL,

    /** AgentTool、子任务、团队协调等 */
    AGENT,

    /** MCP、LSP、WebFetch、WebSearch 等 */
    EXTERNAL,

    /** Plan 模式、Todo 等 */
    PLANNING,

    /** Cron、远程触发等 */
    SCHEDULING,

    /** 特性门控的实验性工具（对齐 Snip、Workflow 等） */
    EXPERIMENTAL,

    /** 记忆系统相关工具（memory_search 等） */
    MEMORY,

    /** 未归类或演示用 */
    OTHER
}
