package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本地文件编辑工具 - 使用字符串替换方式编辑文件。
 * 与 claude-code 的 FileEditTool 协议保持一致。
 */
public class LocalFileEditTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileEditTool.class);

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"file_path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"The absolute path to the file to modify\"" +
            "    }," +
            "    \"old_string\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"The text to replace\"" +
            "    }," +
            "    \"new_string\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"The text to replace it with (must be different from old_string)\"" +
            "    }," +
            "    \"replace_all\": {" +
            "      \"type\": \"boolean\"," +
            "      \"description\": \"Replace all occurrences of old_string (default false)\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"file_path\", \"old_string\", \"new_string\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_edit",
                        ToolCategory.FILE_SYSTEM,
                        "Edit a file by replacing old_string with new_string. Use replace_all to replace all occurrences.",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();

        try {
            String filePath = (String) input.get("file_path");
            if (filePath == null || filePath.isEmpty()) {
                return LocalToolResult.error("file_path is required");
            }

            String oldString = (String) input.get("old_string");
            if (oldString == null || oldString.isEmpty()) {
                return LocalToolResult.error("old_string is required");
            }

            String newString = (String) input.get("new_string");
            if (newString == null) {
                return LocalToolResult.error("new_string is required");
            }

            boolean replaceAll = getBoolean(input, "replace_all", false);

            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return LocalToolResult.error("File does not exist: " + filePath);
            }

            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return LocalToolResult.error("File too large to edit: " + fileSize + " bytes");
            }

            // 读取文件内容
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String content = String.join("\n", lines);
            if (!content.endsWith("\n")) {
                content += "\n";
            }

            // 查找匹配位置
            int matchIndex = content.indexOf(oldString);
            if (matchIndex == -1) {
                return LocalToolResult.error("old_string not found in file");
            }

            // 计算行号
            int lineNumber = countLines(content, matchIndex);

            // 执行替换
            String newContent;
            if (replaceAll) {
                newContent = content.replace(Pattern.quote(oldString), newString);
            } else {
                newContent = content.replaceFirst(Pattern.quote(oldString), newString);
            }

            // 原子写入
            writeAtomically(path, newContent);

            // 生成输出
            StringBuilder output = new StringBuilder();
            output.append("The file ").append(path.toAbsolutePath()).append(" has been updated successfully.\n");
            output.append("  - ").append(truncate(oldString, 100)).append("\n");
            output.append("  + ").append(truncate(newString, 100)).append("\n");

            return LocalToolResult.builder()
                    .success(true)
                    .content(output.toString())
                    .executionLocation("local")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("File edit execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 计算行号
     */
    private static int countLines(String content, int charIndex) {
        int lineNumber = 1;
        for (int i = 0; i < charIndex && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    /**
     * 原子写入文件
     */
    private static void writeAtomically(Path path, String content) throws IOException {
        Path tempFile = Files.createTempFile(path.getParent(), ".tmp-", ".txt");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    /**
     * 截断字符串
     */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    /**
     * 获取布尔参数
     */
    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return defaultValue;
    }
}
