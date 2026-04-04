package demo.k8s.agent.tools.local.planning;

import demo.k8s.agent.tools.local.LocalToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TodoWriteTool 单元测试
 */
@DisplayName("TodoWriteTool 测试")
class TodoWriteToolTest {

    @BeforeEach
    void setUp() {
        // 清除所有事项，确保测试独立
        TodoWriteTool.clearAllForTesting();
    }

    @AfterEach
    void tearDown() {
        TodoWriteTool.clearAllForTesting();
    }

    @Test
    @DisplayName("创建待办事项")
    void testCreateTodo() {
        Map<String, Object> input = Map.of(
                "action", "create",
                "content", "Test todo item"
        );

        LocalToolResult result = TodoWriteTool.execute(input);

        assertTrue(result.isSuccess(), "Should succeed: " + result.getError());
        String content = result.getContent();
        assertTrue(content.contains("Created todo item"));
        assertTrue(content.contains("Test todo item"));
        assertTrue(content.contains("ID: todo-"));
    }

    @Test
    @DisplayName("创建带负责人的待办事项")
    void testCreateTodoWithAssignee() {
        Map<String, Object> input = Map.of(
                "action", "create",
                "content", "Test with assignee",
                "assignee", "John"
        );

        LocalToolResult result = TodoWriteTool.execute(input);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("Assignee: John"));
    }

    @Test
    @DisplayName("更新待办事项状态")
    void testUpdateTodoStatus() {
        // 先创建
        Map<String, Object> createInput = Map.of(
                "action", "create",
                "content", "Test todo"
        );
        LocalToolResult createResult = TodoWriteTool.execute(createInput);
        String id = extractId(createResult.getContent());

        // 更新状态
        Map<String, Object> updateInput = Map.of(
                "action", "update",
                "id", id,
                "status", "in_progress"
        );

        LocalToolResult result = TodoWriteTool.execute(updateInput);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("status: PENDING -> IN_PROGRESS"));
    }

    @Test
    @DisplayName("完成待办事项")
    void testCompleteTodo() {
        // 先创建
        Map<String, Object> createInput = Map.of(
                "action", "create",
                "content", "Complete this task"
        );
        LocalToolResult createResult = TodoWriteTool.execute(createInput);
        String id = extractId(createResult.getContent());

        // 完成
        Map<String, Object> completeInput = Map.of(
                "action", "update",
                "id", id,
                "status", "completed"
        );

        LocalToolResult result = TodoWriteTool.execute(completeInput);

        assertTrue(result.isSuccess());

        // 验证事项已完成
        TodoWriteTool.TodoItem item = TodoWriteTool.getAllTodos().get(id);
        assertEquals(TodoWriteTool.TodoStatus.COMPLETED, item.status);
        assertNotNull(item.completedAt);
    }

    @Test
    @DisplayName("列出所有待办事项")
    void testListTodos() {
        // 创建多个事项
        TodoWriteTool.execute(Map.of("action", "create", "content", "Task 1"));
        TodoWriteTool.execute(Map.of("action", "create", "content", "Task 2"));
        TodoWriteTool.execute(Map.of("action", "create", "content", "Task 3"));

        Map<String, Object> listInput = Map.of("action", "list");
        LocalToolResult result = TodoWriteTool.execute(listInput);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("Task 1"));
        assertTrue(content.contains("Task 2"));
        assertTrue(content.contains("Task 3"));
        assertTrue(content.contains("Total: 3 items"));
    }

    @Test
    @DisplayName("按状态过滤待办事项")
    void testListTodosWithFilter() {
        // 创建事项
        Map<String, Object> createInput1 = Map.of("action", "create", "content", "Pending task");
        LocalToolResult createResult1 = TodoWriteTool.execute(createInput1);
        String id1 = extractId(createResult1.getContent());

        TodoWriteTool.execute(Map.of("action", "create", "content", "Another pending task"));

        // 完成第一个事项
        TodoWriteTool.execute(Map.of("action", "update", "id", id1, "status", "completed"));

        // 过滤已完成
        Map<String, Object> filterCompleted = Map.of(
                "action", "list",
                "filter", "completed"
        );
        LocalToolResult completedResult = TodoWriteTool.execute(filterCompleted);

        assertTrue(completedResult.isSuccess());
        String completedContent = completedResult.getContent();
        assertTrue(completedContent.contains("Pending task"));
        assertFalse(completedContent.contains("Another pending task"));

        // 过滤未完成
        Map<String, Object> filterPending = Map.of(
                "action", "list",
                "filter", "pending"
        );
        LocalToolResult pendingResult = TodoWriteTool.execute(filterPending);

        assertTrue(pendingResult.isSuccess());
        String pendingContent = pendingResult.getContent();
        assertFalse(pendingContent.contains("Pending task"));
        assertTrue(pendingContent.contains("Another pending task"));
    }

    @Test
    @DisplayName("删除待办事项")
    void testDeleteTodo() {
        // 先创建
        Map<String, Object> createInput = Map.of(
                "action", "create",
                "content", "To be deleted"
        );
        LocalToolResult createResult = TodoWriteTool.execute(createInput);
        String id = extractId(createResult.getContent());

        // 删除
        Map<String, Object> deleteInput = Map.of(
                "action", "delete",
                "id", id
        );

        LocalToolResult result = TodoWriteTool.execute(deleteInput);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Deleted todo item"));

        // 验证已删除
        assertFalse(TodoWriteTool.getAllTodos().containsKey(id));
    }

    @Test
    @DisplayName("删除不存在的事项")
    void testDeleteNonExistentTodo() {
        Map<String, Object> deleteInput = Map.of(
                "action", "delete",
                "id", "non-existent-id"
        );

        LocalToolResult result = TodoWriteTool.execute(deleteInput);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    @Test
    @DisplayName("清除已完成事项")
    void testClearCompleted() {
        // 创建并完成任务
        Map<String, Object> createInput1 = Map.of("action", "create", "content", "Completed task 1");
        LocalToolResult createResult1 = TodoWriteTool.execute(createInput1);
        String id1 = extractId(createResult1.getContent());

        Map<String, Object> createInput2 = Map.of("action", "create", "content", "Pending task");

        TodoWriteTool.execute(createInput2);
        TodoWriteTool.execute(Map.of("action", "update", "id", id1, "status", "completed"));

        // 清除已完成
        Map<String, Object> clearInput = Map.of("action", "clear");
        LocalToolResult result = TodoWriteTool.execute(clearInput);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Cleared 1 completed"));

        // 验证只剩未完成事项
        Map<String, TodoWriteTool.TodoItem> remaining = TodoWriteTool.getAllTodos();
        assertEquals(1, remaining.size());
    }

    @Test
    @DisplayName("更新事项内容")
    void testUpdateTodoContent() {
        // 先创建
        Map<String, Object> createInput = Map.of(
                "action", "create",
                "content", "Original content"
        );
        LocalToolResult createResult = TodoWriteTool.execute(createInput);
        String id = extractId(createResult.getContent());

        // 更新内容
        Map<String, Object> updateInput = Map.of(
                "action", "update",
                "id", id,
                "content", "Updated content"
        );

        LocalToolResult result = TodoWriteTool.execute(updateInput);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("content updated"));

        // 验证内容已更新
        TodoWriteTool.TodoItem item = TodoWriteTool.getAllTodos().get(id);
        assertEquals("Updated content", item.content);
    }

    @Test
    @DisplayName("缺少 action 参数")
    void testMissingAction() {
        Map<String, Object> input = Map.of("content", "Test");

        LocalToolResult result = TodoWriteTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("action is required"));
    }

    @Test
    @DisplayName("创建缺少 content 参数")
    void testCreateMissingContent() {
        Map<String, Object> input = Map.of("action", "create");

        LocalToolResult result = TodoWriteTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("content is required"));
    }

    @Test
    @DisplayName("更新不存在的事项")
    void testUpdateNonExistentTodo() {
        Map<String, Object> updateInput = Map.of(
                "action", "update",
                "id", "non-existent-id",
                "status", "completed"
        );

        LocalToolResult result = TodoWriteTool.execute(updateInput);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    /**
     * 从输出中提取 ID
     */
    private String extractId(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("ID:")) {
                return line.trim().substring(3).trim();
            }
        }
        return null;
    }
}
