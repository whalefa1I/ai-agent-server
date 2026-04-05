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
 * 与 claude-code 的 TodoWriteTool 协议保持一致 - 使用声明式 todos 列表。
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
        public String activeForm;

        public TodoItem(String id, String content, String activeForm) {
            this.id = id;
            this.content = content;
            this.status = TodoStatus.PENDING;
            this.activeForm = activeForm != null ? activeForm : content;
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
            "    \"todos\": {" +
            "      \"type\": \"array\"," +
            "      \"items\": {" +
            "        \"type\": \"object\"," +
            "        \"properties\": {" +
            "          \"content\": {\"type\": \"string\"}," +
            "          \"status\": {\"type\": \"string\", \"enum\": [\"pending\", \"in_progress\", \"completed\"]}," +
            "          \"activeForm\": {\"type\": \"string\"}" +
            "        }," +
            "        \"required\": [\"content\", \"status\", \"activeForm\"]" +
            "      }," +
            "      \"description\": \"The updated todo list\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"todos\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "todo_write",
                        ToolCategory.PLANNING,
                        "Manage todo items for task tracking. Pass the complete updated todo list.",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        log.info("=== todo_write 开始执行 ===");
        log.info("输入参数：{}", input);

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> todosInput = (List<Map<String, Object>>) input.get("todos");

            if (todosInput == null) {
                return LocalToolResult.error("todos is required");
            }

            // 保存旧的 todos
            List<Map<String, Object>> oldTodos = new ArrayList<>();
            for (TodoItem item : todos.values()) {
                Map<String, Object> todoMap = new LinkedHashMap<>();
                todoMap.put("content", item.content);
                todoMap.put("status", item.status.name().toLowerCase());
                todoMap.put("activeForm", item.activeForm);
                todoMap.put("id", item.id);
                oldTodos.add(todoMap);
            }

            // 清除旧的 todos
            todos.clear();

            // 添加新的 todos
            List<Map<String, Object>> newTodos = new ArrayList<>();
            for (Map<String, Object> todoData : todosInput) {
                String content = (String) todoData.get("content");
                String statusStr = (String) todoData.get("status");
                String activeForm = (String) todoData.get("activeForm");

                if (content == null || content.isEmpty()) {
                    return LocalToolResult.error("Each todo must have non-empty 'content'");
                }
                if (statusStr == null) {
                    return LocalToolResult.error("Each todo must have 'status'");
                }

                String id = "todo-" + UUID.randomUUID().toString().substring(0, 8);
                TodoItem item = new TodoItem(id, content, activeForm);
                item.status = parseStatus(statusStr);
                if (item.status == TodoStatus.COMPLETED) {
                    item.completedAt = System.currentTimeMillis();
                }

                todos.put(id, item);

                Map<String, Object> todoMap = new LinkedHashMap<>();
                todoMap.put("content", content);
                todoMap.put("status", statusStr);
                todoMap.put("activeForm", activeForm != null ? activeForm : content);
                todoMap.put("id", id);
                newTodos.add(todoMap);
            }

            // 构建输出
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("oldTodos", oldTodos);
            output.put("newTodos", newTodos);

            return LocalToolResult.builder()
                    .success(true)
                    .content("Todos have been modified successfully. Ensure that you continue to use the todo list to track your progress.")
                    .executionLocation("local")
                    .metadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("TodoWrite execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
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
