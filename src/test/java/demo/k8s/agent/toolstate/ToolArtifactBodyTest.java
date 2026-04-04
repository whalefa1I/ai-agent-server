package demo.k8s.agent.toolstate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolArtifactBody 测试
 */
@DisplayName("ToolArtifactBody 测试")
class ToolArtifactBodyTest {

    private ToolArtifactBody body;

    @BeforeEach
    void setUp() {
        body = new ToolArtifactBody();
    }

    @Test
    @DisplayName("测试 Todo 字段")
    void testTodo() {
        body.setTodo("Execute command");
        assertEquals("Execute command", body.getTodo());
    }

    @Test
    @DisplayName("测试 Plan 字段")
    void testPlan() {
        body.setPlan(List.of("Step 1", "Step 2"));
        assertEquals(2, body.getPlan().size());
        assertEquals("Step 1", body.getPlan().get(0));
    }

    @Test
    @DisplayName("测试 Input 字段")
    void testInput() {
        Map<String, Object> input = Map.of("command", "ls -la");
        body.setInput(input);
        assertEquals("ls -la", body.getInput().get("command"));
    }

    @Test
    @DisplayName("测试 Output 字段")
    void testOutput() {
        Map<String, Object> output = Map.of("stdout", "file1.txt");
        body.setOutput(output);
        assertNotNull(body.getOutput());
    }

    @Test
    @DisplayName("测试 Error 字段")
    void testError() {
        body.setError("Command not found");
        assertEquals("Command not found", body.getError());
    }

    @Test
    @DisplayName("测试 Progress 字段")
    void testProgress() {
        body.setProgress("Running...");
        assertEquals("Running...", body.getProgress());
    }

    @Test
    @DisplayName("测试 Confirmation 字段")
    void testConfirmation() {
        ToolArtifactBody.Confirmation confirmation = new ToolArtifactBody.Confirmation();
        confirmation.setRequested(true);
        confirmation.setGranted(true);
        body.setConfirmation(confirmation);
        assertTrue(body.getConfirmation().isRequested());
        assertTrue(body.getConfirmation().isGranted());
    }

    @Test
    @DisplayName("测试 Version 字段")
    void testVersion() {
        body.setVersion(5);
        assertEquals(5, body.getVersion());
    }
}
