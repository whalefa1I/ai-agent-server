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

/**
 * Task 工具集 - 任务管理（与 claude-code 的 Task* 工具对齐）
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
        public Map<String, Object> metadata;
        public final long createdAt;
        public Long completedAt;

        public Task(String id, String subject, String description) {
            this.id = id;
            this.subject = subject;
            this.description = description;
            this.status = TaskStatus.PENDING;
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

    // ===== TaskCreate =====

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
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskCreate", ToolCategory.PLANNING, "Create a new task in the task list", TASK_CREATE_INPUT_SCHEMA, null, false),
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

    public static LocalToolResult executeTaskCreate(Map<String, Object> input) {
        try {
            String subject = (String) input.get("subject");
            String description = (String) input.get("description");
            String activeForm = (String) input.get("activeForm");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) input.get("metadata");

            String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);
            Task task = new Task(taskId, subject, description);
            task.activeForm = activeForm;
            if (metadata != null) {
                task.metadata.putAll(metadata);
            }

            tasks.put(taskId, task);

            log.info("创建任务：{} - {}", taskId, subject);

            Map<String, Object> output = new LinkedHashMap<>();
            Map<String, Object> taskInfo = new LinkedHashMap<>();
            taskInfo.put("id", taskId);
            taskInfo.put("subject", subject);
            output.put("task", taskInfo);

            return LocalToolResult.builder()
                    .success(true)
                    .content("Task created successfully: " + subject)
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("TaskCreate 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
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

            return LocalToolResult.builder()
                    .success(true)
                    .content("Total " + taskList.size() + " tasks")
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("TaskList 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    // ===== TaskGet =====

    private static final String TASK_GET_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"task_id\": {\"type\": \"string\", \"description\": \"The ID of the task to get\"}" +
            "  }," +
            "  \"required\": [\"task_id\"]" +
            "}";

    public static ClaudeLikeTool createTaskGetTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskGet", ToolCategory.PLANNING, "Get details of a specific task", TASK_GET_INPUT_SCHEMA, null, true),
                null,
                (input) -> {
                    if (!input.has("task_id") || input.get("task_id").asText("").isBlank()) {
                        return "task_id is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskGet(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("task_id");

            Task task = tasks.get(taskId);
            if (task == null) {
                return LocalToolResult.error("Task not found: " + taskId);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task", task.toMap());

            return LocalToolResult.builder()
                    .success(true)
                    .content("Task: " + task.subject)
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("TaskGet 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    // ===== TaskUpdate =====

    private static final String TASK_UPDATE_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"task_id\": {\"type\": \"string\", \"description\": \"The ID of the task to update\"}," +
            "    \"status\": {\"type\": \"string\", \"enum\": [\"pending\", \"in_progress\", \"completed\", \"stopped\", \"failed\"], \"description\": \"New status\"}," +
            "    \"subject\": {\"type\": \"string\", \"description\": \"Updated subject\"}," +
            "    \"description\": {\"type\": \"string\", \"description\": \"Updated description\"}," +
            "    \"activeForm\": {\"type\": \"string\", \"description\": \"Updated activeForm\"}" +
            "  }," +
            "  \"required\": [\"task_id\"]" +
            "}";

    public static ClaudeLikeTool createTaskUpdateTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskUpdate", ToolCategory.PLANNING, "Update a task's status or details", TASK_UPDATE_INPUT_SCHEMA, null, false),
                null,
                (input) -> {
                    if (!input.has("task_id") || input.get("task_id").asText("").isBlank()) {
                        return "task_id is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskUpdate(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("task_id");

            Task task = tasks.get(taskId);
            if (task == null) {
                return LocalToolResult.error("Task not found: " + taskId);
            }

            List<String> updates = new ArrayList<>();

            // 更新状态
            String statusStr = (String) input.get("status");
            if (statusStr != null) {
                TaskStatus newStatus = parseStatus(statusStr);
                if (task.status != newStatus) {
                    task.status = newStatus;
                    updates.add("status: " + newStatus.name().toLowerCase());
                    if (newStatus == TaskStatus.COMPLETED || newStatus == TaskStatus.STOPPED || newStatus == TaskStatus.FAILED) {
                        task.completedAt = System.currentTimeMillis();
                    }
                }
            }

            // 更新主题
            String subject = (String) input.get("subject");
            if (subject != null && !subject.isBlank()) {
                task.subject = subject;
                updates.add("subject updated");
            }

            // 更新描述
            String description = (String) input.get("description");
            if (description != null && !description.isBlank()) {
                task.description = description;
                updates.add("description updated");
            }

            // 更新 activeForm
            String activeForm = (String) input.get("activeForm");
            if (activeForm != null) {
                task.activeForm = activeForm;
                updates.add("activeForm updated");
            }

            log.info("更新任务：{} - 更新项：{}", taskId, String.join(", ", updates));

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task", task.toMap());
            output.put("updates", updates);

            return LocalToolResult.builder()
                    .success(true)
                    .content("Task updated: " + String.join(", ", updates))
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("TaskUpdate 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    // ===== TaskStop =====

    private static final String TASK_STOP_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"task_id\": {\"type\": \"string\", \"description\": \"The ID of the task to stop\"}" +
            "  }," +
            "  \"required\": [\"task_id\"]" +
            "}";

    public static ClaudeLikeTool createTaskStopTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskStop", ToolCategory.PLANNING, "Stop a running task", TASK_STOP_INPUT_SCHEMA, null, false),
                null,
                (input) -> {
                    if (!input.has("task_id") || input.get("task_id").asText("").isBlank()) {
                        return "task_id is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskStop(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("task_id");

            Task task = tasks.get(taskId);
            if (task == null) {
                return LocalToolResult.error("Task not found: " + taskId);
            }

            task.status = TaskStatus.STOPPED;
            task.completedAt = System.currentTimeMillis();

            log.info("停止任务：{}", taskId);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task", task.toMap());

            return LocalToolResult.builder()
                    .success(true)
                    .content("Task stopped: " + task.subject)
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("TaskStop 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    // ===== TaskOutput =====

    private static final String TASK_OUTPUT_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"task_id\": {\"type\": \"string\", \"description\": \"The ID of the task to get output for\"}" +
            "  }," +
            "  \"required\": [\"task_id\"]" +
            "}";

    public static ClaudeLikeTool createTaskOutputTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskOutput", ToolCategory.PLANNING, "Get the output/result of a task", TASK_OUTPUT_INPUT_SCHEMA, null, true),
                null,
                (input) -> {
                    if (!input.has("task_id") || input.get("task_id").asText("").isBlank()) {
                        return "task_id is required";
                    }
                    return null;
                });
    }

    public static LocalToolResult executeTaskOutput(Map<String, Object> input) {
        try {
            String taskId = (String) input.get("task_id");

            Task task = tasks.get(taskId);
            if (task == null) {
                return LocalToolResult.error("Task not found: " + taskId);
            }

            // 获取任务输出（这里从 metadata 中获取）
            Object outputObj = task.metadata.get("output");
            String output = outputObj != null ? outputObj.toString() : "No output available";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("task_id", taskId);
            result.put("output", output);
            result.put("status", task.status.name().toLowerCase());

            return LocalToolResult.builder()
                    .success(true)
                    .content("Task output:\n" + output)
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(result))
                    .build();

        } catch (Exception e) {
            log.error("TaskOutput 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
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
    }
}
