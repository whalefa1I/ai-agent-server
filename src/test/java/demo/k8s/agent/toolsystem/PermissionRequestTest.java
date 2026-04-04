package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionRequest 测试
 */
class PermissionRequestTest {

    @Test
    void testPermissionRequestCreation() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test tool description",
                PermissionLevel.READ_ONLY,
                "Input summary",
                "Risk explanation"
        );

        assertNotNull(request);
        assertNotNull(request.id());
        assertEquals("test_tool", request.toolName());
        assertEquals("Test tool description", request.toolDescription());
        assertEquals(PermissionLevel.READ_ONLY, request.level());
        assertEquals("Input summary", request.inputSummary());
        assertEquals("Risk explanation", request.riskExplanation());
        assertNotNull(request.createdAt());
    }

    @Test
    void testPermissionRequestIdFormat() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.READ_ONLY,
                "Input",
                "Risk"
        );

        assertTrue(request.id().startsWith("perm_"));
    }

    @Test
    void testPermissionRequestIdUniqueness() throws InterruptedException {
        PermissionRequest request1 = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.READ_ONLY,
                "Input",
                "Risk"
        );

        Thread.sleep(2);

        PermissionRequest request2 = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.READ_ONLY,
                "Input",
                "Risk"
        );

        assertNotEquals(request1.id(), request2.id());
    }

    @Test
    void testPermissionRequestGetLevelIcon() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.READ_ONLY,
                "Input",
                "Risk"
        );

        assertEquals("📖", request.getLevelIcon());
    }

    @Test
    void testPermissionRequestGetLevelIcon_MODIFY() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.MODIFY_STATE,
                "Input",
                "Risk"
        );

        assertEquals("✏️", request.getLevelIcon());
    }

    @Test
    void testPermissionRequestGetLevelIcon_NETWORK() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.NETWORK,
                "Input",
                "Risk"
        );

        assertEquals("🌐", request.getLevelIcon());
    }

    @Test
    void testPermissionRequestGetLevelIcon_DESTRUCTIVE() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.DESTRUCTIVE,
                "Input",
                "Risk"
        );

        assertEquals("⚠️", request.getLevelIcon());
    }

    @Test
    void testPermissionRequestGetLevelIcon_AGENT() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.AGENT_SPAWN,
                "Input",
                "Risk"
        );

        assertEquals("🤖", request.getLevelIcon());
    }

    @Test
    void testPermissionRequestGetLevelLabel() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.READ_ONLY,
                "Input",
                "Risk"
        );

        assertEquals("只读", request.getLevelLabel());
    }

    @Test
    void testPermissionRequestGetLevelLabel_MODIFY() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.MODIFY_STATE,
                "Input",
                "Risk"
        );

        assertEquals("修改状态", request.getLevelLabel());
    }

    @Test
    void testPermissionRequestGetLevelLabel_NETWORK() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.NETWORK,
                "Input",
                "Risk"
        );

        assertEquals("网络访问", request.getLevelLabel());
    }

    @Test
    void testPermissionRequestGetLevelLabel_DESTRUCTIVE() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.DESTRUCTIVE,
                "Input",
                "Risk"
        );

        assertEquals("破坏性", request.getLevelLabel());
    }

    @Test
    void testPermissionRequestGetLevelLabel_AGENT() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.AGENT_SPAWN,
                "Input",
                "Risk"
        );

        assertEquals("子代理", request.getLevelLabel());
    }

    @Test
    void testPermissionRequestCreatedAt() {
        Instant before = Instant.now();

        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.READ_ONLY,
                "Input",
                "Risk"
        );

        Instant after = Instant.now();

        assertTrue(request.createdAt().isAfter(before) || request.createdAt().equals(before));
        assertTrue(request.createdAt().isBefore(after) || request.createdAt().equals(after));
    }

    @Test
    void testPermissionRequestWithAllLevels() {
        for (PermissionLevel level : PermissionLevel.values()) {
            PermissionRequest request = PermissionRequest.create(
                    "test_tool",
                    "Test",
                    level,
                    "Input",
                    "Risk"
            );

            assertNotNull(request);
            assertEquals(level, request.level());
            assertNotNull(request.getLevelIcon());
            assertNotNull(request.getLevelLabel());
        }
    }
}
