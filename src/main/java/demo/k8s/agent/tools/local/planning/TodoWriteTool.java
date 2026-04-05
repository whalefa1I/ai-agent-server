package demo.k8s.agent.tools.local.planning;

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
 * 待办事项管理工具。
 * <p>
 * 支持：
 * - 创建待办事项
 * - 更新事项状态
 * - 删除事项
 * - 获取事项列表
 * - 清除完成事项
 */
public class TodoWriteTool {

    private static final Logger log = LoggerFactory.getLogger(TodoWriteTool.class);

    /**
     * 待办事项状态
     */
    public enum TodoStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED
    }

    /**
     * 待办事项
     */
    public static class TodoItem {
        public final String id;
        public String content;
        public TodoStatus status;
        public final long createdAt;
        public Long completedAt;
        public String assignee;

        public TodoItem(String id, String content) {
            this.id = id;
            this.content = content;
            this.status = TodoStatus.PENDING;
            this.createdAt = System.currentTimeMillis();
        }
    }

    /**
     * 全局事项存储（会话级别）
     */
    private static final Map<String, TodoItem> todos = new ConcurrentHashMap<>();

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"action\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Action: 'create', 'update', 'delete', 'list', 'clear'\"" +
            "    }," +
            "    \"id\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Todo item ID (required for update/delete)\"" +
            "    }," +
            "    \"content\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Todo content (required for create/update)\"" +
            "    }," +
            "    \"status\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Status: 'pending', 'in_progress', 'completed' (for update)\"" +
            "    }," +
            "    \"assignee\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Assignee name (optional)\"" +
            "    }," +
            "    \"filter\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Filter by status (for list action)\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"action\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "todo_write",
                        ToolCategory.PLANNING,
                        "Manage todo items for task tracking. Actions: create, update, delete, list, clear. Track progress on complex tasks.",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        try {
            log.info("todo_write 收到输入：input={}", input);

            String action = (String) input.get("action");
            log.info("action={}", action);

            if (action == null || action.isEmpty()) {
                return LocalToolResult.error("action is required");
            }

            switch (action.toLowerCase()) {
                case "create":
                    return createTodo(input);
                case "update":
                    return updateTodo(input);
                case "delete":
                    return deleteTodo(input);
                case "list":
                    return listTodos(input);
                case "clear":
                    return clearCompleted();
                default:
                    return LocalToolResult.error("Unknown action: " + action +
                            ". Valid actions: create, update, delete, list, clear");
            }

        } catch (Exception e) {
            log.error("TodoWrite execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 创建待办事项
     */
    private static LocalToolResult createTodo(Map<String, Object> input) {
        String content = (String) input.get("content");
        if (content == null || content.isEmpty()) {
            return LocalToolResult.error("content is required for create action");
        }

        String id = generateId();
        TodoItem item = new TodoItem(id, content);
        item.assignee = (String) input.get("assignee");

        todos.put(id, item);

        // 尝试创建 todo artifact（用于前端显示），失败不影响工具执行
        String artifactId = null;
        try {
            artifactId = TodoArtifactHelper.createTodoArtifact(content, id, item.assignee);
        } catch (Exception e) {
            log.warn("创建 todo artifact 失败（不影响工具执行）: {}", e.getMessage());
        }

        StringBuilder output = new StringBuilder();
        output.append("Created todo item:\n");
        output.append("  ID: ").append(id).append("\n");
        output.append("  Content: ").append(content).append("\n");
        if (item.assignee != null) {
            output.append("  Assignee: ").append(item.assignee).append("\n");
        }
        output.append("  Status: pending\n");
        if (artifactId != null) {
            output.append("  Artifact ID: ").append(artifactId).append("\n");
        }

        return LocalToolResult.builder()
                .success(true)
                .content(output.toString())
                .executionLocation("local")
                .metadata(new com.fasterxml.jackson.databind.ObjectMapper()
                        .valueToTree(Map.of("id", id, "status", "pending", "artifactId", artifactId)))
                .build();
    }

    /**
     * 更新待办事项
     */
    private static LocalToolResult updateTodo(Map<String, Object> input) {
        String id = (String) input.get("id");
        if (id == null || id.isEmpty()) {
            return LocalToolResult.error("id is required for update action");
        }

        TodoItem item = todos.get(id);
        if (item == null) {
            return LocalToolResult.error("Todo item not found: " + id);
        }

        String content = (String) input.get("content");
        String statusStr = (String) input.get("status");
        String assignee = (String) input.get("assignee");

        StringBuilder changes = new StringBuilder();

        if (content != null && !content.isEmpty()) {
            item.content = content;
            changes.append("content updated. ");
        }

        if (statusStr != null && !statusStr.isEmpty()) {
            TodoStatus oldStatus = item.status;
            item.status = parseStatus(statusStr);
            changes.append("status: ").append(oldStatus).append(" -> ").append(item.status).append(". ");

            if (item.status == TodoStatus.COMPLETED && item.completedAt == null) {
                item.completedAt = System.currentTimeMillis();
            } else if (item.status != TodoStatus.COMPLETED) {
                item.completedAt = null;
            }

            // 更新 todo artifact 状态
            TodoArtifactHelper.updateTodoArtifact(id, item.status.name().toLowerCase(), item.content);
        }

        if (assignee != null) {
            item.assignee = assignee;
            changes.append("assignee: ").append(assignee);
        }

        StringBuilder output = new StringBuilder();
        output.append("Updated todo item: ").append(id).append("\n");
        output.append("  Changes: ").append(changes).append("\n");
        output.append("  Current status: ").append(item.status).append("\n");

        return LocalToolResult.builder()
                .success(true)
                .content(output.toString())
                .executionLocation("local")
                .build();
    }

    /**
     * 删除待办事项
     */
    private static LocalToolResult deleteTodo(Map<String, Object> input) {
        String id = (String) input.get("id");
        if (id == null || id.isEmpty()) {
            return LocalToolResult.error("id is required for delete action");
        }

        TodoItem removed = todos.remove(id);
        if (removed == null) {
            return LocalToolResult.error("Todo item not found: " + id);
        }

        // 删除 todo artifact
        boolean artifactDeleted = TodoArtifactHelper.deleteTodoArtifact(id);

        return LocalToolResult.success("Deleted todo item: " + id + "\n  Content: " + removed.content +
            (artifactDeleted ? "\n  Artifact also deleted." : ""));
    }

    /**
     * 列出待办事项
     */
    private static LocalToolResult listTodos(Map<String, Object> input) {
        String filter = (String) input.get("filter");

        StringBuilder output = new StringBuilder();
        output.append("Todo Items:\n");
        output.append("===========\n\n");

        int count = 0;
        int completed = 0;

        for (TodoItem item : todos.values()) {
            // 应用过滤器
            if (filter != null && !filter.isEmpty()) {
                TodoStatus filterStatus = parseStatus(filter);
                if (item.status != filterStatus) {
                    continue;
                }
            }

            count++;
            if (item.status == TodoStatus.COMPLETED) {
                completed++;
            }

            output.append("[").append(item.status.name().charAt(0)).append("] ")
                    .append(item.id).append(": ").append(item.content).append("\n");
            if (item.assignee != null) {
                output.append("    Assignee: ").append(item.assignee).append("\n");
            }
        }

        if (count == 0) {
            output.append("(No items");
            if (filter != null && !filter.isEmpty()) {
                output.append(" matching filter: ").append(filter);
            }
            output.append(")\n");
        }

        output.append("\n===========\n");
        output.append("Total: ").append(count).append(" items");
        if (count > 0) {
            output.append(" (").append(completed).append(" completed, ")
                    .append(count - completed).append(" remaining)");
        }

        return LocalToolResult.builder()
                .success(true)
                .content(output.toString())
                .executionLocation("local")
                .metadata(new com.fasterxml.jackson.databind.ObjectMapper()
                        .valueToTree(Map.of("total", count, "completed", completed)))
                .build();
    }

    /**
     * 清除已完成事项
     */
    private static LocalToolResult clearCompleted() {
        int beforeCount = todos.size();

        todos.entrySet().removeIf(entry -> entry.getValue().status == TodoStatus.COMPLETED);

        int afterCount = todos.size();
        int removed = beforeCount - afterCount;

        return LocalToolResult.success("Cleared " + removed + " completed todo item(s).\n" +
                "Remaining items: " + afterCount);
    }

    /**
     * 生成唯一 ID
     */
    private static String generateId() {
        return "todo-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 解析状态字符串
     */
    private static TodoStatus parseStatus(String statusStr) {
        switch (statusStr.toLowerCase()) {
            case "pending":
                return TodoStatus.PENDING;
            case "in_progress":
            case "in-progress":
            case "active":
                return TodoStatus.IN_PROGRESS;
            case "completed":
            case "done":
            case "finished":
                return TodoStatus.COMPLETED;
            default:
                return TodoStatus.PENDING;
        }
    }

    /**
     * 获取所有事项（用于测试）
     */
    public static Map<String, TodoItem> getAllTodos() {
        return new HashMap<>(todos);
    }

    /**
     * 清除所有事项（用于测试）
     */
    public static void clearAllForTesting() {
        todos.clear();
    }
}
