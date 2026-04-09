package demo.k8s.agent.toolsystem;

/**
 * 与当前 demo 中实际注册的 Spring AI 工具名对齐的 {@link ClaudeLikeTool} 描述。
 * 扩展新工具时：在此增加一项，并在 {@link demo.k8s.agent.config.DemoToolRegistryConfiguration} 中注册 {@link ToolModule}。
 */
public final class DemoToolSpecs {

    private static final String K8S_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "command": { "type": "string", "description": "单行 shell" }
                      },
                      "required": ["command"]
                    }
                    """;

    private static final String SKILL_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "command": { "type": "string", "description": "技能名" }
                      },
                      "required": ["command"]
                    }
                    """;

    private static final String TASK_SCHEMA =
            """
                    {
                      "type": "object",
                      "description": "TaskCall：子代理名、提示词等，详见 TaskTool 定义"
                    }
                    """;

    // ========== 本地工具 Schema 定义 ==========
    private static final String GLOB_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "pattern": { "type": "string", "description": "Glob pattern (e.g., **/SKILL.md, **/*.java). Prefer this field." },
                        "path": { "type": "string", "description": "Base directory to search under workspace root, OR if pattern is omitted and this value contains * or ?, it is treated as the glob pattern" }
                      }
                    }
                    """;

    private static final String FILE_READ_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "path": { "type": "string", "description": "File path to read" },
                        "offset": { "type": "integer", "description": "Starting line offset (0-based)" },
                        "limit": { "type": "integer", "description": "Maximum lines to read" }
                      },
                      "required": ["path"]
                    }
                    """;

    private static final String FILE_WRITE_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "path": { "type": "string", "description": "File path to write" },
                        "content": { "type": "string", "description": "Content to write" }
                      },
                      "required": ["path", "content"]
                    }
                    """;

    private static final String FILE_EDIT_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "path": { "type": "string", "description": "File path to edit" },
                        "oldText": { "type": "string", "description": "Text to find and replace" },
                        "newText": { "type": "string", "description": "Replacement text" }
                      },
                      "required": ["path", "oldText", "newText"]
                    }
                    """;

    private static final String BASH_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "command": { "type": "string", "description": "Shell command to execute" },
                        "workdir": { "type": "string", "description": "Working directory" },
                        "timeout": { "type": "integer", "description": "Timeout in milliseconds" }
                      },
                      "required": ["command"]
                    }
                    """;

    private static final String READ_CONTEXT_OBJECT_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "id": { "type": "string", "description": "Context object id (e.g. ctx-obj-...)" },
                        "offset": { "type": "integer", "description": "Character offset (optional)" },
                        "limit": { "type": "integer", "description": "Max characters (optional; server-capped)" }
                      },
                      "required": ["id"]
                    }
                    """;

    private static final String GREP_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "pattern": { "type": "string", "description": "Regex pattern to search for" },
                        "path": { "type": "string", "description": "Directory to search in" },
                        "include": { "type": "string", "description": "Glob pattern to include files" },
                        "exclude": { "type": "string", "description": "Glob pattern to exclude files" },
                        "contextLines": { "type": "integer", "description": "Context lines to show" },
                        "caseSensitive": { "type": "boolean", "description": "Case sensitive search" }
                      },
                      "required": ["pattern"]
                    }
                    """;

    private DemoToolSpecs() {}

    /** {@code k8s_sandbox_run} — 对应 K8s Job 沙盒 */
    public static ClaudeLikeTool k8sSandboxRun() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "k8s_sandbox_run",
                        ToolCategory.EXTERNAL,
                        "在 Kubernetes 中以 Job 执行 shell，返回 Pod 日志。",
                        K8S_SCHEMA,
                        null,
                        false));
    }

    /** spring-ai-agent-utils 中 SkillsTool 注册名为 {@code Skill} */
    public static ClaudeLikeTool skill() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "Skill",
                        ToolCategory.EXTERNAL,
                        "按名称加载 SKILL.md 技能说明（只读加载）。",
                        SKILL_SCHEMA,
                        null,
                        true));
    }

    /** Task 子 Agent — spring-ai-agent-utils TaskTool */
    public static ClaudeLikeTool task() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "Task",
                        ToolCategory.AGENT,
                        "将任务委派给子 Agent（多智能体协调）。",
                        TASK_SCHEMA,
                        null,
                        false));
    }

    // ========== 本地工具定义 ==========

    /** glob - 文件匹配工具 */
    public static ClaudeLikeTool glob() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "glob",
                        ToolCategory.FILE_SYSTEM,
                        "匹配文件路径（支持 glob 模式如 **/*.java）",
                        GLOB_SCHEMA,
                        null,
                        true));
    }

    /** file_read - 文件读取工具 */
    public static ClaudeLikeTool fileRead() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_read",
                        ToolCategory.FILE_SYSTEM,
                        "读取文件内容（支持范围 offset/limit）",
                        FILE_READ_SCHEMA,
                        null,
                        true));
    }

    /** file_write - 文件写入工具 */
    public static ClaudeLikeTool fileWrite() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_write",
                        ToolCategory.FILE_SYSTEM,
                        "写入文件内容（原子操作，安全写入）",
                        FILE_WRITE_SCHEMA,
                        null,
                        false));
    }

    /** file_edit - 文件编辑工具 */
    public static ClaudeLikeTool fileEdit() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_edit",
                        ToolCategory.FILE_SYSTEM,
                        "编辑文件内容（字符串替换，带匹配验证）",
                        FILE_EDIT_SCHEMA,
                        null,
                        false));
    }

    /** bash - Shell 命令执行工具 */
    public static ClaudeLikeTool bash() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "bash",
                        ToolCategory.SHELL,
                        "执行 Shell 命令（带危险命令检测）",
                        BASH_SCHEMA,
                        null,
                        false));
    }

    /** grep - 内容搜索工具 */
    public static ClaudeLikeTool grep() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "grep",
                        ToolCategory.FILE_SYSTEM,
                        "在文件内容中搜索正则表达式匹配项",
                        GREP_SCHEMA,
                        null,
                        true));
    }

    /** read_context_object - 按 id 读取外置超长工具结果 */
    public static ClaudeLikeTool readContextObject() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "read_context_object",
                        ToolCategory.FILE_SYSTEM,
                        "Retrieve truncated tool output by context object id (session-bound on server).",
                        READ_CONTEXT_OBJECT_SCHEMA,
                        null,
                        true));
    }
}
