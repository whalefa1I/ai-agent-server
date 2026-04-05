package demo.k8s.agent.skills;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通用技能执行器测试
 */
@SpringBootTest
@ActiveProfiles("test")
public class GenericSkillExecutorTest {

    @Autowired
    private GenericSkillExecutor genericSkillExecutor;

    // 使用绝对路径
    private final String calculatorDir = Paths.get(System.getProperty("user.dir"), "skills", "calculator").toString();
    private final String fileOrganizerDir = Paths.get(System.getProperty("user.dir"), "skills", "file-organizer-zh").toString();
    private final String localFileDir = Paths.get(System.getProperty("user.dir"), "skills", "local-file").toString();

    @Test
    void testDetectSkillTypeCalculator() {
        GenericSkillExecutor.SkillType type = genericSkillExecutor.detectSkillType(calculatorDir);
        System.out.println("Calculator 技能类型：" + type);
        assertEquals(GenericSkillExecutor.SkillType.PYTHON, type);
    }

    @Test
    void testDetectSkillTypeFileOrganizer() {
        GenericSkillExecutor.SkillType type = genericSkillExecutor.detectSkillType(fileOrganizerDir);
        System.out.println("FileOrganizer 技能类型：" + type);
        assertEquals(GenericSkillExecutor.SkillType.NODEJS, type);
    }

    @Test
    void testDetectSkillTypeLocalFile() {
        GenericSkillExecutor.SkillType type = genericSkillExecutor.detectSkillType(localFileDir);
        System.out.println("LocalFile 技能类型：" + type);
        assertEquals(GenericSkillExecutor.SkillType.NODEJS, type);
    }

    @Test
    void testExecuteCalculator() {
        String result = genericSkillExecutor.execute(calculatorDir, Map.of("expression", "2 + 3 * 4"));
        System.out.println("计算器结果：" + result);
        assertNotNull(result);
        assertTrue(result.contains("14"), "计算结果应该包含 14");
    }

    @Test
    void testExecuteCalculatorSqrt() {
        String result = genericSkillExecutor.execute(calculatorDir, Map.of("expression", "sqrt(144)"));
        System.out.println("平方根结果：" + result);
        assertNotNull(result);
        assertTrue(result.contains("12"), "结果应该包含 12");
    }

    @Test
    void testExecuteCalculatorPower() {
        String result = genericSkillExecutor.execute(calculatorDir, Map.of("expression", "2^10"));
        System.out.println("幂运算结果：" + result);
        assertNotNull(result);
        assertTrue(result.contains("1024"), "结果应该包含 1024");
    }
}
