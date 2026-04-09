package demo.k8s.agent.tools.local.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task 工具集 - 任务管理（与 claude-code 的 Task* 工具对齐）
 * <p>
 * 与前端契约：{@link LocalToolResult#getMetadata()} 序列化后由服务端写入 artifact body 顶层 {@code metadata}，
 * TaskList 为 {@code { "tasks": [ ... ] }}，TaskCreate 为 {@code { "task": { "id", "subject" } }}，
 * 见 ai-agent-web {@code extractTaskRowsFromAiAgentToolBody}。
 *
 * 功能：
 * - TaskCreate: 创建新任务
 * - TaskList: 列出所有任务
 * - TaskGet: 获取任务详情
 * - TaskUpdate: 更新任务状态
 * - TaskStop: 停止任务
 * - TaskOutput: 获取任务输出
 */
public class TaskTools {

    private static final Logger log = LoggerFactory.getLogger(TaskTools.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TASK_CONTRACT_VERSION = "task-contract.v1";

    /**
     * 任务状态
     */
    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        STOPPED,
        FAILED
    }

    /**
     * 任务数据结构
     */
    public static class Task {
        public final String id;
        public String subject;
        public String description;
        public TaskStatus status;
        public String owner;
        public String activeForm;
        public List<String> blocks;
        public List<String> blockedBy;
        public Map<String, Object> metadata;
        public final long createdAt;
        public Long completedAt;

        public Task(String id, String subject, String description) {
            this.id = id;
            this.subject = subject;
            this.description = description;
            this.status = TaskStatus.PENDING;
            this.blocks = new ArrayList<>();
            this.blockedBy = new ArrayList<>();
            this.metadata = new HashMap<>();
            this.createdAt = System.currentTimeMillis();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("subject", subject);
            map.put("description", description);
            map.put("status", status.name().toLowerCase());
            map.put("owner", owner);
            map.put("activeForm", activeForm);
            map.put("blocks", new ArrayList<>(blocks));
            map.put("blockedBy", new ArrayList<>(blockedBy));
            map.put("metadata", metadata);
            map.put("createdAt", createdAt);
            if (completedAt != null) {
                map.put("completedAt", completedAt);
            }
            return map;
        }
    }

    /**
     * 全局任务存储（会话级别）
     */
    private static final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private static final AtomicInteger nextTaskId = new AtomicInteger(1);

    private static LocalToolResult contractError(String toolName, String errorCode, String message, Map<String, Object> input, List<String> required) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("version", TASK_CONTRACT_VERSION);
        contract.put("tool", toolName);
        contract.put("errorCode", errorCode);
        contract.put("required", required);
        contract.put("inputKeys", input == null ? List.of() : new ArrayList<>(input.keySet()));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskContract", contract);

        return LocalToolResult.builder()
                .success(false)
                .error(message)
                .executionLocation("local")
                .metadata(MAPPER.valueToTree(meta))
                .build();
    }

    private static LocalToolResult contractSuccess(String content, String toolName, Map<String, Object> payload) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("version", TASK_CONTRACT_VERSION);
        contract.put("tool", toolName);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.putAll(payload);
        meta.put("taskContract", contract);

        return LocalToolResult.builder()
                .success(true)
                .content(content)
                .executionLocation("local")
                .metadata(MAPPER.valueToTree(meta))
                .build();
    }

    // ===== TaskCreate =====

    /**
     * TaskCreate 工具提示词（与 Claude Code 对齐）
     */
    private static final String TASK_CREATE_PROMPT = """
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

    private static final String TASK_CREATE_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"subject\": {\"type\": \"string\", \"description\": \"A brief title for the task\"}," +
            "    \"description\": {\"type\": \"string\", \"description\": \"What needs to be done\"}," +
            "    \"activeForm\": {\"type\": \"string\", \"description\": \"Present continuous form shown in spinner when in_progress\"}," +
            "    \"metadata\": {\"type\": \"object\", \"description\": \"Arbitrary metadata to attach to the task\"}" +
            "  }," +
            "  \"required\": [\"subject\", \"description\"]" +
            "}";

    public static ClaudeLikeTool createTaskCreateTool() {
        String detailedDescription = """
                Create a new task in the task list.

                Required parameters:
                - subject: A brief, actionable title for the task in imperative form (e.g., "Fix authentication bug", "Add user login feature")
                - description: Detailed description of what needs to be done

                Optional parameters:
                - activeForm: Present continuous form shown in spinner when task is in_progress (e.g., "Fixing authentication bug")
                - metadata: Arbitrary metadata to attach to the task

                Example:
                {
                  "subject": "Fix authentication bug in login flow",
                  "description": "Users are unable to log in due to a session validation error",
                  "activeForm": "Fixing authentication bug"
                }
                """;

        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskCreate", ToolCategory.PLANNING, detailedDescription, TASK_CREATE_INPUT_SCHEMA, null, false),
                (json, ctx) -> {
                    // TaskCreate 需要用户确认（因为会创建新任务）
                    return null; // 由 PermissionManager 检查
                },
                (input) -> {
                    if (!input.has("subject") || input.get("subject").asText("").isBlank()) {
                        return "subject is required and cannot be empty";
                    }
                    if (!input.has("description") || input.get("description").asText("").isBlank()) {
                        return "description is required and cannot be empty";
                    }
                    return null;
                });
    }

    public static TaskCreateParseResult parseTaskCreateInput(Map<String, Object> input) {
        String subject = (String) input.get("subject");
        if (subject == null || subject.isBlank()) {
            subject = (String) input.get("name");
        }
        if (subject == null || subject.isBlank()) {
            String prompt = (String) input.get("prompt");
            if (prompt != null && !prompt.isBlank()) {
                subject = prompt.replaceAll("[,.!?.:，。！？：;]", " ").split("\\s+")[0];
                if (subject.length() > 30) {
                    subject = subject.substring(0, 30) + "...";
                }
            }
        }

        String description = (String) input.get("description");
        if (description == null || description.isBlank()) {
            description = (String) input.get("task_instruction");
        }
        if (description == null || description.isBlank()) {
            description = (String) input.get("input");
        }
        if (description == null || description.isBlank()) {
            description = (String) input.get("prompt");
        }

        if (subject == null || subject.isBlank()) {
            return new TaskCreateParseResult.ParseError(
                    contractError("TaskCreate", "TASK_CREATE_SUBJECT_REQUIRED", "subject (or name) is required", input, List.of("subject", "description")));
        }
        if (description == null || description.isBlank()) {
            return new TaskCreateParseResult.ParseError(
                    contractError("TaskCreate", "TASK_CREATE_DESCRIPTION_REQUIRED", "description (or task_instruction or input or prompt) is required", input, List.of("subject", "description")));
        }

        String activeForm = (String) input.get("activeForm");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) input.get("metadata");
        return new TaskCreateParseResult.Parsed(subject, description, activeForm, metadata);
    }

    /**
     * Shadow 模式：禁止创建真实子任务（对齐 v1 不注入伪成功）。
     */
    public static LocalToolResult taskCreateShadowBlocked(Map<String, Object> input) {
        return contractError(
                "TaskCreate",
                "TASK_CREATE_SHADOW_MODE",
                "Multi-agent is in shadow mode: do not spawn subtasks. Answer from the current context only.",
                input,
                List.of("subject", "description"));
    }

    /**
     * 将 subagent_run 终态镜像到 Task 列表，便于 TaskList 与 runId 对齐。
     */
    public static void mirrorSubagentRunCompleted(String runId, String subject, String description, String resultPreview) {
        Task task = new Task(runId, subject, description);
        task.status = TaskStatus.COMPLETED;
        task.completedAt = System.currentTimeMillis();
        task.metadata.put("subagentRun", true);
        if (resultPreview != null && !resultPreview.isBlank()) {
            String p = resultPreview.length() > 4000 ? resultPreview.substring(0, 4000) + "…" : resultPreview;
            task.metadata.put("outputPreview", p);
        }
        tasks.put(runId, task);
    }

    public static LocalToolResult taskCreateSpawnRejected(Map<String, Object> input, String message) {
        return contractError("TaskCreate", "TASK_CREATE_SPAWN_REJECTED",
                message != null ? message : "spawn rejected", input, List.of("subject", "description"));
    }

    public static LocalToolResult taskCreateSuccessWithRunId(String runId, String subject) {
        Map<String, Object> output = new LinkedHashMap<>();
        Map<String, Object> taskInfo = new LinkedHashMap<>();
        taskInfo.put("id", runId);
        taskInfo.put("subject", subject);
        output.put("task", taskInfo);
        return contractSuccess("Subagent run completed: runId=" + runId + ", subject=" + subject, "TaskCreate", output);
    }

    public static LocalToolResult executeTaskCreate(Map<String, Object> input) {
        try {
            return switch (parseTaskCreateInput(input)) {
                case TaskCreateParseResult.ParseError e -> e.error();
                case TaskCreateParseResult.Parsed p -> executeInMemoryTaskCreate(input, p);
            };
        } catch (Exception e) {
            log.error("TaskCreate 执行失败", e);
            return contractError("TaskCreate", "TASK_CREATE_EXECUTION_ERROR", "Error: " + e.getMessage(), input, List.of("subject", "description"));
        }
    }

    private static LocalToolResult executeInMemoryTaskCreate(Map<String, Object> input, TaskCreateParseResult.Parsed p) {
        String taskId = String.valueOf(nextTaskId.getAndIncrement());
        Task task = new Task(taskId, p.subject(), p.description());
        task.activeForm = p.activeForm();
        if (p.metadata() != null) {
            task.metadata.putAll(p.metadata());
        }
        tasks.put(taskId, task);
        log.info("创建任务：{} - {}", taskId, p.subject());

        Map<String, Object> output = new LinkedHashMap<>();
        Map<String, Object> taskInfo = new LinkedHashMap<>();
        taskInfo.put("id", taskId);
        taskInfo.put("subject", p.subject());
        output.put("task", taskInfo);

        return contractSuccess("Task created successfully: taskId=" + taskId + ", subject=" + p.subject(), "TaskCreate", output);
    }

    // ===== TaskList =====

    private static final String TASK_LIST_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"status\": {\"type\": \"string\", \"enum\": [\"pending\", \"in_progress\", \"completed\", \"stopped\", \"failed\"], \"description\": \"Filter by status (optional)\"}" +
            "  }" +
            "}";

    public static ClaudeLikeTool createTaskListTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskList", ToolCategory.PLANNING, "List all tasks", TASK_LIST_INPUT_SCHEMA, null, true),
                null,
                null);
    }

    public static LocalToolResult executeTaskList(Map<String, Object> input) {
        try {
            String statusFilter = (String) input.get("status");

            List<Map<String, Object>> taskList = new ArrayList<>();
            for (Task task : tasks.values()) {
                if (statusFilter == null || task.status.name().toLowerCase().equals(statusFilter.toLowerCase())) {
                    taskList.add(task.toMap());
                }
            }

            // 按创建时间排序
            taskList.sort((a, b) -> Long.compare((Long) b.get("createdAt"), (Long) a.get("createdAt")));

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("tasks", taskList);

            log.info("列出任务：共 {} 个", taskList.size());
            StringBuilder listText = new StringBuilder("TaskList results (" + taskList.size() + " tasks):");
            for (Map<String, Object> t : taskList) {
                listText.append("\n- id=").append(t.get("id"))
                        .append(", status=").append(t.get("status"))
                        .append(", subject=").append(t.get("subject"));
            }

            return contractSuccess(listText.toString(), "TaskList", output);

        } catch (Exception e) {
            log.error("TaskList 执行失败", e);
            return contractError("TaskList", "TASK_LIST_EXECUTION_ERROR", "Error: " + e.getMessage(), input, List.of());
        }
    }

    // ===== TaskGet =====

    private static final String TASK_GET_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"taskId\": {\"type\": \"string\", \"description\": \"The ID of the task to get\"}" +
            "  }," +
            "  \"required\": [\"taskId\"]" +
            "}";

    public static ClaudeLikeTool createTaskGetTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskGet", ToolCategory.PLANNING, "Get details of a specific task", TASK_GET_INPUT_SCHEMA, null, true),
                null,
                (input) -> {
                    if (!input.has("taskId") || input.get("taskId").asText("").isBlank()) {
                        return "taskId is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskGet(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("taskId");
            if (taskId == null || taskId.isBlank()) return contractError("TaskGet", "TASK_ID_REQUIRED", "taskId is required", input, List.of("taskId"));
            Task task = tasks.get(taskId);
            if (task == null) return contractError("TaskGet", "TASK_NOT_FOUND", "Task not found", input, List.of("taskId"));

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task", task.toMap());

            return contractSuccess("Task details: taskId=" + task.id + ", status=" + task.status.name().toLowerCase() + ", subject=" + task.subject, "TaskGet", output);

        } catch (Exception e) {
            log.error("TaskGet 执行失败", e);
            return contractError("TaskGet", "TASK_GET_EXECUTION_ERROR", "Error: " + e.getMessage(), input, List.of("taskId"));
        }
    }

    // ===== TaskUpdate =====
    private static final String TASK_UPDATE_PROMPT = """
            Use this tool to update a task in the task list.

            ## When to Use This Tool

            **Mark tasks as resolved:**
            - When you have completed the work described in a task
            - When a task is no longer needed or has been superseded
            - IMPORTANT: Always mark your assigned tasks as resolved when you finish them
            - After resolving, call TaskList to find your next task

            - ONLY mark a task as completed when you have FULLY accomplished it
            - If you encounter errors, blockers, or cannot finish, keep the task as in_progress
            - When blocked, create a new task describing what needs to be resolved
            - Never mark a task as completed if:
              - Tests are failing
              - Implementation is partial
              - You encountered unresolved errors
              - You couldn't find necessary files or dependencies

            **Delete tasks:**
            - When a task is no longer relevant or was created in error
            - Setting status to `deleted` permanently removes the task

            **Update task details:**
            - When requirements change or become clearer
            - When establishing dependencies between tasks

            ## Fields You Can Update

            - **status**: The task status (see Status Workflow below)
            - **subject**: Change the task title (imperative form, e.g., "Run tests")
            - **description**: Change the task description
            - **activeForm**: Present continuous form shown in spinner when in_progress (e.g., "Running tests")
            - **owner**: Change the task owner (agent name)
            - **metadata**: Merge metadata keys into the task (set a key to null to delete it)
            - **addBlocks**: Mark tasks that cannot start until this one completes
            - **addBlockedBy**: Mark tasks that must complete before this one can start

            ## Status Workflow

            Status progresses: `pending` -> `in_progress` -> `completed`

            Use `deleted` to permanently remove a task.

            ## Staleness

            Make sure to read a task's latest state using `TaskGet` before updating it.

            ## Examples

            Mark task as in progress when starting work:
            {"taskId":"1","status":"in_progress"}

            Mark task as completed after finishing work:
            {"taskId":"1","status":"completed"}

            Delete a task:
            {"taskId":"1","status":"deleted"}

            Claim a task by setting owner:
            {"taskId":"1","owner":"my-name"}

            Set up task dependencies:
            {"taskId":"2","addBlockedBy":["1"]}
            """;

    private static final String TASK_UPDATE_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"taskId\": {\"type\": \"string\", \"description\": \"The ID of the task to update\"}," +
            "    \"subject\": {\"type\": \"string\", \"description\": \"New subject for the task\"}," +
            "    \"description\": {\"type\": \"string\", \"description\": \"New description for the task\"}," +
            "    \"activeForm\": {\"type\": \"string\", \"description\": \"Present continuous form shown in spinner when in_progress (e.g., Running tests)\"}," +
            "    \"status\": {\"type\": \"string\", \"enum\": [\"pending\", \"in_progress\", \"completed\", \"stopped\", \"failed\", \"deleted\"], \"description\": \"New status for the task\"}," +
            "    \"addBlocks\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"Task IDs that this task blocks\"}," +
            "    \"addBlockedBy\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"Task IDs that block this task\"}," +
            "    \"owner\": {\"type\": \"string\", \"description\": \"New owner for the task\"}," +
            "    \"metadata\": {\"type\": \"object\", \"description\": \"Metadata keys to merge into the task. Set a key to null to delete it.\"}" +
            "  }," +
            "  \"required\": [\"taskId\"]" +
            "}";

    public static ClaudeLikeTool createTaskUpdateTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskUpdate", ToolCategory.PLANNING, TASK_UPDATE_PROMPT, TASK_UPDATE_INPUT_SCHEMA, null, false),
                null,
                (input) -> {
                    if (!input.has("taskId") || input.get("taskId").asText("").isBlank()) {
                        return "taskId is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskUpdate(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("taskId");
            if (taskId == null || taskId.isBlank()) return contractError("TaskUpdate", "TASK_ID_REQUIRED", "taskId is required", input, List.of("taskId"));
            Task task = tasks.get(taskId);
            if (task == null) return contractError("TaskUpdate", "TASK_NOT_FOUND", "Task not found", input, List.of("taskId"));

            List<String> updates = new ArrayList<>();

            // Handle deletion as a special action
            String statusStr = (String) input.get("status");
            if (statusStr != null && "deleted".equalsIgnoreCase(statusStr)) {
                tasks.remove(taskId);
                updates.add("deleted");
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("taskId", taskId);
                output.put("updates", updates);
                return contractSuccess("Task deleted: taskId=" + taskId, "TaskUpdate", output);
            }

            // 更新状态
            if (statusStr != null) {
                TaskStatus newStatus = parseStatus(statusStr);
                if (task.status != newStatus) {
                    task.status = newStatus;
                    updates.add("status");
                    if (newStatus == TaskStatus.COMPLETED || newStatus == TaskStatus.STOPPED || newStatus == TaskStatus.FAILED) {
                        task.completedAt = System.currentTimeMillis();
                    } else {
                        task.completedAt = null;
                    }
                }
            }

            // 更新主题
            String subject = (String) input.get("subject");
            if (subject != null && !subject.isBlank()) {
                task.subject = subject;
                updates.add("subject");
            }

            // 更新描述
            String description = (String) input.get("description");
            if (description != null && !description.isBlank()) {
                task.description = description;
                updates.add("description");
            }

            // 更新 activeForm
            String activeForm = (String) input.get("activeForm");
            if (activeForm != null) {
                task.activeForm = activeForm;
                updates.add("activeForm");
            }

            // 更新 owner（允许传 null 清空）
            if (input.containsKey("owner")) {
                task.owner = (String) input.get("owner");
                updates.add("owner");
            }

            // 合并 metadata（值为 null 的键会被删除）
            if (input.containsKey("metadata")) {
                Object metadataObj = input.get("metadata");
                if (metadataObj instanceof Map<?, ?> metadataMap) {
                    for (Map.Entry<?, ?> entry : metadataMap.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        Object value = entry.getValue();
                        if (value == null) {
                            task.metadata.remove(key);
                        } else {
                            task.metadata.put(key, value);
                        }
                    }
                    updates.add("metadata");
                }
            }

            // 追加 blocks
            if (input.containsKey("addBlocks")) {
                Object addBlocksObj = input.get("addBlocks");
                if (addBlocksObj instanceof List<?> ids) {
                    boolean changed = false;
                    for (Object idObj : ids) {
                        String id = String.valueOf(idObj);
                        if (!id.isBlank() && !task.blocks.contains(id)) {
                            task.blocks.add(id);
                            changed = true;
                        }
                    }
                    if (changed) updates.add("blocks");
                }
            }

            // 追加 blockedBy
            if (input.containsKey("addBlockedBy")) {
                Object addBlockedByObj = input.get("addBlockedBy");
                if (addBlockedByObj instanceof List<?> ids) {
                    boolean changed = false;
                    for (Object idObj : ids) {
                        String id = String.valueOf(idObj);
                        if (!id.isBlank() && !task.blockedBy.contains(id)) {
                            task.blockedBy.add(id);
                            changed = true;
                        }
                    }
                    if (changed) updates.add("blockedBy");
                }
            }

            log.info("更新任务：{} - 更新项：{}", taskId, String.join(", ", updates));

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task", task.toMap());
            output.put("updates", updates);

            return contractSuccess("Task updated: taskId=" + taskId + ", fields=" + String.join(", ", updates), "TaskUpdate", output);

        } catch (Exception e) {
            log.error("TaskUpdate 执行失败", e);
            return contractError("TaskUpdate", "TASK_UPDATE_EXECUTION_ERROR", "Error: " + e.getMessage(), input, List.of("taskId"));
        }
    }

    // ===== TaskStop =====

    private static final String TASK_STOP_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"taskId\": {\"type\": \"string\", \"description\": \"The ID of the task to stop\"}" +
            "  }," +
            "  \"required\": [\"taskId\"]" +
            "}";

    public static ClaudeLikeTool createTaskStopTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskStop", ToolCategory.PLANNING, "Stop a running task", TASK_STOP_INPUT_SCHEMA, null, false),
                null,
                (input) -> {
                    if (!input.has("taskId") || input.get("taskId").asText("").isBlank()) {
                        return "taskId is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskStop(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("taskId");
            if (taskId == null || taskId.isBlank()) return contractError("TaskStop", "TASK_ID_REQUIRED", "taskId is required", input, List.of("taskId"));
            Task task = tasks.get(taskId);
            if (task == null) return contractError("TaskStop", "TASK_NOT_FOUND", "Task not found", input, List.of("taskId"));

            task.status = TaskStatus.STOPPED;
            task.completedAt = System.currentTimeMillis();

            log.info("停止任务：{}", taskId);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task", task.toMap());

            return contractSuccess("Task stopped: " + task.subject, "TaskStop", output);

        } catch (Exception e) {
            log.error("TaskStop 执行失败", e);
            return contractError("TaskStop", "TASK_STOP_EXECUTION_ERROR", "Error: " + e.getMessage(), input, List.of("taskId"));
        }
    }

    // ===== TaskOutput =====

    private static final String TASK_OUTPUT_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"taskId\": {\"type\": \"string\", \"description\": \"The ID of the task to get output for\"}" +
            "  }," +
            "  \"required\": [\"taskId\"]" +
            "}";

    public static ClaudeLikeTool createTaskOutputTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskOutput", ToolCategory.PLANNING, "Get the output/result of a task", TASK_OUTPUT_INPUT_SCHEMA, null, true),
                null,
                (input) -> {
                    if (!input.has("taskId") || input.get("taskId").asText("").isBlank()) {
                        return "taskId is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskOutput(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("taskId");
            if (taskId == null || taskId.isBlank()) return contractError("TaskOutput", "TASK_ID_REQUIRED", "taskId is required", input, List.of("taskId"));
            Task task = tasks.get(taskId);
            if (task == null) return contractError("TaskOutput", "TASK_NOT_FOUND", "Task not found", input, List.of("taskId"));

            // 获取任务输出（从 metadata 中获取）
            Object outputObj = task.metadata.get("output");
            String output = outputObj != null ? outputObj.toString() : "No output available";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("output", output);
            result.put("status", task.status.name().toLowerCase());

            return contractSuccess("Task output:\n" + output, "TaskOutput", result);

        } catch (Exception e) {
            log.error("TaskOutput 执行失败", e);
            return contractError("TaskOutput", "TASK_OUTPUT_EXECUTION_ERROR", "Error: " + e.getMessage(), input, List.of("taskId"));
        }
    }

    // ===== 辅助方法 =====

    private static TaskStatus parseStatus(String statusStr) {
        switch (statusStr.toLowerCase()) {
            case "pending": return TaskStatus.PENDING;
            case "in_progress":
            case "in-progress":
            case "active": return TaskStatus.IN_PROGRESS;
            case "completed":
            case "done":
            case "finished": return TaskStatus.COMPLETED;
            case "stopped":
            case "cancelled": return TaskStatus.STOPPED;
            case "failed":
            case "error": return TaskStatus.FAILED;
            default: return TaskStatus.PENDING;
        }
    }


    // ===== 测试用 =====

    public static Map<String, Task> getAllTasks() {
        return new HashMap<>(tasks);
    }

    public static void clearAllForTesting() {
        tasks.clear();
        nextTaskId.set(1);
    }
}
