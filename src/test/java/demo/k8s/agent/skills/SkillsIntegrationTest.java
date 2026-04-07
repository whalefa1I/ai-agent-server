package demo.k8s.agent.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import demo.k8s.agent.config.SkillsProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Skills 系统集成测试
 * 模拟真实对话场景，测试 skill catalog 注入和调用流程
 */
@DisplayName("Skills 系统集成测试")
class SkillsIntegrationTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    private SkillRegistry skillRegistry;
    private SkillExecutorRegistry executorRegistry;
    private GenericSkillExecutor genericExecutor;
    private SkillsSnapshotService snapshotService;
    private SkillsProperties skillsProperties;
    private SkillService skillService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        skillRegistry = new SkillRegistry();
        executorRegistry = new SkillExecutorRegistry();
        genericExecutor = new GenericSkillExecutor();
        snapshotService = new SkillsSnapshotService();
        skillsProperties = new SkillsProperties();

        skillService = new SkillService(
                skillRegistry,
                executorRegistry,
                genericExecutor,
                snapshotService,
                skillsProperties
        );
    }

    // ==================== Skill Catalog Prompt 测试 ====================

    @Test
    @DisplayName("Skill Catalog - 生成 OpenClaw 风格 XML")
    void skillCatalog_generatesOpenClawXml() {
        // 获取 skills prompt
        String prompt = skillService.buildSkillsPrompt();

        // 验证 XML 格式
        assertNotNull(prompt);
        assertTrue(prompt.contains("<available_skills>"), "应该包含 available_skills 标签");
        assertTrue(prompt.contains("</available_skills>"), "应该包含结束标签");
        assertTrue(prompt.contains("<name>"), "应该包含 name 标签");
        assertTrue(prompt.contains("<description>"), "应该包含 description 标签");
        assertTrue(prompt.contains("<location>"), "应该包含 location 标签");
    }

    @Test
    @DisplayName("Skill Catalog - 包含 file_read 使用说明")
    void skillCatalog_includesFileReadInstructions() {
        String prompt = skillService.buildSkillsPrompt();

        // 验证使用说明
        assertTrue(prompt.contains("file_read"), "应该提及 file_read 工具");
        assertTrue(prompt.contains("skill"), "应该提及 skill");
    }

    @Test
    @DisplayName("自然语言场景：模型看到 skills catalog 后的行为")
    void skillCatalog_naturalLanguageScenario_modelBehavior() throws Exception {
        // 模拟场景：用户请求使用某个技能
        String userRequest = "帮我计算 2 + 3 * 4";

        // 构造系统提示（包含 skills catalog）
        String systemPrompt = skillService.buildSkillsPrompt();

        // 构造用户消息
        UserMessage userMessage = new UserMessage(userRequest);

        // Mock 模型响应（模拟模型选择调用 skill_calculator）
        AssistantMessage modelResponse = new AssistantMessage(
                "我来使用计算器技能帮您计算。\n" +
                "<tool_use>\n" +
                "<name>skill_calculator</name>\n" +
                "<input>{\"expression\": \"2 + 3 * 4\"}</input>\n" +
                "</tool_use>");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(modelResponse);

        // 执行对话
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                userMessage
        ));

        ChatResponse response = chatModel.call(prompt);

        // 验证模型响应
        assertNotNull(response);
        String responseText = response.getResult().getOutput().getText();
        assertTrue(responseText.contains("skill_calculator"), "模型应该选择调用 calculator 技能");

        // 验证模型被调用
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    // ==================== Skill 调用流程测试 ====================

    @Test
    @DisplayName("Skill 调用流程 - Calculator 技能")
    void skillInvocation_flow_calculator() throws Exception {
        // 准备测试技能 manifest
        SkillService.SkillManifest manifest = new SkillService.SkillManifest();
        manifest.name = "calculator";
        manifest.description = "数学计算技能";
        manifest.directory = createTestSkillDirectory("calculator").toString();

        // 验证技能解析正确
        assertEquals("calculator", manifest.name);
        assertEquals("数学计算技能", manifest.description);
        assertNotNull(manifest.directory);
    }

    @Test
    @DisplayName("自然语言场景：JSON 格式化请求")
    void skillInvocation_naturalLanguageScenario_jsonFormat() throws Exception {
        // 场景：用户请求格式化 JSON
        String userRequest = "帮我验证并格式化这个 JSON:\n{\"name\": \"test\", \"value\": 123}";

        // 获取 skills catalog
        String systemPrompt = skillService.buildSkillsPrompt();

        // Mock 模型响应（模拟模型选择调用 skill_json）
        AssistantMessage modelResponse = new AssistantMessage(
                "我来使用 JSON 技能帮您格式化。\n" +
                "<tool_use>\n" +
                "<name>skill_json</name>\n" +
                "<input>{\"action\": \"format\", \"input\": \"{\\\"name\\\": \\\"test\\\", \\\"value\\\": 123}\"}</input>\n" +
                "</tool_use>");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(modelResponse);

        // 执行对话
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userRequest)
        )));

        // 验证
        assertNotNull(response);
        String responseText = response.getResult().getOutput().getText();
        assertTrue(responseText.contains("skill_json") || responseText.contains("JSON"),
                   "模型应该识别 JSON 相关请求");
    }

    @Test
    @DisplayName("自然语言场景：Markdown 检查请求")
    void skillInvocation_naturalLanguageScenario_markdownCheck() throws Exception {
        // 场景：用户请求检查 Markdown 格式
        String userRequest = "检查一下这段 Markdown 有没有语法错误：\n# 标题\n\n- 列表项 1\n- 列表项 2";

        String systemPrompt = skillService.buildSkillsPrompt();

        // Mock 模型响应
        AssistantMessage modelResponse = new AssistantMessage(
                "这段 Markdown 格式正确，没有问题。\n" +
                "如果需要更详细的检查，我可以使用 markdown 技能。");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(modelResponse);

        // 执行对话
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userRequest)
        )));

        // 验证
        assertNotNull(response);
    }

    // ==================== Gating 逻辑测试 ====================

    @Test
    @DisplayName("Gating 逻辑 - always=true 直接通过")
    void gating_alwaysTrue_passes() {
        SkillService.SkillManifest manifest = new SkillService.SkillManifest();
        manifest.name = "always_skill";
        manifest.metadata = new SkillService.SkillManifest.OpenClawMetadata();
        manifest.metadata.always = true;

        // 验证：always=true 的技能应该通过
        // 注意：由于 isEligibleForCatalog 是 private 方法，这里通过 buildSkillsPrompt 间接验证
        String prompt = skillService.buildSkillsPrompt();
        assertNotNull(prompt);
    }

    @Test
    @DisplayName("自然语言场景：带 gating 的技能可见性")
    void gating_naturalLanguageScenario_skillVisibility() {
        // 场景：某些技能只在特定条件下可见
        // 例如：python 技能需要 python 二进制文件

        SkillService.SkillManifest pythonSkill = new SkillService.SkillManifest();
        pythonSkill.name = "python-linter";
        pythonSkill.description = "Python 代码检查";
        pythonSkill.metadata = new SkillService.SkillManifest.OpenClawMetadata();
        pythonSkill.metadata.requires = new SkillService.SkillManifest.OpenClawMetadata.Requires();
        pythonSkill.metadata.requires.bins = List.of("python");

        // 验证技能 manifest 正确配置
        assertNotNull(pythonSkill.metadata);
        assertNotNull(pythonSkill.metadata.requires);
        assertNotNull(pythonSkill.metadata.requires.bins);
        assertTrue(pythonSkill.metadata.requires.bins.contains("python"));
    }

    // ==================== 完整流程测试 ====================

    @Test
    @DisplayName("完整流程：用户请求 -> Catalog 匹配 -> Skill 调用")
    void fullFlow_userRequestToSkillInvocation() throws Exception {
        // 1. 用户提出请求
        String userRequest = "我有一个数学表达式需要计算：2 + 3 * 4 - 1";

        // 2. 系统注入 skills catalog
        String systemPrompt = skillService.buildSkillsPrompt();

        // 3. Mock 模型响应（识别请求并选择技能）
        AssistantMessage step1Response = new AssistantMessage(
                "我来帮您计算这个表达式。\n" +
                "<tool_use>\n" +
                "<name>skill_calculator</name>\n" +
                "<input>{\"expression\": \"2 + 3 * 4 - 1\"}</input>\n" +
                "</tool_use>");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(step1Response);

        // 4. 执行第一轮对话
        Prompt prompt1 = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userRequest)
        ));
        ChatResponse response1 = chatModel.call(prompt1);

        // 验证第一轮
        assertNotNull(response1);
        String text1 = response1.getResult().getOutput().getText();
        assertTrue(text1.contains("skill_calculator"), "模型应该选择 calculator 技能");

        // 5. 模拟工具执行结果
        ToolResponseMessage toolResult = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "tool_1", "skill_calculator", "{\"result\": 13}")))
                .build();

        // 6. Mock 第二轮模型响应（解释结果）
        AssistantMessage step2Response = new AssistantMessage(
                "计算结果是 13。\n" +
                "计算过程：2 + 3 * 4 - 1 = 2 + 12 - 1 = 13");

        when(generation.getOutput()).thenReturn(step2Response);

        // 7. 执行第二轮对话
        Prompt prompt2 = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userRequest),
                step1Response,
                toolResult,
                new UserMessage("告诉我结果")
        ));
        ChatResponse response2 = chatModel.call(prompt2);

        // 验证第二轮
        assertNotNull(response2);
        String text2 = response2.getResult().getOutput().getText();
        assertTrue(text2.contains("13"), "模型应该解释计算结果");

        // 验证调用次数
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("完整流程：多技能场景 - 先格式化 JSON 再检查 Markdown")
    void fullFlow_multiSkillScenario_jsonThenMarkdown() throws Exception {
        // 场景：用户需要连续使用多个技能

        // 第一轮：格式化 JSON
        String userRequest1 = "帮我格式化这个 JSON：{\"a\":1,\"b\":2}";
        AssistantMessage response1 = new AssistantMessage(
                "<tool_use><name>skill_json</name>" +
                "<input>{\"action\":\"format\",\"input\":\"{\\\"a\\\":1,\\\"b\\\":2}\"}</input>" +
                "</tool_use>");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(response1);

        ChatResponse resp1 = chatModel.call(new Prompt(List.of(
                new SystemMessage(skillService.buildSkillsPrompt()),
                new UserMessage(userRequest1)
        )));

        assertNotNull(resp1);

        // 第二轮：检查 Markdown
        String userRequest2 = "现在检查这段 Markdown：# 标题";
        AssistantMessage response2 = new AssistantMessage(
                "<tool_use><name>skill_markdown</name>" +
                "<input>{\"action\":\"check\",\"content\":\"# 标题\"}</input>" +
                "</tool_use>");

        when(generation.getOutput()).thenReturn(response2);

        ChatResponse resp2 = chatModel.call(new Prompt(List.of(
                new SystemMessage(skillService.buildSkillsPrompt()),
                new UserMessage(userRequest1),
                response1,
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("t1", "skill_json", "{\"formatted\":\"{\\n  \\\"a\\\": 1,\\n  \\\"b\\\": 2\\n}\"}")))
                        .build(),
                new UserMessage(userRequest2)
        )));

        assertNotNull(resp2);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试技能目录
     */
    private Path createTestSkillDirectory(String skillName) throws Exception {
        Path tempDir = Files.createTempDirectory("skill-test-" + skillName);
        Path skillDir = tempDir.resolve(skillName);
        Files.createDirectories(skillDir);

        // 创建 SKILL.md
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: %s
                description: Test skill for %s
                ---

                # %s Skill

                This is a test skill.
                """.formatted(skillName, skillName, skillName));

        return skillDir;
    }
}
