package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolFeatureFlags 测试
 */
class ToolFeatureFlagsTest {

    @Test
    void testToolFeatureFlagsCreation() {
        ToolFeatureFlags flags = new ToolFeatureFlags(true, false, true, false, Set.of("tool1", "tool2"));

        assertNotNull(flags);
        assertTrue(flags.simpleMode());
        assertFalse(flags.agentSwarmsEnabled());
        assertTrue(flags.experimentalEnabled());
        assertFalse(flags.internalAntTools());
        assertEquals(2, flags.simpleToolNames().size());
        assertTrue(flags.simpleToolNames().contains("tool1"));
    }

    @Test
    void testToolFeatureFlagsFromProperties() {
        demo.k8s.agent.config.DemoToolsProperties props = new demo.k8s.agent.config.DemoToolsProperties();

        ToolFeatureFlags flags = ToolFeatureFlags.from(props);

        assertNotNull(flags);
    }

    @Test
    void testToolFeatureFlagsAllEnabled() {
        ToolFeatureFlags flags = new ToolFeatureFlags(true, true, true, true, Set.of());

        assertNotNull(flags);
        assertTrue(flags.simpleMode());
        assertTrue(flags.agentSwarmsEnabled());
        assertTrue(flags.experimentalEnabled());
        assertTrue(flags.internalAntTools());
        assertTrue(flags.simpleToolNames().isEmpty());
    }

    @Test
    void testToolFeatureFlagsAllDisabled() {
        ToolFeatureFlags flags = new ToolFeatureFlags(false, false, false, false, Set.of());

        assertNotNull(flags);
        assertFalse(flags.simpleMode());
        assertFalse(flags.agentSwarmsEnabled());
        assertFalse(flags.experimentalEnabled());
        assertFalse(flags.internalAntTools());
    }

    @Test
    void testToolFeatureFlagsEquality() {
        ToolFeatureFlags flags1 = new ToolFeatureFlags(true, false, true, false, Set.of("a"));
        ToolFeatureFlags flags2 = new ToolFeatureFlags(true, false, true, false, Set.of("a"));
        ToolFeatureFlags flags3 = new ToolFeatureFlags(false, false, true, false, Set.of("a"));

        assertEquals(flags1, flags2);
        assertNotEquals(flags1, flags3);
        assertEquals(flags1.hashCode(), flags2.hashCode());
    }

    @Test
    void testToolFeatureFlagsToString() {
        ToolFeatureFlags flags = new ToolFeatureFlags(true, false, true, false, Set.of("test"));

        String str = flags.toString();

        assertNotNull(str);
        assertTrue(str.contains("ToolFeatureFlags"));
    }

    @Test
    void testToolFeatureFlagsWithNullSimpleToolNames() {
        // 验证 from() 方法处理 null simpleToolNames 的情况
        demo.k8s.agent.config.DemoToolsProperties props = new demo.k8s.agent.config.DemoToolsProperties();
        props.setSimpleToolNames(null);

        ToolFeatureFlags flags = ToolFeatureFlags.from(props);

        assertNotNull(flags);
        assertNotNull(flags.simpleToolNames());
        assertTrue(flags.simpleToolNames().contains("Skill")); // 默认值
    }
}
