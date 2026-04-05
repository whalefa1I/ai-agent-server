package demo.k8s.agent.skills;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能执行集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
public class SkillExecutionIntegrationTest {

    @Autowired
    private SkillExecutorRegistry skillExecutorRegistry;

    @Test
    void testCalculatorSkillExecution() {
        // 直接使用执行器测试
        String result = skillExecutorRegistry.executeSkill("calculator", Map.of("expression", "100 + 50"));
        System.out.println("计算器结果：" + result);
        assertNotNull(result);
        assertTrue(result.contains("150"), "结果应该包含 150");
    }

    @Test
    void testJsonSkillExecution() {
        String result = skillExecutorRegistry.executeSkill("json", Map.of(
            "action", "format",
            "input", "{\"test\":\"value\"}"
        ));
        System.out.println("JSON 结果：" + result);
        assertNotNull(result);
        assertTrue(result.contains("格式化") || result.contains("format"), "结果应该包含格式化后的 JSON");
    }

    @Test
    void testMarkdownSkillExecution() {
        String result = skillExecutorRegistry.executeSkill("markdown", Map.of(
            "action", "check",
            "content", "# 标题\n\n正文内容"
        ));
        System.out.println("Markdown 结果：" + result);
        assertNotNull(result);
        assertTrue(result.contains("检查") || result.contains("报告"), "结果应该包含检查报告");
    }

    @Test
    void testFileOrganizerSkillExecution() {
        String result = skillExecutorRegistry.executeSkill("file-organizer-zh", Map.of(
            "action", "scan",
            "directory", System.getProperty("user.dir")
        ));
        System.out.println("文件整理结果：" + result);
        assertNotNull(result);
        assertTrue(result.contains("扫描") || result.contains("文件") || result.contains("目录"), "结果应该包含扫描信息");
    }

    @Test
    void testSkillExecutorRegistryHasAllExecutors() {
        // 验证所有执行器都已注册
        assertTrue(skillExecutorRegistry.hasExecutor("calculator"), "应该有 calculator 执行器");
        assertTrue(skillExecutorRegistry.hasExecutor("json"), "应该有 json 执行器");
        assertTrue(skillExecutorRegistry.hasExecutor("markdown"), "应该有 markdown 执行器");
        assertTrue(skillExecutorRegistry.hasExecutor("file-organizer-zh"), "应该有 file-organizer-zh 执行器");
    }
}
