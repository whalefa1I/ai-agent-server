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
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * 本地文件写入工具 - 创建或覆盖文件。
 */
public class LocalFileWriteTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileWriteTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Path to the file to write\"" +
            "    }," +
            "    \"content\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Content to write to the file\"" +
            "    }," +
            "    \"encoding\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"File encoding, defaults to UTF-8\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"path\", \"content\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_write",
                        ToolCategory.FILE_SYSTEM,
                        "Create a new file or overwrite an existing file",
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
            String pathStr = (String) input.get("path");
            String content = (String) input.get("content");
            String encoding = (String) input.getOrDefault("encoding", "UTF-8");

            if (pathStr == null || pathStr.isEmpty()) {
                return LocalToolResult.error("path is required");
            }

            if (content == null) {
                return LocalToolResult.error("content is required");
            }

            Path path = Paths.get(pathStr);
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Charset charset;
            try {
                charset = Charset.forName(encoding);
            } catch (Exception e) {
                charset = StandardCharsets.UTF_8;
            }

            // 原子写入：先写 temp 文件，再 move
            Path tempFile = Files.createTempFile(path.getParent(), ".tmp-", ".txt");
            try {
                Files.writeString(tempFile, content, charset);
                Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }

            boolean overwritten = Files.exists(path) && Files.size(path) > 0;
            StringBuilder result = new StringBuilder();
            result.append("Successfully wrote ").append(content.length()).append(" bytes to ").append(pathStr);
            if (overwritten) {
                result.append(" (overwritten)");
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(result.toString())
                    .executionLocation("local")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("File write execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }
}
