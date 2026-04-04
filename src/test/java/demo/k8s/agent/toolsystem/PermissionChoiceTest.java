package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionChoice 测试
 */
class PermissionChoiceTest {

    @Test
    void testPermissionChoiceLabels() {
        assertEquals("本次允许", PermissionChoice.ALLOW_ONCE.getLabel());
        assertEquals("会话允许", PermissionChoice.ALLOW_SESSION.getLabel());
        assertEquals("始终允许", PermissionChoice.ALLOW_ALWAYS.getLabel());
        assertEquals("拒绝", PermissionChoice.DENY.getLabel());
    }

    @Test
    void testPermissionChoiceSessionDurationMs() {
        // ALLOW_ONCE 没有固定时长
        assertNull(PermissionChoice.ALLOW_ONCE.getSessionDurationMs());

        // ALLOW_SESSION 有 30 分钟时长
        assertEquals(30 * 60 * 1000L, PermissionChoice.ALLOW_SESSION.getSessionDurationMs());

        // ALLOW_ALWAYS 永不过期
        assertNull(PermissionChoice.ALLOW_ALWAYS.getSessionDurationMs());

        // DENY 没有时长
        assertNull(PermissionChoice.DENY.getSessionDurationMs());
    }

    @Test
    void testPermissionChoiceIsAllowed() {
        assertTrue(PermissionChoice.ALLOW_ONCE.isAllowed());
        assertTrue(PermissionChoice.ALLOW_SESSION.isAllowed());
        assertTrue(PermissionChoice.ALLOW_ALWAYS.isAllowed());
        assertFalse(PermissionChoice.DENY.isAllowed());
    }

    @Test
    void testPermissionChoiceIsPersistent() {
        assertFalse(PermissionChoice.ALLOW_ONCE.isPersistent());
        assertFalse(PermissionChoice.ALLOW_SESSION.isPersistent());
        assertTrue(PermissionChoice.ALLOW_ALWAYS.isPersistent());
        assertFalse(PermissionChoice.DENY.isPersistent());
    }

    @Test
    void testPermissionChoiceValueOf() {
        assertEquals(PermissionChoice.ALLOW_ONCE, PermissionChoice.valueOf("ALLOW_ONCE"));
        assertEquals(PermissionChoice.ALLOW_SESSION, PermissionChoice.valueOf("ALLOW_SESSION"));
        assertEquals(PermissionChoice.ALLOW_ALWAYS, PermissionChoice.valueOf("ALLOW_ALWAYS"));
        assertEquals(PermissionChoice.DENY, PermissionChoice.valueOf("DENY"));
    }

    @Test
    void testPermissionChoiceOrdinal() {
        assertEquals(0, PermissionChoice.ALLOW_ONCE.ordinal());
        assertEquals(1, PermissionChoice.ALLOW_SESSION.ordinal());
        assertEquals(2, PermissionChoice.ALLOW_ALWAYS.ordinal());
        assertEquals(3, PermissionChoice.DENY.ordinal());
    }

    @Test
    void testPermissionChoiceAllValues() {
        PermissionChoice[] values = PermissionChoice.values();

        assertEquals(4, values.length);
        assertArrayEquals(new PermissionChoice[]{
                PermissionChoice.ALLOW_ONCE,
                PermissionChoice.ALLOW_SESSION,
                PermissionChoice.ALLOW_ALWAYS,
                PermissionChoice.DENY
        }, values);
    }
}
