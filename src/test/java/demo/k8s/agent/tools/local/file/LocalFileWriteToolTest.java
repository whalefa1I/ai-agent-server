package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalFileWriteTool 单元测试
 */
@DisplayName("LocalFileWriteTool 测试")
class LocalFileWriteToolTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("创建新文件")
    void testCreateNewFile() throws Exception {
        Path testFile = tempDir.resolve("new.txt");

        LocalToolResult result = LocalFileWriteTool.execute(Map.of(
                "path", testFile.toString(),
                "content", "Hello, World!"
        ));

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(testFile));
        assertEquals("Hello, World!", Files.readString(testFile));
    }

    @Test
    @DisplayName("覆盖已存在文件")
    void testOverwriteExistingFile() throws Exception {
        Path testFile = tempDir.resolve("existing.txt");
        Files.writeString(testFile, "Old content");

        LocalToolResult result = LocalFileWriteTool.execute(Map.of(
                "path", testFile.toString(),
                "content", "New content"
        ));

        assertTrue(result.isSuccess());
        assertEquals("New content", Files.readString(testFile));
    }

    @Test
    @DisplayName("创建带目录的文件")
    void testCreateFileInSubDirectory() throws Exception {
        Path subDir = tempDir.resolve("subdir");
        Path testFile = subDir.resolve("test.txt");

        LocalToolResult result = LocalFileWriteTool.execute(Map.of(
                "path", testFile.toString(),
                "content", "Content"
        ));

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(testFile));
    }

    @Test
    @DisplayName("写入空内容")
    void testWriteEmptyContent() throws Exception {
        Path testFile = tempDir.resolve("empty.txt");

        LocalToolResult result = LocalFileWriteTool.execute(Map.of(
                "path", testFile.toString(),
                "content", ""
        ));

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(testFile));
    }

    @Test
    @DisplayName("写入 UTF-8 内容")
    void testWriteUtf8Content() throws Exception {
        Path testFile = tempDir.resolve("utf8.txt");

        LocalToolResult result = LocalFileWriteTool.execute(Map.of(
                "path", testFile.toString(),
                "content", "你好，世界！"
        ));

        assertTrue(result.isSuccess());
        assertEquals("你好，世界！", Files.readString(testFile));
    }

    @Test
    @DisplayName("写入多行内容")
    void testWriteMultilineContent() throws Exception {
        Path testFile = tempDir.resolve("multiline.txt");
        String content = "Line 1\nLine 2\nLine 3\nLine 4";

        LocalToolResult result = LocalFileWriteTool.execute(Map.of(
                "path", testFile.toString(),
                "content", content
        ));

        assertTrue(result.isSuccess());
        assertEquals(content, Files.readString(testFile));
    }
}
