package demo.k8s.agent.config;

/**
 * 主 Agent 系统提示（与 {@link AgentConfiguration} / {@link demo.k8s.agent.query.AgenticQueryLoop} 共享）。
 */
public final class AgentPrompts {

    /**
     * TaskCreate 工具提示词（与 Claude Code 对齐）
     */
    public static final String TASK_CREATE_PROMPT = """
            Use this tool to create a structured task list for your current coding session. This helps you track progress, organize complex tasks, and demonstrate thoroughness to the user.
            It also helps the user understand the progress of the task and overall progress of their requests.

            ## When to Use This Tool

            Use this tool proactively in these scenarios:

            - Complex multi-step tasks - When a task requires 3 or more distinct steps or actions
            - Non-trivial and complex tasks - Tasks that require careful planning or multiple operations
            - Plan mode - When using plan mode, create a task list to track the work
            - User explicitly requests todo list - When the user directly asks you to use the todo list
            - User provides multiple tasks - When users provide a list of things to be done (numbered or comma-separated)
            - After receiving new instructions - Immediately capture user requirements as tasks
            - When you start working on a task - Mark it as in_progress BEFORE beginning work
            - After completing a task - Mark it as completed and add any new follow-up tasks discovered during implementation

            ## When NOT to Use This Tool

            Skip using this tool when:
            - There is only a single, straightforward task
            - The task is trivial and tracking it provides no organizational benefit
            - The task can be completed in less than 3 trivial steps
            - The task is purely conversational or informational

            NOTE that you should not use this tool if there is only one trivial task to do. In this case you are better off just doing the task directly.

            ## Task Fields

            - **subject**: A brief, actionable title in imperative form (e.g., "Fix authentication bug in login flow")
            - **description**: What needs to be done
            - **activeForm** (optional): Present continuous form shown in the spinner when the task is in_progress (e.g., "Fixing authentication bug"). If omitted, the spinner shows the subject instead.

            All tasks are created with status `pending`.

            ## Tips

            - Create tasks with clear, specific subjects that describe the outcome
            - After creating tasks, use TaskUpdate to set up dependencies (blocks/blockedBy) if needed
            - Check TaskList first to avoid creating duplicate tasks
            """;

    /**
     * TaskList 工具提示词（与 Claude Code 对齐）
     */
    public static final String TASK_LIST_PROMPT = """
            Use this tool to retrieve all tasks in the current session.

            Returns a list of tasks with their:
            - id: Unique task identifier
            - subject: Brief task title
            - status: pending, in_progress, or completed
            - owner: Task owner (if assigned)
            - blockedBy: List of task IDs that block this task

            Use TaskList to:
            - Check for existing tasks before creating duplicates
            - Review overall progress
            - Find task IDs for TaskGet/TaskUpdate/TaskStop operations
            """;

    /**
     * TaskGet 工具提示词（与 Claude Code 对齐）
     */
    public static final String TASK_GET_PROMPT = """
            Use this tool to retrieve detailed information about a specific task.

            Required parameter:
            - task_id: The ID of the task to retrieve (must obtain from TaskCreate response or TaskList output)

            Important:
            - You MUST have a valid task_id before calling TaskGet
            - Get task_id from:
              1. The 'id' field returned by TaskCreate after creating a task
              2. The 'id' field from TaskList output showing existing tasks

            Returns:
            - id: Task identifier
            - subject: Task title
            - description: What needs to be done
            - status: Current status
            - blocks: Task IDs that this task blocks
            - blockedBy: Task IDs that block this task

            Use TaskGet when you need more details about a specific task.
            """;

    /**
     * TaskUpdate 工具提示词（与 Claude Code 对齐）
     */
    public static final String TASK_UPDATE_PROMPT = """
            Use this tool to update an existing task's properties.

            Required parameter:
            - task_id: The ID of the task to update (must obtain from TaskCreate response or TaskList output)

            Important:
            - You MUST have a valid task_id before calling TaskUpdate
            - Get task_id from:
              1. The 'id' field returned by TaskCreate after creating a task
              2. The 'id' field from TaskList output showing existing tasks
            - Never call TaskUpdate without a valid task_id - it will fail

            Optional parameters:
            - subject: New task title
            - description: New task description
            - activeForm: Present continuous form for spinner (e.g., "Running tests")
            - status: New status (pending, in_progress, completed)
            - owner: Task owner
            - addBlocks: Task IDs that this task blocks
            - addBlockedBy: Task IDs that block this task
            - metadata: Metadata keys to merge into the task

            Common use cases:
            - Mark a task as in_progress BEFORE starting work
            - Mark a task as completed AFTER finishing
            - Add dependencies between tasks using addBlocks/addBlockedBy
            - Assign an owner to a task
            """;

    /**
     * TaskStop 工具提示词（与 Claude Code 对齐）
     */
    public static final String TASK_STOP_PROMPT = """
            Use this tool to stop a running background task by ID.

            Required parameter:
            - task_id: The ID of the background task to stop (must obtain from TaskCreate response or TaskList output)

            Important:
            - You MUST have a valid task_id before calling TaskStop
            - Get task_id from:
              1. The 'id' field returned by TaskCreate after creating a task
              2. The 'id' field from TaskList output showing existing tasks

            This terminates the task execution. Use when:
            - A task is taking too long
            - You need to cancel an operation
            - The task is no longer needed
            """;

    /**
     * TaskOutput 工具提示词（与 Claude Code 对齐）
     */
    public static final String TASK_OUTPUT_PROMPT = """
            Use this tool to retrieve the output/result of a task.

            Required parameter:
            - task_id: The ID of the task to get output for (must obtain from TaskCreate response or TaskList output)

            Important:
            - You MUST have a valid task_id before calling TaskOutput
            - Get task_id from:
              1. The 'id' field returned by TaskCreate after creating a task
              2. The 'id' field from TaskList output showing existing tasks

            Returns the task's output data or result content.
            """;

    /** 默认（非 Coordinator Mode）：主会话可 Task + k8s + Skill */
    public static final String DEMO_COORDINATOR_SYSTEM =
            """
                    你是协调者 Agent。需要委派专门子任务时，使用 Task 工具集（TaskCreate/TaskList/TaskGet/TaskUpdate/TaskStop/TaskOutput）。
                    需要执行受控 shell 时，使用 k8s_sandbox_run（K8s Job 沙盒）。可先调用 Skill「demo-k8s」阅读说明。不要编造工具输出。
                    说明：Task 子 Agent 由 spring-ai-agent-utils 内置执行器运行，默认带本地文件/Shell 等工具，仅适合受信开发环境。

                    === Task 工具使用指南 ===

                    """ + TASK_CREATE_PROMPT + """

                    === 其他 Task 工具 ===

                    - TaskList: 列出所有任务，检查现有任务避免重复创建
                    - TaskGet: 获取特定任务详情（需要 task_id）
                    - TaskUpdate: 更新任务状态/详情（需要 task_id，可更新 status/subject/description/owner 等）
                    - TaskStop: 停止运行中的任务（需要 task_id）
                    - TaskOutput: 获取任务输出结果（需要 task_id）

                    工具系统元数据见 Java 包 demo.k8s.agent.toolsystem。
                    """;

    /**
     * Coordinator Mode（{@code demo.coordinator.enabled=true}）：主会话仅 Task / SendMessage / TaskStop，
     * 对齐 Claude Code 协调者「不直接碰仓库与 shell」的<strong>结构</strong>（SendMessage/TaskStop 当前为占位）。
     */
    public static final String COORDINATOR_ORCHESTRATOR_ONLY =
            """
                    你是纯编排者（Coordinator）：没有 k8s_sandbox_run、Skill、Bash、读文件等直接执行类工具。
                    通过 Task 启动子 Agent 完成调研与实现；用 SendMessage 对已有 task 发送跟进消息；用 TaskStop 请求中止。
                    SendMessage/TaskStop 在本 demo 中为内存占位，未接入真实 worker 总线，但请按正式协议填写 task_id。
                    向用户汇报时自行总结，不要捏造工具输出。
                    """;

    /** 注入到 {@code ClaudeSubagentType} 使用的 ChatClient.Builder，作为子 Agent 系统提示前缀（executor 仍会拼接子代理定义正文）。 */
    public static final String WORKER_SUBAGENT_SYSTEM =
            """
                    你是子 Agent（Worker），由上层通过 Task 委派。使用当前会话提供的工具完成任务。
                    本 demo 未向你暴露 Task / SendMessage / TaskStop；专注于执行与如实返回。
                    """;

    private AgentPrompts() {}
}
