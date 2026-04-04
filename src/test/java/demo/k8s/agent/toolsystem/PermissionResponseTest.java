package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionResponse 测试
 */
class PermissionResponseTest {

    @Test
    void testPermissionResponseCreation() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.ALLOW_ONCE,
                null,
                null
        );

        assertNotNull(response);
        assertEquals("request_123", response.requestId());
        assertEquals(PermissionChoice.ALLOW_ONCE, response.choice());
        assertNull(response.comment());
    }

    @Test
    void testPermissionResponseWithComment() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.DENY,
                null,
                "This is a comment"
        );

        assertNotNull(response);
        assertEquals("This is a comment", response.comment());
    }

    @Test
    void testPermissionResponseGetSessionDuration_ONCE() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.ALLOW_ONCE,
                null,
                null
        );

        Duration duration = response.getSessionDuration();

        assertNotNull(duration);
        // ALLOW_ONCE 没有指定 sessionDurationMs，使用默认值 30 分钟
        assertEquals(30, duration.toMinutes());
    }

    @Test
    void testPermissionResponseGetSessionDuration_SESSION() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.ALLOW_SESSION,
                null,
                null
        );

        Duration duration = response.getSessionDuration();

        assertNotNull(duration);
        // ALLOW_SESSION 默认 30 分钟
        assertEquals(30, duration.toMinutes());
    }

    @Test
    void testPermissionResponseGetSessionDuration_SESSION_WithCustomDuration() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.ALLOW_SESSION,
                60, // 60 分钟
                null
        );

        Duration duration = response.getSessionDuration();

        assertNotNull(duration);
        assertEquals(60, duration.toMinutes());
    }

    @Test
    void testPermissionResponseGetSessionDuration_ALWAYS() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.ALLOW_ALWAYS,
                null,
                null
        );

        Duration duration = response.getSessionDuration();

        assertNotNull(duration);
        // ALLOW_ALWAYS 永不过期，但 getSessionDuration 返回默认 30 分钟
        assertEquals(30, duration.toMinutes());
    }

    @Test
    void testPermissionResponseGetSessionDuration_DENY() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.DENY,
                null,
                null
        );

        Duration duration = response.getSessionDuration();

        assertNotNull(duration);
        // DENY 默认 30 分钟（虽然实际上不会用）
        assertEquals(30, duration.toMinutes());
    }

    @Test
    void testPermissionResponseGetSessionDuration_WithZeroDuration() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.ALLOW_SESSION,
                0,
                null
        );

        Duration duration = response.getSessionDuration();

        assertNotNull(duration);
        // 0 会被视为未指定，使用默认值
        assertEquals(30, duration.toMinutes());
    }

    @Test
    void testPermissionResponseGetSessionDuration_WithNegativeDuration() {
        PermissionResponse response = new PermissionResponse(
                "request_123",
                PermissionChoice.ALLOW_SESSION,
                -10,
                null
        );

        Duration duration = response.getSessionDuration();

        assertNotNull(duration);
        // 负数会被视为未指定，使用默认值
        assertEquals(30, duration.toMinutes());
    }

    @Test
    void testPermissionResponseWithAllChoices() {
        for (PermissionChoice choice : PermissionChoice.values()) {
            PermissionResponse response = new PermissionResponse(
                    "request_123",
                    choice,
                    null,
                    null
            );

            assertNotNull(response);
            assertEquals(choice, response.choice());
            assertNotNull(response.getSessionDuration());
        }
    }
}
