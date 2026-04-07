package demo.k8s.agent.tools.local.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.tools.local.planning.TaskTools.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Task 工具集成测试
 * 模拟自然语言场景验证 Task 功能端到端执行
 */
@DisplayName("Task 工具集成测试")
class TaskIntegrationTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 清空任务存储
        TaskTools.clearAllForTesting();
    }

    // ==================== 基础流程测试 ====================

    @Test
    @DisplayName("基础流程：创建任务")
    void basicFlow_createTask() {
        // 执行 TaskCreate
        Map<String, Object> input = Map.of(
                "subject", "实现用户认证功能",
                "description", "添加 JWT 认证支持"
        );

        LocalToolResult result = TaskTools.executeTaskCreate(input);

        // 验证
        assertTrue(result.isSuccess(), "TaskCreate 应该成功");
        assertTrue(result.getContent().contains("Task created successfully"));

        // 验证任务被存储
        JsonNode metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.has("task"));
        assertEquals("实现用户认证功能", metadata.get("task").get("subject").asText());
    }

    @Test
    @DisplayName("基础流程：列出任务")
    void basicFlow_listTasks() {
        // 先创建两个任务
        TaskTools.executeTaskCreate(Map.of(
                "subject", "任务 1",
                "description", "描述 1"
        ));
        TaskTools.executeTaskCreate(Map.of(
                "subject", "任务 2",
                "description", "描述 2"
        ));

        // 执行 TaskList
        LocalToolResult result = TaskTools.executeTaskList(Map.of());

        // 验证
        assertTrue(result.isSuccess());
        JsonNode metadata = result.getMetadata();
        assertTrue(metadata.has("tasks"));
        assertEquals(2, metadata.get("tasks").size());
    }

    @Test
    @DisplayName("基础流程：获取任务详情")
    void basicFlow_getTask() {
        // 创建任务
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "测试任务",
                "description", "测试描述"
        ));

        String taskId = extractTaskId(createResult.getMetadata());

        // 执行 TaskGet
        LocalToolResult getResult = TaskTools.executeTaskGet(Map.of("task_id", taskId));

        // 验证
        assertTrue(getResult.isSuccess());
        JsonNode metadata = getResult.getMetadata();
        assertTrue(metadata.has("task"));
        assertEquals("测试任务", metadata.get("task").get("subject").asText());
        assertEquals("测试描述", metadata.get("task").get("description").asText());
    }

    @Test
    @DisplayName("基础流程：更新任务状态")
    void basicFlow_updateTaskStatus() {
        // 创建任务
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "状态转换测试",
                "description", "测试 PENDING -> IN_PROGRESS -> COMPLETED"
        ));

        String taskId = extractTaskId(createResult.getMetadata());

        // 更新为 IN_PROGRESS
        LocalToolResult progressResult = TaskTools.executeTaskUpdate(Map.of(
                "task_id", taskId,
                "status", "in_progress"
        ));

        assertTrue(progressResult.isSuccess());
        assertEquals("in_progress", getTaskStatus(taskId));

        // 更新为 COMPLETED
        LocalToolResult completeResult = TaskTools.executeTaskUpdate(Map.of(
                "task_id", taskId,
                "status", "completed"
        ));

        assertTrue(completeResult.isSuccess());
        assertEquals("completed", getTaskStatus(taskId));
        assertNotNull(getTaskCompletedAt(taskId));
    }

    @Test
    @DisplayName("基础流程：获取任务输出")
    void basicFlow_getTaskOutput() {
        // 创建任务并添加输出
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "带输出的任务",
                "description", "测试输出获取",
                "metadata", Map.of("output", "任务执行结果：成功完成所有步骤")
        ));

        String taskId = extractTaskId(createResult.getMetadata());

        // 执行 TaskOutput
        LocalToolResult outputResult = TaskTools.executeTaskOutput(Map.of("task_id", taskId));

        // 验证
        assertTrue(outputResult.isSuccess());
        assertTrue(outputResult.getContent().contains("任务执行结果：成功完成所有步骤"));
    }

    // ==================== 完整生命周期测试 ====================

    @Test
    @DisplayName("完整生命周期：创建→列表→详情→更新→完成")
    void fullLifecycle_createToListToUpdateToComplete() {
        // 1. 创建任务
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "完整生命周期测试",
                "description", "验证从创建到完成的完整流程",
                "activeForm", "正在执行完整生命周期测试"
        ));

        String taskId = extractTaskId(createResult.getMetadata());
        assertNotNull(taskId);

        // 2. 列出任务（验证任务存在）
        LocalToolResult listResult = TaskTools.executeTaskList(Map.of());
        assertTrue(listResult.isSuccess());
        assertEquals(1, listResult.getMetadata().get("tasks").size());

        // 3. 获取详情（验证初始状态）
        LocalToolResult getInitialResult = TaskTools.executeTaskGet(Map.of("task_id", taskId));
        assertEquals("pending", getInitialResult.getMetadata().get("task").get("status").asText());

        // 4. 更新状态为 IN_PROGRESS
        LocalToolResult updateProgressResult = TaskTools.executeTaskUpdate(Map.of(
                "task_id", taskId,
                "status", "in_progress"
        ));
        assertTrue(updateProgressResult.isSuccess());
        assertEquals("in_progress", getTaskStatus(taskId));

        // 5. 更新状态为 COMPLETED
        LocalToolResult updateCompleteResult = TaskTools.executeTaskUpdate(Map.of(
                "task_id", taskId,
                "status", "completed"
        ));
        assertTrue(updateCompleteResult.isSuccess());
        assertEquals("completed", getTaskStatus(taskId));

        // 6. 获取最终状态
        LocalToolResult getFinalResult = TaskTools.executeTaskGet(Map.of("task_id", taskId));
        JsonNode finalTask = getFinalResult.getMetadata().get("task");
        assertEquals("completed", finalTask.get("status").asText());
        assertNotNull(finalTask.get("completedAt").asLong());
    }

    // ==================== 自然语言场景测试 ====================

    @Test
    @DisplayName("自然语言场景：用户请求创建任务")
    void naturalLanguageScenario_userRequestsTaskCreation() throws Exception {
        // 模拟用户请求："帮我创建一个任务，实现用户登录功能"
        String userRequest = "帮我创建一个任务，实现用户登录功能";

        // Mock LLM 响应（识别请求并调用 TaskCreate）
        AssistantMessage modelResponse = new AssistantMessage(
                "我来为您创建这个任务。\n" +
                "<tool_use>\n" +
                "<name>TaskCreate</name>\n" +
                "<input>{\"subject\": \"实现用户登录功能\", \"description\": \"添加用户登录功能，包括密码验证和会话管理\"}</input>\n" +
                "</tool_use>");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(modelResponse);

        // 执行对话
        Prompt prompt = new Prompt(List.of(
                new UserMessage(userRequest)
        ));
        ChatResponse response = chatModel.call(prompt);

        // 验证模型被调用
        verify(chatModel, times(1)).call(any(Prompt.class));

        // 实际执行工具调用
        LocalToolResult toolResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "实现用户登录功能",
                "description", "添加用户登录功能，包括密码验证和会话管理"
        ));

        assertTrue(toolResult.isSuccess());
        assertTrue(toolResult.getContent().contains("Task created successfully"));
    }

    @Test
    @DisplayName("自然语言场景：用户询问任务进度")
    void naturalLanguageScenario_userAsksTaskProgress() {
        // 先创建任务
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "代码审查",
                "description", "审查 UserService 模块"
        ));
        String taskId = extractTaskId(createResult.getMetadata());

        // 更新为进行中
        TaskTools.executeTaskUpdate(Map.of("task_id", taskId, "status", "in_progress"));

        // 模拟用户询问："我的任务进行得怎么样了？"
        String userQuery = "我的任务进行得怎么样了？";

        // 实际执行 TaskList 工具调用
        LocalToolResult listResult = TaskTools.executeTaskList(Map.of());

        // 验证
        assertTrue(listResult.isSuccess());
        assertEquals(1, listResult.getMetadata().get("tasks").size());
        assertEquals("in_progress", listResult.getMetadata().get("tasks").get(0)
                .get("status").asText());
    }

    @Test
    @DisplayName("自然语言场景：用户要求查看任务详情")
    void naturalLanguageScenario_userRequestsTaskDetails() {
        // 创建任务
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "数据库迁移",
                "description", "将用户表迁移到新的 schema",
                "metadata", Map.of(
                        "files", List.of("users.sql", "roles.sql"),
                        "priority", "high"
                )
        ));
        String taskId = extractTaskId(createResult.getMetadata());

        // 模拟用户请求："查看数据库迁移任务的详细信息"
        // 实际执行 TaskGet
        LocalToolResult getResult = TaskTools.executeTaskGet(Map.of("task_id", taskId));

        // 验证
        assertTrue(getResult.isSuccess());
        JsonNode task = getResult.getMetadata().get("task");
        assertEquals("数据库迁移", task.get("subject").asText());
        assertEquals("high", task.get("metadata").get("priority").asText());
        assertTrue(task.get("metadata").get("files").isArray());
    }

    @Test
    @DisplayName("自然语言场景：用户完成任务")
    void naturalLanguageScenario_userCompletesTask() {
        // 创建并启动任务
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "编写单元测试",
                "description", "为 AuthService 编写测试用例"
        ));
        String taskId = extractTaskId(createResult.getMetadata());
        TaskTools.executeTaskUpdate(Map.of("task_id", taskId, "status", "in_progress"));

        // 模拟用户完成工作后标记任务完成："我已经完成了单元测试的编写"
        // 实际执行 TaskUpdate
        LocalToolResult completeResult = TaskTools.executeTaskUpdate(Map.of(
                "task_id", taskId,
                "status", "completed",
                "metadata", Map.of("output", "完成 15 个测试用例，覆盖率 92%")
        ));

        // 验证
        assertTrue(completeResult.isSuccess());
        assertEquals("completed", getTaskStatus(taskId));
        assertNotNull(getTaskCompletedAt(taskId));
    }

    // ==================== 多任务场景测试 ====================

    @Test
    @DisplayName("多任务场景：创建多个任务并按状态过滤")
    void multiTaskScenario_createAndFilterByStatus() {
        // 创建多个不同状态的任务
        TaskTools.executeTaskCreate(Map.of("subject", "任务 A", "description", "待处理")); // PENDING
        TaskTools.executeTaskCreate(Map.of("subject", "任务 B", "description", "进行中"));
        TaskTools.executeTaskUpdate(Map.of("name", "任务 B", "status", "in_progress"));

        TaskTools.executeTaskCreate(Map.of("subject", "任务 C", "description", "已完成"));
        TaskTools.executeTaskUpdate(Map.of("name", "任务 C", "status", "completed"));

        // 验证总任务数
        LocalToolResult allResult = TaskTools.executeTaskList(Map.of());
        assertEquals(3, allResult.getMetadata().get("tasks").size(), "应该共有 3 个任务");

        // 过滤 PENDING 任务（任务 A 仍然是 PENDING）
        LocalToolResult pendingResult = TaskTools.executeTaskList(Map.of("status", "pending"));
        assertEquals(1, pendingResult.getMetadata().get("tasks").size(), "应该有 1 个 PENDING 任务");

        // 过滤 IN_PROGRESS 任务
        LocalToolResult progressResult = TaskTools.executeTaskList(Map.of("status", "in_progress"));
        assertEquals(1, progressResult.getMetadata().get("tasks").size(), "应该有 1 个 IN_PROGRESS 任务");

        // 过滤 COMPLETED 任务
        LocalToolResult completeResult = TaskTools.executeTaskList(Map.of("status", "completed"));
        assertEquals(1, completeResult.getMetadata().get("tasks").size(), "应该有 1 个 COMPLETED 任务");
    }

    @Test
    @DisplayName("多任务场景：并行任务管理")
    void multiTaskScenario_parallelTaskManagement() {
        // 创建 3 个并行任务
        LocalToolResult r1 = TaskTools.executeTaskCreate(Map.of(
                "subject", "前端开发",
                "description", "实现用户界面",
                "activeForm", "正在开发前端界面"
        ));
        LocalToolResult r2 = TaskTools.executeTaskCreate(Map.of(
                "subject", "后端开发",
                "description", "实现 API 接口",
                "activeForm", "正在开发后端 API"
        ));
        LocalToolResult r3 = TaskTools.executeTaskCreate(Map.of(
                "subject", "测试",
                "description", "编写集成测试",
                "activeForm", "正在编写测试用例"
        ));

        // 同时启动所有任务
        TaskTools.executeTaskUpdate(Map.of("name", "前端开发", "status", "in_progress"));
        TaskTools.executeTaskUpdate(Map.of("name", "后端开发", "status", "in_progress"));
        TaskTools.executeTaskUpdate(Map.of("name", "测试", "status", "in_progress"));

        // 验证所有任务状态
        Map<String, Task> allTasks = TaskTools.getAllTasks();
        long inProgressCount = allTasks.values().stream()
                .filter(t -> t.status == TaskTools.TaskStatus.IN_PROGRESS)
                .count();
        assertEquals(3, inProgressCount);
    }

    // ==================== 边缘情况测试 ====================

    @Test
    @DisplayName("边缘情况：任务不存在")
    void edgeCase_taskNotFound() {
        // TaskGet - 不存在的任务
        LocalToolResult getResult = TaskTools.executeTaskGet(Map.of("task_id", "non-existent-id"));
        assertFalse(getResult.isSuccess());
        assertTrue(getResult.getError() != null && getResult.getError().contains("Task not found"), "应该返回 Task not found 错误");

        // TaskUpdate - 不存在的任务
        LocalToolResult updateResult = TaskTools.executeTaskUpdate(Map.of(
                "task_id", "non-existent-id",
                "status", "completed"
        ));
        assertFalse(updateResult.isSuccess());
        assertTrue(updateResult.getError() != null && updateResult.getError().contains("Task not found"), "应该返回 Task not found 错误");

        // TaskStop - 不存在的任务
        LocalToolResult stopResult = TaskTools.executeTaskStop(Map.of("task_id", "non-existent-id"));
        assertFalse(stopResult.isSuccess());
        assertTrue(stopResult.getError() != null && stopResult.getError().contains("Task not found"), "应该返回 Task not found 错误");

        // TaskOutput - 不存在的任务
        LocalToolResult outputResult = TaskTools.executeTaskOutput(Map.of("task_id", "non-existent-id"));
        assertFalse(outputResult.isSuccess());
        assertTrue(outputResult.getError() != null && outputResult.getError().contains("Task not found"), "应该返回 Task not found 错误");
    }

    @Test
    @DisplayName("边缘情况：状态转换验证")
    void edgeCase_statusTransitions() {
        // 创建任务
        LocalToolResult createResult = TaskTools.executeTaskCreate(Map.of(
                "subject", "状态转换测试",
                "description", "测试各种状态转换"
        ));
        String taskId = extractTaskId(createResult.getMetadata());

        // PENDING -> IN_PROGRESS
        TaskTools.executeTaskUpdate(Map.of("task_id", taskId, "status", "in_progress"));
        assertEquals("in_progress", getTaskStatus(taskId));

        // IN_PROGRESS -> COMPLETED
        TaskTools.executeTaskUpdate(Map.of("task_id", taskId, "status", "completed"));
        assertEquals("completed", getTaskStatus(taskId));

        // COMPLETED -> STOPPED (验证可以停止已完成的任务)
        TaskTools.executeTaskStop(Map.of("task_id", taskId));
        assertEquals("stopped", getTaskStatus(taskId));

        // 创建新任务测试 FAILED 状态
        LocalToolResult createResult2 = TaskTools.executeTaskCreate(Map.of(
                "subject", "失败任务",
                "description", "模拟失败"
        ));
        String taskId2 = extractTaskId(createResult2.getMetadata());
        TaskTools.executeTaskUpdate(Map.of("task_id", taskId2, "status", "failed"));
        assertEquals("failed", getTaskStatus(taskId2));
    }

    @Test
    @DisplayName("边缘情况：必填字段验证")
    void edgeCase_requiredFieldValidation() {
        // subject 为空
        LocalToolResult r1 = TaskTools.executeTaskCreate(Map.of(
                "description", "只有描述"
        ));
        assertFalse(r1.isSuccess());
        assertTrue(r1.getError() != null && r1.getError().contains("subject"), "应该返回 subject 错误");

        // description 为空
        LocalToolResult r2 = TaskTools.executeTaskCreate(Map.of(
                "subject", "只有主题"
        ));
        assertFalse(r2.isSuccess());
        assertTrue(r2.getError() != null && r2.getError().contains("description"), "应该返回 description 错误");

        // task_id 为空（TaskGet）
        LocalToolResult r3 = TaskTools.executeTaskGet(Map.of());
        assertFalse(r3.isSuccess());
        assertTrue(r3.getError() != null && r3.getError().contains("task_id"), "应该返回 task_id 错误");
    }

    @Test
    @DisplayName("边缘情况：参数兼容性测试")
    void edgeCase_parameterCompatibility() {
        // 测试 name 代替 subject
        LocalToolResult r1 = TaskTools.executeTaskCreate(Map.of(
                "name", "使用 name 参数",
                "description", "测试参数兼容性"
        ));
        assertTrue(r1.isSuccess());

        // 测试 task_instruction 代替 description
        LocalToolResult r2 = TaskTools.executeTaskCreate(Map.of(
                "subject", "使用 task_instruction 参数",
                "task_instruction", "这是任务说明"
        ));
        assertTrue(r2.isSuccess());

        // 测试 id 代替 task_id
        String taskId = extractTaskId(r1.getMetadata());
        LocalToolResult r3 = TaskTools.executeTaskGet(Map.of("id", taskId));
        assertTrue(r3.isSuccess());
    }

    // ==================== 辅助方法 ====================

    private String extractTaskId(JsonNode metadata) {
        if (metadata != null && metadata.has("task")) {
            return metadata.get("task").get("id").asText();
        }
        return null;
    }

    private String getTaskStatus(String taskId) {
        Task task = TaskTools.getAllTasks().get(taskId);
        return task != null ? task.status.name().toLowerCase() : null;
    }

    private Long getTaskCompletedAt(String taskId) {
        Task task = TaskTools.getAllTasks().get(taskId);
        return task != null ? task.completedAt : null;
    }
}
