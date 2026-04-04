package demo.k8s.agent.tools.local.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalFileReadToolTest {

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("read-test-", ".txt");
        String content = """
            Line 1: Hello World
            Line 2: This is a test
            Line 3: Third line
            Line 4: Fourth line
            Line 5: End
            """;
        Files.writeString(tempFile, content);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void readsEntireFile() throws IOException {
        Map<String, Object> input = Map.of("path", tempFile.toString());
        var result = LocalFileReadTool.execute(input);

        assertTrue(result.isSuccess(), "Should succeed: " + result.getError());
        String content = result.getContent();
        assertTrue(content.contains("Line 1"));
        assertTrue(content.contains("Line 5"));
        assertTrue(content.contains("5 lines"));
    }

    @Test
    void readsWithOffsetAndLimit() {
        Map<String, Object> input = Map.of(
            "path", tempFile.toString(),
            "offset", 1,
            "limit", 2
        );
        var result = LocalFileReadTool.execute(input);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("Line 2"));
        assertTrue(content.contains("Line 3"));
        assertFalse(content.contains("Line 1"));
        assertFalse(content.contains("Line 5"));
    }

    @Test
    void nonexistentFile() {
        Map<String, Object> input = Map.of("path", "/nonexistent/file.txt");
        var result = LocalFileReadTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("does not exist"));
    }

    @Test
    void nullPath() {
        // 使用 null 值作为路径会触发参数验证错误
        // 由于 Map.of 不支持 null 值，我们直接测试空字符串
        Map<String, Object> input = Map.of("path", "");
        var result = LocalFileReadTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("path is required") || result.getError().contains("empty"));
    }
}
