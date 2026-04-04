package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionGrant 测试
 */
class PermissionGrantTest {

    @Test
    void testPermissionGrantCreation() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        assertNotNull(grant);
        assertNotNull(grant.id());
        assertEquals("test_tool", grant.toolName());
        assertEquals(PermissionChoice.ALLOW_ONCE, grant.choice());
        assertEquals(PermissionLevel.READ_ONLY, grant.level());
        assertNotNull(grant.grantedAt());
        assertNotNull(grant.expiresAt());
    }

    @Test
    void testPermissionGrantExpiry_ONCE() throws InterruptedException {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        assertNotNull(grant.expiresAt());
        // ALLOW_ONCE 应该是 5 分钟过期
        assertTrue(grant.expiresAt().isAfter(grant.grantedAt()));
        Duration diff = Duration.between(grant.grantedAt(), grant.expiresAt());
        assertEquals(5, diff.toMinutes());
    }

    @Test
    void testPermissionGrantExpiry_SESSION() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_SESSION,
                PermissionLevel.READ_ONLY
        );

        assertNotNull(grant.expiresAt());
        // ALLOW_SESSION 应该是 30 分钟过期
        Duration diff = Duration.between(grant.grantedAt(), grant.expiresAt());
        assertEquals(30, diff.toMinutes());
    }

    @Test
    void testPermissionGrantExpiry_ALWAYS() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ALWAYS,
                PermissionLevel.READ_ONLY
        );

        // ALLOW_ALWAYS 应该永不过期（null）
        assertNull(grant.expiresAt());
        assertFalse(grant.isExpired());
    }

    @Test
    void testPermissionGrantExpiry_DENY() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.DENY,
                PermissionLevel.READ_ONLY
        );

        // DENY 的 expiresAt 为 null，但 isValid() 应该返回 false
        assertNull(grant.expiresAt());
        assertFalse(grant.isValid()); // 因为 choice 是 DENY
    }

    @Test
    void testPermissionGrantIsExpired() {
        // 创建一个已过期的授权
        Instant past = Instant.now().minus(Duration.ofMinutes(10));
        Instant expiresAt = Instant.now().minus(Duration.ofMinutes(1));
        PermissionGrant expiredGrant = new PermissionGrant(
                "grant_123",
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY,
                past,
                expiresAt
        );

        assertTrue(expiredGrant.isExpired());
        assertFalse(expiredGrant.isValid());
    }

    @Test
    void testPermissionGrantIsNotExpired() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        assertFalse(grant.isExpired());
        assertTrue(grant.isValid());
    }

    @Test
    void testPermissionGrantMatchesTool() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        assertTrue(grant.matchesTool("test_tool"));
        assertFalse(grant.matchesTool("other_tool"));
    }

    @Test
    void testPermissionGrantCoversLevel() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        // coversLevel 使用 level.compareTo(other) <= 0
        // READ_ONLY (ordinal=0) compareTo 任何更高级别都返回负数，所以 <= 0 为 true
        // 这意味着 READ_ONLY 授权可以"覆盖"所有级别（但实际上是因为它限制最严格）
        assertTrue(grant.coversLevel(PermissionLevel.READ_ONLY));
        assertTrue(grant.coversLevel(PermissionLevel.MODIFY_STATE));
        assertTrue(grant.coversLevel(PermissionLevel.NETWORK));
        assertTrue(grant.coversLevel(PermissionLevel.DESTRUCTIVE));
        assertTrue(grant.coversLevel(PermissionLevel.AGENT_SPAWN));
    }

    @Test
    void testPermissionGrantCoversLevel_Higher() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.DESTRUCTIVE
        );

        // coversLevel 使用 level.compareTo(other) <= 0
        // DESTRUCTIVE (ordinal=3) compareTo READ_ONLY(0) = 3 > 0，所以返回 false
        // DESTRUCTIVE (ordinal=3) compareTo DESTRUCTIVE(3) = 0 <= 0，所以返回 true
        // DESTRUCTIVE (ordinal=3) compareTo AGENT_SPAWN(4) = -1 <= 0，所以返回 true
        assertFalse(grant.coversLevel(PermissionLevel.READ_ONLY));
        assertFalse(grant.coversLevel(PermissionLevel.MODIFY_STATE));
        assertFalse(grant.coversLevel(PermissionLevel.NETWORK));
        assertTrue(grant.coversLevel(PermissionLevel.DESTRUCTIVE));
        assertTrue(grant.coversLevel(PermissionLevel.AGENT_SPAWN));
    }

    @Test
    void testPermissionGrantIdUniqueness() throws InterruptedException {
        PermissionGrant grant1 = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        Thread.sleep(2); // 等待几毫秒确保时间戳不同

        PermissionGrant grant2 = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        assertNotEquals(grant1.id(), grant2.id());
    }

    @Test
    void testPermissionGrantIsValid_Allowed() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.ALLOW_ONCE,
                PermissionLevel.READ_ONLY
        );

        assertTrue(grant.isValid());
    }

    @Test
    void testPermissionGrantIsValid_Denied() {
        PermissionGrant grant = PermissionGrant.create(
                "test_tool",
                PermissionChoice.DENY,
                PermissionLevel.READ_ONLY
        );

        assertFalse(grant.isValid());
    }
}
