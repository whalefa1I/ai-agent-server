package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 本地文件删除工具
 * <p>
 * 功能：
 * - 删除文件
 * - 删除目录（递归）
 */
public class LocalFileDeleteTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileDeleteTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"path\": {\"type\": \"string\", \"description\": \"File or directory path to delete\"}," +
            "    \"recursive\": {\"type\": \"boolean\", \"description\": \"Recursively delete directories (default false)\"}" +
            "  }," +
            "  \"required\": [\"path\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "FileDelete",
                        ToolCategory.FILE_SYSTEM,
                        "Delete a file or directory",
                        INPUT_SCHEMA,
                        null,
                        false)); // 不是只读
    }

    /**
     * 执行文件删除
     *
     * @param input 输入参数
     *              - path: 要删除的文件或目录路径
     *              - recursive: 是否递归删除目录（默认 false）
     * @return 删除结果
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String pathStr = FilesystemPathArgs.readPathOrAlias(input);
        Boolean recursive = (Boolean) input.getOrDefault("recursive", false);

        if (pathStr == null || pathStr.isBlank()) {
            return LocalToolResult.error("path is required");
        }

        Path path = Paths.get(pathStr);

        try {
            if (!Files.exists(path)) {
                return LocalToolResult.error("Path does not exist: " + path);
            }

            boolean isDirectory = Files.isDirectory(path);

            if (isDirectory && !recursive) {
                return LocalToolResult.error("Path is a directory. Use recursive=true to delete directories.");
            }

            if (isDirectory) {
                deleteDirectory(path);
            } else {
                Files.delete(path);
            }

            log.info("删除{}: {}", isDirectory ? "目录" : "文件", path);

            return LocalToolResult.success(
                    "Deleted: " + path,
                    Map.of(
                            "path", path.toAbsolutePath().toString(),
                            "wasDirectory", isDirectory
                    )
            );

        } catch (IOException e) {
            log.error("删除失败：{}", path, e);
            return LocalToolResult.error("Failed to delete: " + e.getMessage());
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a)) // 反向排序，先删除子文件
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
        }
    }
}
