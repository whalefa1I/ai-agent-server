package demo.k8s.agent.tools.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalToolResult 单元测试
 */
class LocalToolResultTest {

    @Test
    void testSuccessWithContent() {
        LocalToolResult result = LocalToolResult.success("Operation completed successfully");

        assertTrue(result.isSuccess());
        assertEquals("Operation completed successfully", result.getContent());
        assertNull(result.getError());
        assertEquals("local", result.getExecutionLocation());
    }

    @Test
    void testSuccessWithMetadata() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("file", "test.txt");
        metadata.put("size", 1024);

        LocalToolResult result = LocalToolResult.success("File created", metadata);

        assertTrue(result.isSuccess());
        assertEquals("File created", result.getContent());
        assertNotNull(result.getMetadata());
        assertEquals("test.txt", result.getMetadata().get("file").asText());
    }

    @Test
    void testError() {
        LocalToolResult result = LocalToolResult.error("Something went wrong");

        assertFalse(result.isSuccess());
        assertEquals("Something went wrong", result.getError());
        assertNull(result.getContent());
        assertEquals("local", result.getExecutionLocation());
    }

    @Test
    void testBuilder() {
        LocalToolResult result = LocalToolResult.builder()
                .success(true)
                .content("Custom result")
                .executionLocation("remote")
                .durationMs(150)
                .build();

        assertTrue(result.isSuccess());
        assertEquals("Custom result", result.getContent());
        assertEquals("remote", result.getExecutionLocation());
        assertEquals(150, result.getDurationMs());
    }

    @Test
    void testSetters() {
        LocalToolResult result = new LocalToolResult();
        result.setSuccess(true);
        result.setContent("Modified content");
        result.setError(null);
        result.setExecutionLocation("local");
        result.setDurationMs(200);

        assertTrue(result.isSuccess());
        assertEquals("Modified content", result.getContent());
        assertEquals(200, result.getDurationMs());
    }

    @Test
    void testMetadataWithPojo() {
        TestMetadata pojo = new TestMetadata();
        pojo.name = "Test";
        pojo.value = 42;

        LocalToolResult result = LocalToolResult.success("Success", pojo);

        assertNotNull(result.getMetadata());
        assertEquals("Test", result.getMetadata().get("name").asText());
        assertEquals(42, result.getMetadata().get("value").asInt());
    }

    static class TestMetadata {
        public String name;
        public int value;
    }
}
