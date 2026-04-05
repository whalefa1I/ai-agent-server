package demo.k8s.agent.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能执行器测试
 */
public class SkillExecutorsTest {

    private static final Logger log = LoggerFactory.getLogger(SkillExecutorsTest.class);

    private CalculatorSkillExecutor calculatorExecutor;
    private JsonSkillExecutor jsonExecutor;
    private MarkdownSkillExecutor markdownExecutor;
    private FileOrganizerSkillExecutor fileOrganizerExecutor;

    @BeforeEach
    void setUp() {
        calculatorExecutor = new CalculatorSkillExecutor("skills/calculator");
        jsonExecutor = new JsonSkillExecutor();
        markdownExecutor = new MarkdownSkillExecutor();
        fileOrganizerExecutor = new FileOrganizerSkillExecutor();
    }

    @Test
    void testCalculatorBasicArithmetic() {
        String result = calculatorExecutor.execute(Map.of("expression", "2 + 3 * 4"));
        log.info("计算器结果：{}", result);
        assertNotNull(result);
        // 应该返回 14（先乘除后加减）
        assertTrue(result.contains("14"), "计算结果应该包含 14");
    }

    @Test
    void testCalculatorSquareRoot() {
        String result = calculatorExecutor.execute(Map.of("expression", "sqrt(16)"));
        log.info("平方根结果：{}", result);
        assertNotNull(result);
        assertTrue(result.contains("4"), "结果应该包含 4");
    }

    @Test
    void testCalculatorPower() {
        String result = calculatorExecutor.execute(Map.of("expression", "2^8"));
        log.info("幂运算结果：{}", result);
        assertNotNull(result);
        assertTrue(result.contains("256"), "结果应该包含 256");
    }

    @Test
    void testJsonValidator() {
        String validJson = "{\"name\": \"test\", \"value\": 123}";
        String result = jsonExecutor.execute(Map.of(
            "action", "validate",
            "input", validJson
        ));
        log.info("JSON 验证结果：{}", result);
        assertTrue(result.contains("有效"), "应该验证 JSON 有效");
    }

    @Test
    void testJsonFormat() {
        String compactJson = "{\"name\":\"test\",\"value\":123}";
        String result = jsonExecutor.execute(Map.of(
            "action", "format",
            "input", compactJson
        ));
        log.info("JSON 格式化结果：{}", result);
        assertTrue(result.contains("格式化后的 JSON"), "应该返回格式化后的 JSON");
    }

    @Test
    void testJsonInvalid() {
        String invalidJson = "{\"name\": \"test\",}";
        String result = jsonExecutor.execute(Map.of(
            "action", "validate",
            "input", invalidJson
        ));
        log.info("无效 JSON 验证结果：{}", result);
        assertTrue(result.contains("无效") || result.contains("错误"), "应该报告 JSON 无效");
    }

    @Test
    void testMarkdownValidate() {
        String markdown = "# 标题\n\n这是正文。\n\n- 列表项 1\n- 列表项 2\n";
        String result = markdownExecutor.execute(Map.of(
            "action", "validate",
            "content", markdown
        ));
        log.info("Markdown 验证结果：{}", result);
        assertNotNull(result);
    }

    @Test
    void testMarkdownCheck() {
        String markdown = "# 标题\n\n## 子标题\n\n[链接](https://example.com)\n\n![图片](image.png)";
        String result = markdownExecutor.execute(Map.of(
            "action", "check",
            "content", markdown
        ));
        log.info("Markdown 检查报告：{}", result);
        assertTrue(result.contains("检查报告"), "应该返回检查报告");
    }

    @Test
    void testFileOrganizerScan() {
        // 扫描当前项目目录
        String result = fileOrganizerExecutor.execute(Map.of(
            "action", "scan",
            "directory", System.getProperty("user.dir")
        ));
        log.info("文件扫描结果：{}", result);
        assertNotNull(result);
        assertTrue(result.contains("目录扫描结果") || result.contains("文件数"), "应该返回扫描结果");
    }

    @Test
    void testFileOrganizerClassify() {
        String result = fileOrganizerExecutor.execute(Map.of(
            "action", "classify",
            "directory", "test.py"
        ));
        log.info("文件分类结果：{}", result);
        assertNotNull(result);
    }

    @Test
    void testFileOrganizerOrganizeDryRun() {
        // 测试干运行模式（不实际移动文件）
        String result = fileOrganizerExecutor.execute(Map.of(
            "action", "organize",
            "directory", System.getProperty("user.dir"),
            "dryRun", true
        ));
        log.info("文件整理预览：{}", result);
        assertNotNull(result);
        assertTrue(result.contains("预扫描") || result.contains("统计"), "应该返回预览结果");
    }
}
