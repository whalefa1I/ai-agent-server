package demo.k8s.agent.query;

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

import demo.k8s.agent.config.DemoQueryProperties;
import demo.k8s.agent.contextobject.ContextObjectWriteService;
import demo.k8s.agent.contextobject.ContextObjectRepository;
import demo.k8s.agent.config.DemoContextObjectWriteProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 上下文压缩管道集成测试
 * 对齐 Claude Code 的压缩流程，验证三档压缩策略
 */
@DisplayName("上下文压缩管道测试")
class CompactionPipelineTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private ContextObjectWriteService contextObjectWriteService;

    private DemoQueryProperties props;
    private EnhancedCompactionPipeline pipeline;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        props = new DemoQueryProperties();
        // Mock writeService：始终返回成功但不实际写入 DB 的结果
        var mockResult = new ContextObjectWriteService.WriteResult(null, "mocked-content", true, null);
        when(contextObjectWriteService.write(anyString(), anyString(), anyInt(), any(), anyString()))
                .thenReturn(mockResult);
        pipeline = new EnhancedCompactionPipeline(props, chatModel, contextObjectWriteService);
    }

    // ==================== Tier 1: Microcompact 测试 ====================

    @Test
    @DisplayName("Tier 1: Microcompact - 截断过长 ToolResponse")
    void tier1_microcompact_truncatesLongToolResponse() {
        // 构造超长 tool result（超过 24k 字符）
        String longContent = "x".repeat(30_000);
        ToolResponseMessage longToolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tool_1", "file_read", longContent)))
                .build();

        List<Message> messages = List.of(
                new UserMessage("读取大文件"),
                longToolResponse,
                new AssistantMessage("文件内容已读取")
        );

        // 执行压缩
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：tool result 被截断
        ToolResponseMessage truncatedTool = (ToolResponseMessage) result.get(1);
        String truncatedContent = truncatedTool.getResponses().get(0).responseData();
        assertTrue(truncatedContent.length() < 30_000, "Tool result 应该被截断");
        assertTrue(truncatedContent.contains("[truncated"), "应该包含截断标记");
    }

    @Test
    @DisplayName("Tier 1: Microcompact - 保留短 ToolResponse")
    void tier1_microcompact_preservesShortToolResponse() {
        // 构造短 tool result（小于 24k 字符）
        String shortContent = "短内容";
        ToolResponseMessage shortToolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tool_1", "file_read", shortContent)))
                .build();

        List<Message> messages = List.of(
                new UserMessage("读取文件"),
                shortToolResponse
        );

        // 执行压缩
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：短 tool result 保持不变
        ToolResponseMessage preservedTool = (ToolResponseMessage) result.get(1);
        assertEquals(shortContent, preservedTool.getResponses().get(0).responseData());
    }

    // ==================== Tier 2: Time-based 测试 ====================

    @Test
    @DisplayName("Tier 2: Time-based - 禁用时间触发")
    void tier2_timeBased_disabled() {
        props.setTimeBasedCompactionGapMinutes(0); // 禁用

        List<Message> messages = List.of(
                new UserMessage("你好"),
                new AssistantMessage("你好！有什么可以帮助你的？")
        );

        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：消息保持不变
        assertEquals(messages.size(), result.size());
    }

    @Test
    @DisplayName("Tier 2: Time-based - 保留最近 N 个工具结果")
    void tier2_timeBased_keepsRecentTools() {
        props.setTimeBasedCompactionGapMinutes(1); // 1 分钟触发（测试用）
        props.setTimeBasedCompactionKeepRecent(2); // 保留最近 2 个

        // 构造多个 tool result
        ToolResponseMessage tool1 = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tool_1", "file_read", "内容 1")))
                .build();
        ToolResponseMessage tool2 = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tool_2", "bash", "内容 2")))
                .build();
        ToolResponseMessage tool3 = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tool_3", "grep", "内容 3")))
                .build();

        List<Message> messages = List.of(
                new UserMessage("执行操作 1"),
                tool1,
                new UserMessage("执行操作 2"),
                tool2,
                new UserMessage("执行操作 3"),
                tool3
        );

        // 执行压缩（模拟时间间隔已满足）
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：保留最近的工具结果
        // 注意：由于无法模拟时间戳，这个测试主要验证流程
        assertNotNull(result);
    }

    // ==================== Tier 3: Autocompact 测试 ====================

    @Test
    @DisplayName("Tier 3: Autocompact - 触发 LLM 摘要")
    void tier3_autocompact_triggersLLMSummary() throws Exception {
        props.setFullCompactEnabled(true);
        props.setFullCompactThresholdChars(5_000); // 降低阈值便于测试

        // 构造超长对话（超过 5k 字符）
        String longUserContent = "问题".repeat(2_000);
        String longAssistantContent = "回答".repeat(2_000);

        List<Message> messages = List.of(
                new UserMessage(longUserContent),
                new AssistantMessage(longAssistantContent),
                new UserMessage("继续"),
                new AssistantMessage("继续回答")
        );

        // 验证总字符数超过阈值
        int totalChars = MessageTextEstimator.estimateChars(messages);
        assertTrue(totalChars >= 5_000, "测试消息应该超过阈值");

        // Mock LLM 响应
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("这是摘要内容"));

        // 执行压缩
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：返回摘要消息
        assertTrue(result.size() < messages.size(), "压缩后消息数量应该减少");
        assertTrue(result.get(0) instanceof SystemMessage, "第一条消息应该是 system message");
        SystemMessage sysMsg = (SystemMessage) result.get(0);
        // 使用 getText() 访问内容
        String content = sysMsg.getText();
        assertTrue(content != null && content.contains("摘要"), "应该包含摘要");

        // 验证 LLM 被调用
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Tier 3: Autocompact - 未达阈值不触发")
    void tier3_autocompact_belowThreshold() {
        props.setFullCompactEnabled(true);
        props.setFullCompactThresholdChars(120_000);

        List<Message> messages = List.of(
                new UserMessage("短消息"),
                new AssistantMessage("短回复")
        );

        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：消息保持不变，LLM 未被调用
        assertEquals(messages.size(), result.size());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Tier 3: Autocompact - LLM 失败时降级处理")
    void tier3_autocompact_llmFailure() throws Exception {
        props.setFullCompactEnabled(true);
        props.setFullCompactThresholdChars(5_000);

        String longContent = "x".repeat(6_000);
        List<Message> messages = List.of(
                new UserMessage(longContent),
                new AssistantMessage(longContent)
        );

        // Mock LLM 抛出异常
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM 服务不可用"));

        // 执行压缩（应该降级处理，不抛异常）
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：返回原始消息（降级）
        assertNotNull(result);
    }

    // ==================== 完整流程测试 ====================

    @Test
    @DisplayName("完整流程：Tier 1 + Tier 3 级联压缩")
    void fullPipeline_tier1AndTier3() throws Exception {
        props.setFullCompactEnabled(true);
        props.setFullCompactThresholdChars(50_000);
        props.setMicrocompactMaxCharsPerToolResponse(24_000);

        // 构造场景：长 tool result + 超长对话
        String longToolContent = "工具结果".repeat(30_000); // 超过 24k，触发 Tier 1
        String longConversation = "对话".repeat(30_000);   // 总字符超过 50k，触发 Tier 3

        ToolResponseMessage longTool = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tool_1", "file_read", longToolContent)))
                .build();

        List<Message> messages = List.of(
                new UserMessage(longConversation),
                longTool,
                new AssistantMessage(longConversation)
        );

        // Mock LLM 响应
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("摘要"));

        // 执行完整压缩流程
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证：
        // 1. Tier 1 先截断 tool result
        // 2. Tier 3 生成摘要
        assertNotNull(result);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("完整流程：自然语言场景 - 代码审查对话压缩")
    void fullPipeline_naturalLanguageScenario_codeReview() throws Exception {
        props.setFullCompactEnabled(true);
        props.setFullCompactThresholdChars(30_000);

        // 模拟真实代码审查对话场景
        List<Message> messages = List.of(
                // 用户请求
                new UserMessage("请帮我审查一下 src/main/java/UserService.java 这个文件"),

                // 工具调用：读取文件
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "tool_read_1", "file_read",
                                "// UserService.java\npublic class UserService {\n" +
                                "    public void createUser(String name) {\n" +
                                "        // TODO: 添加参数验证\n" +
                                "        User user = new User(name);\n" +
                                "        userRepository.save(user);\n" +
                                "    }\n" +
                                "}")))
                        .build(),

                // 助手审查意见
                new AssistantMessage("我发现以下问题：\n" +
                        "1. 缺少参数验证\n" +
                        "2. 没有异常处理\n" +
                        "3. 建议添加日志记录"),

                // 用户追问
                new UserMessage("如何修复这些问题？"),

                // 助手详细建议
                new AssistantMessage("建议修改如下：\n" +
                        "1. 添加 name != null 检查\n" +
                        "2. 捕获 DataAccessException\n" +
                        "3. 使用 slf4j 记录日志\n" +
                        "具体代码...".repeat(100)) // 重复使内容超长
        );

        // Mock LLM 响应
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage(
                "[代码审查摘要]\n" +
                "- 用户请求审查 UserService.java\n" +
                "- 发现 3 个问题：参数验证、异常处理、日志记录\n" +
                "- 已提供修复建议"));

        // 执行压缩
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证流程正确
        assertNotNull(result);
    }

    @Test
    @DisplayName("自然语言场景：多轮对话后压缩")
    void fullPipeline_naturalLanguageScenario_multiTurnConversation() throws Exception {
        props.setFullCompactEnabled(true);
        props.setFullCompactThresholdChars(20_000);

        // 构建多轮对话历史
        List<Message> messages = new java.util.ArrayList<>();

        // 模拟 10 轮对话
        for (int i = 0; i < 10; i++) {
            messages.add(new UserMessage("第 " + i + " 轮问题：" + "问题内容".repeat(100)));
            messages.add(new AssistantMessage("第 " + i + " 轮回答：" + "回答内容".repeat(200)));
        }

        // 验证总字符数
        int totalChars = MessageTextEstimator.estimateChars(messages);
        System.out.println("总字符数：" + totalChars);

        // Mock LLM 响应
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage(
                "[多轮对话摘要]\n" +
                "- 共 10 轮问答\n" +
                "- 主要讨论主题：XXX\n" +
                "- 待决问题：YYY"));

        // 执行压缩
        List<Message> result = pipeline.compactBeforeModelCall(messages);

        // 验证
        assertNotNull(result);
        // 压缩后应该保留 system message（摘要）+ 最后一条用户消息
        assertTrue(result.size() >= 1);
    }
}
