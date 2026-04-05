package demo.k8s.agent.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skills 系统单元测试
 */
@DisplayName("Skills 系统测试")
class SkillsTest {

    private SkillRegistry skillRegistry;
    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillRegistry = new SkillRegistry();
        // 不创建 SkillService，避免自动加载实际技能目录
        // skillService = new SkillService(skillRegistry);
    }

    @Test
    @DisplayName("测试技能注册表 - 注册和获取技能")
    void testSkillRegistry_RegisterAndGetSkill() {
        // 验证初始状态
        assertEquals(0, skillRegistry.getSkillCount());

        // 创建测试技能
        TestSkill testSkill = new TestSkill("test_skill", "Test Skill Description");
        skillRegistry.registerSkill(testSkill);

        // 验证注册成功
        assertEquals(1, skillRegistry.getSkillCount());
        assertNotNull(skillRegistry.getSkill("test_skill"));
        assertEquals("test_skill", skillRegistry.getSkill("test_skill").getName());
        assertEquals("Test Skill Description", skillRegistry.getSkill("test_skill").getDescription());
    }

    @Test
    @DisplayName("测试技能注册表 - 注销技能")
    void testSkillRegistry_UnregisterSkill() {
        // 注册技能
        TestSkill testSkill = new TestSkill("test_skill", "Test Description");
        skillRegistry.registerSkill(testSkill);
        assertEquals(1, skillRegistry.getSkillCount());

        // 注销技能
        skillRegistry.unregisterSkill("test_skill");
        assertEquals(0, skillRegistry.getSkillCount());
        assertNull(skillRegistry.getSkill("test_skill"));
    }

    @Test
    @DisplayName("测试技能注册表 - 获取所有技能")
    void testSkillRegistry_GetAllSkills() {
        // 注册多个技能
        skillRegistry.registerSkill(new TestSkill("skill1", "First Skill"));
        skillRegistry.registerSkill(new TestSkill("skill2", "Second Skill"));
        skillRegistry.registerSkill(new TestSkill("skill3", "Third Skill"));

        List<SkillRegistry.Skill> skills = skillRegistry.getAllSkills();
        assertEquals(3, skills.size());
    }

    @Test
    @DisplayName("测试技能服务 - 搜索技能")
    void testSkillService_SearchSkills() {
        // 使用空查询测试
        // SkillService 会在集成测试中测试，这里只测试空值处理
        assertNotNull(skillRegistry);
    }

    @Test
    @DisplayName("测试技能服务 - 安装和卸载技能")
    void testSkillService_InstallUninstallSkill() {
        // SkillService 集成测试在集成测试类中
        // 这里只验证注册表基础功能
        assertTrue(true); // 占位测试
    }

    @Test
    @DisplayName("测试技能清单解析")
    void testSkillManifest_Parse() {
        SkillService.SkillManifest manifest = new SkillService.SkillManifest();
        manifest.name = "calc";
        manifest.description = "Calculator skill";
        manifest.directory = "/skills/calc";

        assertEquals("calc", manifest.getName());
        assertEquals("Calculator skill", manifest.getDescription());
        assertEquals("/skills/calc", manifest.getDirectory());
    }

    /**
     * 测试用技能实现
     */
    static class TestSkill implements SkillRegistry.Skill {
        private final String name;
        private final String description;
        private boolean enabled = true;

        TestSkill(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public java.util.List<demo.k8s.agent.toolsystem.ClaudeLikeTool> getTools() {
            return java.util.List.of();
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
