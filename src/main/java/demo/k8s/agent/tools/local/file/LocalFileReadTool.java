package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 本地文件读取工具 - 读取文件内容。
 */
public class LocalFileReadTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileReadTool.class);

    private static final int MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Path to the file to read\"" +
            "    }," +
            "    \"offset\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Starting line number (0-based), defaults to 0\"" +
            "    }," +
            "    \"limit\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Number of lines to read, defaults to all\"" +
            "    }," +
            "    \"encoding\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"File encoding, defaults to UTF-8\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"path\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_read",
                        ToolCategory.FILE_SYSTEM,
                        "Read file contents with optional range selection",
                        INPUT_SCHEMA,
                        null,
                        true));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();

        try {
            String pathStr = (String) input.get("path");
            int offset = getInt(input, "offset", 0);
            int limit = getInt(input, "limit", Integer.MAX_VALUE);
            String encoding = (String) input.getOrDefault("encoding", "UTF-8");

            if (pathStr == null || pathStr.isEmpty()) {
                return LocalToolResult.error("path is required");
            }

            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                return LocalToolResult.error("File does not exist: " + pathStr);
            }

            if (Files.isDirectory(path)) {
                return LocalToolResult.error("Path is a directory, not a file: " + pathStr);
            }

            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return LocalToolResult.error("File too large to read: " + fileSize + " bytes");
            }

            Charset charset;
            try {
                charset = Charset.forName(encoding);
            } catch (Exception e) {
                charset = StandardCharsets.UTF_8;
            }

            List<String> allLines = Files.readAllLines(path, charset);
            int end = Math.min(allLines.size(), offset + limit);
            List<String> lines;
            if (offset > 0 || end < allLines.size()) {
                lines = allLines.subList(Math.max(0, offset), end);
            } else {
                lines = allLines;
            }

            StringBuilder content = new StringBuilder();
            content.append("File: ").append(pathStr).append(" (").append(allLines.size()).append(" lines)\n");
            content.append("Lines ").append(offset).append("-").append(end - 1).append(":\n\n");
            for (String line : lines) {
                content.append(line).append("\n");
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(content.toString())
                    .executionLocation("local")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("File read execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
