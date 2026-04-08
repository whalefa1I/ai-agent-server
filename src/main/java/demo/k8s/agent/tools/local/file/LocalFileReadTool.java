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

    /**
     * FileRead 工具提示词（与 Claude Code 对齐）
     */
    private static final String FILE_READ_PROMPT = """
            Reads a file from the local filesystem. You can access any file directly by using this tool.
            Assume this tool is able to read all files on the machine. If the User provides a path to a file assume that path is valid. It is okay to read a file that does not exist; an error will be returned.

            Usage:
            - The file_path parameter must be an absolute path, not a relative path
            - By default, it reads up to 2000 lines starting from the beginning of the file
            - You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters
            - Results are returned using cat -n format, with line numbers starting at 1
            - This tool can read image files (eg PNG, JPG, etc). When reading an image file the contents are presented visually as the AI is a multimodal LLM.
            - This tool can read PDF files (.pdf). For large PDFs (more than 10 pages), you MUST provide the pages parameter to read specific page ranges (e.g., pages: "1-5"). Maximum 20 pages per request.
            - This tool can read Jupyter notebooks (.ipynb files) and returns all cells with their outputs, combining code, text, and visualizations.
            - This tool can only read files, not directories. To read a directory, use an ls command via the bash tool.
            - If you read a file that exists but has empty contents you will receive a system reminder warning in place of file contents.
            """;

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"file_path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"The absolute path to the file to read\"" +
            "    }," +
            "    \"offset\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"The line number to start reading from. Only provide if the file is too large to read at once\"" +
            "    }," +
            "    \"limit\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"The number of lines to read. Only provide if the file is too large to read at once\"" +
            "    }," +
            "    \"pages\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Page range for PDF files (e.g., \\\"1-5\\\", \\\"3\\\", \\\"10-20\\\")\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"file_path\"]" +
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
            String filePath = FileToolArgs.readFilePath(input);
            int offset = getInt(input, "offset", 0);
            int limit = getInt(input, "limit", Integer.MAX_VALUE);
            String pages = (String) input.get("pages");

            if (filePath == null || filePath.isEmpty()) {
                return LocalToolResult.error("file_path is required");
            }

            WorkspacePathPolicy.ResolvedPath rp = WorkspacePathPolicy.resolveToWorkspace(filePath);
            if (!rp.ok()) {
                return LocalToolResult.error("Invalid file_path: " + rp.error());
            }
            String resolvedPath = rp.resolved();

            Path path = Paths.get(resolvedPath);
            if (!Files.exists(path)) {
                return LocalToolResult.error("File does not exist: " + filePath
                        + " (resolved=" + resolvedPath + ", workspaceRoot=" + rp.workspaceRoot() + ")");
            }

            if (Files.isDirectory(path)) {
                return LocalToolResult.error("Path is a directory, not a file: " + filePath);
            }

            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return LocalToolResult.error("File too large to read: " + fileSize + " bytes");
            }

            Charset charset = StandardCharsets.UTF_8;

            List<String> allLines = Files.readAllLines(path, charset);
            int end = Math.min(allLines.size(), offset + limit);
            List<String> lines;
            if (offset > 0 || end < allLines.size()) {
                lines = allLines.subList(Math.max(0, offset), end);
            } else {
                lines = allLines;
            }

            StringBuilder content = new StringBuilder();
            content.append("File: ").append(resolvedPath).append(" (").append(allLines.size()).append(" lines)\n");
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
