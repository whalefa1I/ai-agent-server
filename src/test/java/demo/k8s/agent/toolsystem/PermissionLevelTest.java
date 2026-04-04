package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionLevel 测试
 */
class PermissionLevelTest {

    @Test
    void testPermissionLevelLabels() {
        assertEquals("只读", PermissionLevel.READ_ONLY.getLabel());
        assertEquals("修改状态", PermissionLevel.MODIFY_STATE.getLabel());
        assertEquals("网络访问", PermissionLevel.NETWORK.getLabel());
        assertEquals("破坏性", PermissionLevel.DESTRUCTIVE.getLabel());
        assertEquals("子代理", PermissionLevel.AGENT_SPAWN.getLabel());
    }

    @Test
    void testPermissionLevelIcons() {
        assertEquals("📖", PermissionLevel.READ_ONLY.getIcon());
        assertEquals("✏️", PermissionLevel.MODIFY_STATE.getIcon());
        assertEquals("🌐", PermissionLevel.NETWORK.getIcon());
        assertEquals("⚠️", PermissionLevel.DESTRUCTIVE.getIcon());
        assertEquals("🤖", PermissionLevel.AGENT_SPAWN.getIcon());
    }

    @Test
    void testPermissionLevelRequiresConfirmation() {
        // 只有 READ_ONLY 不需要确认
        assertFalse(PermissionLevel.READ_ONLY.requiresConfirmation());
        assertTrue(PermissionLevel.MODIFY_STATE.requiresConfirmation());
        assertTrue(PermissionLevel.NETWORK.requiresConfirmation());
        assertTrue(PermissionLevel.DESTRUCTIVE.requiresConfirmation());
        assertTrue(PermissionLevel.AGENT_SPAWN.requiresConfirmation());
    }

    @Test
    void testPermissionLevelOrdinal() {
        // 验证等级顺序（从低到高）
        assertEquals(0, PermissionLevel.READ_ONLY.ordinal());
        assertEquals(1, PermissionLevel.MODIFY_STATE.ordinal());
        assertEquals(2, PermissionLevel.NETWORK.ordinal());
        assertEquals(3, PermissionLevel.DESTRUCTIVE.ordinal());
        assertEquals(4, PermissionLevel.AGENT_SPAWN.ordinal());
    }

    @Test
    void testPermissionLevelComparison() {
        // 验证等级顺序可用于比较
        assertTrue(PermissionLevel.READ_ONLY.compareTo(PermissionLevel.MODIFY_STATE) < 0);
        assertTrue(PermissionLevel.MODIFY_STATE.compareTo(PermissionLevel.NETWORK) < 0);
        assertTrue(PermissionLevel.NETWORK.compareTo(PermissionLevel.DESTRUCTIVE) < 0);
        assertTrue(PermissionLevel.DESTRUCTIVE.compareTo(PermissionLevel.AGENT_SPAWN) < 0);

        // 同级比较
        assertEquals(0, PermissionLevel.READ_ONLY.compareTo(PermissionLevel.READ_ONLY));
        assertEquals(0, PermissionLevel.DESTRUCTIVE.compareTo(PermissionLevel.DESTRUCTIVE));
    }

    @Test
    void testPermissionLevelValueOf() {
        assertEquals(PermissionLevel.READ_ONLY, PermissionLevel.valueOf("READ_ONLY"));
        assertEquals(PermissionLevel.MODIFY_STATE, PermissionLevel.valueOf("MODIFY_STATE"));
        assertEquals(PermissionLevel.NETWORK, PermissionLevel.valueOf("NETWORK"));
        assertEquals(PermissionLevel.DESTRUCTIVE, PermissionLevel.valueOf("DESTRUCTIVE"));
        assertEquals(PermissionLevel.AGENT_SPAWN, PermissionLevel.valueOf("AGENT_SPAWN"));
    }

    @Test
    void testPermissionLevelAllValues() {
        PermissionLevel[] values = PermissionLevel.values();

        assertEquals(5, values.length);
        assertArrayEquals(new PermissionLevel[]{
                PermissionLevel.READ_ONLY,
                PermissionLevel.MODIFY_STATE,
                PermissionLevel.NETWORK,
                PermissionLevel.DESTRUCTIVE,
                PermissionLevel.AGENT_SPAWN
        }, values);
    }

    @Test
    void testPermissionLevelDescendingOrder() {
        // 验证最高等级
        PermissionLevel[] levels = PermissionLevel.values();
        PermissionLevel maxLevel = levels[0];
        for (PermissionLevel level : levels) {
            if (level.compareTo(maxLevel) > 0) {
                maxLevel = level;
            }
        }

        assertEquals(PermissionLevel.AGENT_SPAWN, maxLevel);
    }

    @Test
    void testPermissionLevelRiskExplanation() {
        // 验证每个等级都有图标和标签
        for (PermissionLevel level : PermissionLevel.values()) {
            assertNotNull(level.getIcon(), level.name() + " 应该有图标");
            assertNotNull(level.getLabel(), level.name() + " 应该有标签");
        }
    }
}
