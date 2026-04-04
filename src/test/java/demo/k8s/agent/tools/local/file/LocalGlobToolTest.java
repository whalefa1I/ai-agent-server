package demo.k8s.agent.tools.local.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalGlobToolTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("glob-test");
        // 创建测试文件结构
        Files.createFile(tempDir.resolve("Test1.java"));
        Files.createFile(tempDir.resolve("Test2.java"));
        Files.createFile(tempDir.resolve("README.md"));
        Files.createDirectory(tempDir.resolve("subdir"));
        Files.createFile(tempDir.resolve("subdir").resolve("Nested.java"));
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                // ignore
            }
        });
    }

    @Test
    void matchesJavaFiles() {
        Map<String, Object> input = Map.of(
            "pattern", "**/*.java",
            "path", tempDir.toString()
        );
        var result = LocalGlobTool.execute(input);

        assertTrue(result.isSuccess(), "Should succeed");
        String content = result.getContent();
        assertTrue(content.contains("Test1.java"));
        assertTrue(content.contains("Test2.java"));
        assertTrue(content.contains("Nested.java"));
        assertFalse(content.contains("README.md"));
    }

    @Test
    void matchesMdFiles() {
        Map<String, Object> input = Map.of(
            "pattern", "**/*.md",
            "path", tempDir.toString()
        );
        var result = LocalGlobTool.execute(input);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("README.md"));
        assertFalse(result.getContent().contains(".java"));
    }

    @Test
    void emptyPattern() {
        Map<String, Object> input = Map.of("pattern", "");
        var result = LocalGlobTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("pattern is required"));
    }

    @Test
    void nonexistentPath() {
        Map<String, Object> input = Map.of(
            "pattern", "*.java",
            "path", "/nonexistent/path"
        );
        var result = LocalGlobTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("does not exist"));
    }
}
