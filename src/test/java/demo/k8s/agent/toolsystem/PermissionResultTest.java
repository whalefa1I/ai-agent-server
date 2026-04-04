package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionResult 测试
 */
class PermissionResultTest {

    @Test
    void testAllowCreation() {
        PermissionResult.Allow allow = PermissionResult.allow();

        assertNotNull(allow);
        assertEquals("{}", allow.updatedInputJson());
        assertTrue(allow.isAllowed());
        assertFalse(allow.isDenied());
        assertFalse(allow.needsConfirmation());
    }

    @Test
    void testAllowCreationWithUpdatedInput() {
        PermissionResult.Allow allow = PermissionResult.allow("{\"key\": \"value\"}");

        assertNotNull(allow);
        assertEquals("{\"key\": \"value\"}", allow.updatedInputJson());
        assertEquals("{\"key\": \"value\"}", allow.getUpdatedInput());
    }

    @Test
    void testDenyCreation() {
        PermissionResult.Deny deny = PermissionResult.deny("Test reason");

        assertNotNull(deny);
        assertEquals("Test reason", deny.reason());
        assertEquals(PermissionLevel.DESTRUCTIVE, deny.level());
        assertFalse(deny.isAllowed());
        assertTrue(deny.isDenied());
        assertFalse(deny.needsConfirmation());
    }

    @Test
    void testDenyCreationWithLevel() {
        PermissionResult.Deny deny = PermissionResult.deny("Test reason", PermissionLevel.NETWORK);

        assertNotNull(deny);
        assertEquals("Test reason", deny.reason());
        assertEquals(PermissionLevel.NETWORK, deny.level());
        assertEquals("Test reason", deny.getDenyReason());
    }

    @Test
    void testNeedsConfirmationCreation() {
        PermissionRequest request = PermissionRequest.create(
                "test_tool",
                "Test",
                PermissionLevel.READ_ONLY,
                "Input",
                "Risk"
        );

        PermissionResult.NeedsConfirmation needsConfirmation = PermissionResult.needsConfirmation(request);

        assertNotNull(needsConfirmation);
        assertEquals(request, needsConfirmation.request());
        assertEquals(request, needsConfirmation.getPermissionRequest());
        assertFalse(needsConfirmation.isAllowed());
        assertFalse(needsConfirmation.isDenied());
        assertTrue(needsConfirmation.needsConfirmation());
    }

    @Test
    void testIsAllowed() {
        PermissionResult allow = PermissionResult.allow();
        PermissionResult deny = PermissionResult.deny("reason");
        PermissionRequest request = PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk");
        PermissionResult needsConfirmation = PermissionResult.needsConfirmation(request);

        assertTrue(allow.isAllowed());
        assertFalse(deny.isAllowed());
        assertFalse(needsConfirmation.isAllowed());
    }

    @Test
    void testIsDenied() {
        PermissionResult allow = PermissionResult.allow();
        PermissionResult deny = PermissionResult.deny("reason");
        PermissionRequest request = PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk");
        PermissionResult needsConfirmation = PermissionResult.needsConfirmation(request);

        assertFalse(allow.isDenied());
        assertTrue(deny.isDenied());
        assertFalse(needsConfirmation.isDenied());
    }

    @Test
    void testNeedsConfirmation() {
        PermissionResult allow = PermissionResult.allow();
        PermissionResult deny = PermissionResult.deny("reason");
        PermissionRequest request = PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk");
        PermissionResult needsConfirmation = PermissionResult.needsConfirmation(request);

        assertFalse(allow.needsConfirmation());
        assertFalse(deny.needsConfirmation());
        assertTrue(needsConfirmation.needsConfirmation());
    }

    @Test
    void testGetUpdatedInput() {
        PermissionResult allow = PermissionResult.allow("{\"key\": \"value\"}");
        PermissionResult deny = PermissionResult.deny("reason");
        PermissionRequest request = PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk");
        PermissionResult needsConfirmation = PermissionResult.needsConfirmation(request);

        assertEquals("{\"key\": \"value\"}", allow.getUpdatedInput());
        assertNull(deny.getUpdatedInput());
        assertNull(needsConfirmation.getUpdatedInput());
    }

    @Test
    void testGetDenyReason() {
        PermissionResult allow = PermissionResult.allow();
        PermissionResult deny = PermissionResult.deny("test reason");
        PermissionRequest request = PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk");
        PermissionResult needsConfirmation = PermissionResult.needsConfirmation(request);

        assertNull(allow.getDenyReason());
        assertEquals("test reason", deny.getDenyReason());
        assertNull(needsConfirmation.getDenyReason());
    }

    @Test
    void testGetPermissionRequest() {
        PermissionResult allow = PermissionResult.allow();
        PermissionResult deny = PermissionResult.deny("reason");
        PermissionRequest request = PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk");
        PermissionResult needsConfirmation = PermissionResult.needsConfirmation(request);

        assertNull(allow.getPermissionRequest());
        assertNull(deny.getPermissionRequest());
        assertEquals(request, needsConfirmation.getPermissionRequest());
    }

    @Test
    void testAllowRecordAccessors() {
        PermissionResult.Allow allow = new PermissionResult.Allow("{\"test\": true}");

        assertEquals("{\"test\": true}", allow.updatedInputJson());
    }

    @Test
    void testDenyRecordAccessors() {
        PermissionResult.Deny deny = new PermissionResult.Deny("reason", PermissionLevel.MODIFY_STATE);

        assertEquals("reason", deny.reason());
        assertEquals(PermissionLevel.MODIFY_STATE, deny.level());
    }

    @Test
    void testNeedsConfirmationRecordAccessors() {
        PermissionRequest request = PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk");
        PermissionResult.NeedsConfirmation nc = new PermissionResult.NeedsConfirmation(request);

        assertEquals(request, nc.request());
    }

    @Test
    void testSealedInterfaceImplementation() {
        // 验证只有三种实现类型
        PermissionResult[] results = {
                PermissionResult.allow(),
                PermissionResult.deny("reason"),
                PermissionResult.needsConfirmation(
                        PermissionRequest.create("tool", "desc", PermissionLevel.READ_ONLY, "input", "risk")
                )
        };

        for (PermissionResult result : results) {
            assertTrue(result instanceof PermissionResult.Allow ||
                       result instanceof PermissionResult.Deny ||
                       result instanceof PermissionResult.NeedsConfirmation);
        }
    }
}
