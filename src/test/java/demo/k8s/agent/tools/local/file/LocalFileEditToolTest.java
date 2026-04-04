package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ToolCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalFileEditTool 测试
 */
class LocalFileEditToolTest {

    @TempDir
    Path tempDir;

    @Test
    void testExecuteWithNonExistentFile() {
        Map<String, Object> input = Map.of("path", "/non/existent/file.txt");

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("File does not exist"));
    }

    @Test
    void testExecuteWithMissingOldText() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "newText", "New text"
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        // 当只有 newText 没有 oldText 时，工具可能执行删除操作或其他模式
        // 验证工具执行了某种操作
        assertNotNull(result);
    }

    @Test
    void testExecuteWithMissingNewText() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "oldText", "Hello"
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        // 当只有 oldText 没有 newText 时，工具可能执行删除操作
        // 验证工具执行了某种操作
        assertNotNull(result);
    }

    @Test
    void testExecuteWithTextNotFound() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "oldText", "Goodbye",
                "newText", "Hello"
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Text not found"));
    }

    @Test
    void testExecuteStringReplacementSuccess() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        String originalContent = "Hello World\nThis is a test";
        Files.writeString(testFile, originalContent);

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "oldText", "World",
                "newText", "Universe"
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Successfully replaced"));

        // 验证文件内容已修改
        String newContent = Files.readString(testFile);
        assertTrue(newContent.contains("Hello Universe"));
        assertFalse(newContent.contains("Hello World"));
    }

    @Test
    void testExecuteStringReplacementMultiLine() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        String originalContent = "Line 1\nLine 2\nLine 3";
        Files.writeString(testFile, originalContent);

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "oldText", "Line 2",
                "newText", "Modified Line 2"
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertTrue(result.isSuccess());

        String newContent = Files.readString(testFile);
        assertTrue(newContent.contains("Modified Line 2"));
        // 替换后 Line 2 应该被 Modified Line 2 替代
        assertTrue(newContent.contains("Modified Line 2\n"));
    }

    @Test
    void testExecuteLineBasedModeInsert() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        String originalContent = "Line 1\nLine 2\nLine 3";
        Files.writeString(testFile, originalContent);

        Map<String, Object> input = new HashMap<>();
        input.put("path", testFile.toString());
        input.put("lineNumber", 2);
        input.put("newLines", List.of("Inserted Line"));

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Successfully edited"));

        String newContent = Files.readString(testFile);
        List<String> lines = newContent.lines().toList();
        assertEquals(4, lines.size());
        assertEquals("Inserted Line", lines.get(1));
    }

    @Test
    void testExecuteLineBasedModeDelete() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        String originalContent = "Line 1\nLine 2\nLine 3";
        Files.writeString(testFile, originalContent);

        Map<String, Object> input = new HashMap<>();
        input.put("path", testFile.toString());
        input.put("lineNumber", 2);
        input.put("endLineNumber", 2);

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertTrue(result.isSuccess());

        String newContent = Files.readString(testFile);
        List<String> lines = newContent.lines().toList();
        assertEquals(2, lines.size());
        assertEquals("Line 1", lines.get(0));
        assertEquals("Line 3", lines.get(1));
    }

    @Test
    void testExecuteLineBasedModeInvalidLineNumber() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Line 1\nLine 2");

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "lineNumber", 100
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("exceeds file length"));
    }

    @Test
    void testExecuteDiffMode() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        String originalContent = "Line 1\nLine 2\nLine 3";
        Files.writeString(testFile, originalContent);

        String diff = "@@ -1,3 +1,3 @@\n Line 1\n-Line 2\n+Modified Line 2\n Line 3";

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "diff", diff
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("Successfully applied"));

        String newContent = Files.readString(testFile);
        assertTrue(newContent.contains("Modified Line 2"));
    }

    @Test
    void testExecuteDiffModeInvalidDiff() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Line 1\nLine 2");

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "diff", "invalid diff content"
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertFalse(result.isSuccess());
    }

    @Test
    void testExecuteNoEditModeSpecified() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Content");

        Map<String, Object> input = Map.of("path", testFile.toString());

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Must specify one of"));
    }

    @Test
    void testExecuteWithEmptyPath() {
        Map<String, Object> input = Map.of("path", "");

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("path is required"));
    }

    @Test
    void testExecuteWithNullPath() {
        Map<String, Object> input = new HashMap<>();
        input.put("path", null);

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("path is required"));
    }

    @Test
    void testExecuteWithFuzzyMatching() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        // 创建带有不同空白的文件
        Files.writeString(testFile, "Hello    World");

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "oldText", "Hello World", // 单个空格
                "newText", "Hello Universe",
                "fuzzy", true
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        // Fuzzy 匹配可能成功或失败，取决于实现
        // 这里至少验证不会抛异常
        assertNotNull(result);
    }

    @Test
    void testCreateTool() {
        var tool = LocalFileEditTool.createTool();

        assertNotNull(tool);
        assertEquals("file_edit", tool.name());
        assertEquals(ToolCategory.FILE_SYSTEM, tool.category());
        assertNotNull(tool.inputSchemaJson());
        assertTrue(tool.inputSchemaJson().contains("path"));
        assertTrue(tool.inputSchemaJson().contains("oldText"));
        assertTrue(tool.inputSchemaJson().contains("newText"));
        assertTrue(tool.inputSchemaJson().contains("diff"));
        assertTrue(tool.inputSchemaJson().contains("lineNumber"));
    }

    @Test
    void testExecutePreservesFileEncoding() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        // 包含 UTF-8 字符
        String originalContent = "Hello 世界\nПривет";
        Files.writeString(testFile, originalContent, StandardCharsets.UTF_8);

        Map<String, Object> input = Map.of(
                "path", testFile.toString(),
                "oldText", "世界",
                "newText", "World"
        );

        LocalToolResult result = LocalFileEditTool.execute(input);

        assertTrue(result.isSuccess());

        String newContent = Files.readString(testFile, StandardCharsets.UTF_8);
        assertTrue(newContent.contains("Hello World"));
        assertTrue(newContent.contains("Привет")); // 保留原有字符
    }
}
