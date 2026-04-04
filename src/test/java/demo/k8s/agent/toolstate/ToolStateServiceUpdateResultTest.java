package demo.k8s.agent.toolstate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolStateService.UpdateResult 测试
 */
@DisplayName("ToolStateService.UpdateResult 测试")
class ToolStateServiceUpdateResultTest {

    @Test
    @DisplayName("测试 success 结果")
    void testSuccess() {
        ToolArtifact artifact = new ToolArtifact();
        artifact.setId("test-id");

        ToolStateService.UpdateResult result = ToolStateService.UpdateResult.success(artifact);

        assertTrue(result.isSuccess());
        assertFalse(result.isVersionMismatch());
        assertTrue(result.getArtifact().isPresent());
        assertEquals("test-id", result.getArtifact().get().getId());
        assertFalse(result.getCurrentVersion().isPresent());
        assertFalse(result.getCurrentBody().isPresent());
    }

    @Test
    @DisplayName("测试 notFound 结果")
    void testNotFound() {
        ToolStateService.UpdateResult result = ToolStateService.UpdateResult.notFound();

        assertFalse(result.isSuccess());
        assertFalse(result.isVersionMismatch());
        assertFalse(result.getArtifact().isPresent());
        assertFalse(result.getCurrentVersion().isPresent());
        assertFalse(result.getCurrentBody().isPresent());
    }

    @Test
    @DisplayName("测试 versionMismatch 结果")
    void testVersionMismatch() {
        ToolStateService.UpdateResult result = ToolStateService.UpdateResult.versionMismatch(5, "{\"test\":\"data\"}");

        assertFalse(result.isSuccess());
        assertTrue(result.isVersionMismatch());
        assertFalse(result.getArtifact().isPresent());
        assertTrue(result.getCurrentVersion().isPresent());
        assertEquals(5, result.getCurrentVersion().get());
        assertTrue(result.getCurrentBody().isPresent());
        assertEquals("{\"test\":\"data\"}", result.getCurrentBody().get());
    }

    @Test
    @DisplayName("测试 Optional 空值处理")
    void testOptionalEmpty() {
        ToolStateService.UpdateResult result = ToolStateService.UpdateResult.success(null);

        assertTrue(result.isSuccess());
        // artifact 为 null 时，Optional 应该为空
        assertFalse(result.getArtifact().isPresent());
    }
}
