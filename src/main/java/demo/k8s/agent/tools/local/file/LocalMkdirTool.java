package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * 本地目录操作工具
 * <p>
 * 功能：
 * - 创建目录（mkdir -p 语义）
 * - 列出目录内容
 * - 删除空目录
 */
public class LocalMkdirTool {

    private static final Logger log = LoggerFactory.getLogger(LocalMkdirTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"action\": {\"type\": \"string\", \"enum\": [\"create\", \"list\", \"delete\"], \"description\": \"Action to perform\"}," +
            "    \"path\": {\"type\": \"string\", \"description\": \"Directory path\"}," +
            "    \"recursive\": {\"type\": \"boolean\", \"description\": \"Create parent directories if needed (for create action)\"}" +
            "  }," +
            "  \"required\": [\"action\", \"path\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "local_mkdir",
                        ToolCategory.FILE_SYSTEM,
                        "Create, list, or delete directories with mkdir -p semantics",
                        INPUT_SCHEMA,
                        null,
                        false)); // 不是只读，因为会修改文件系统
    }

    /**
     * 执行目录操作
     *
     * @param input 输入参数
     *              - path: 目录路径
     *              - action: "create" | "list" | "delete"
     *              - recursive: 是否递归（仅 create 动作有效）
     * @return 操作结果
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String action = (String) input.get("action");
        String pathStr = (String) input.get("path");
        Boolean recursive = (Boolean) input.getOrDefault("recursive", true);

        if (pathStr == null || pathStr.isBlank()) {
            return LocalToolResult.error("path is required");
        }

        Path path = Paths.get(pathStr);

        return switch (action != null ? action : "create") {
            case "create" -> createDirectory(path, recursive);
            case "list" -> listDirectory(path);
            case "delete" -> deleteDirectory(path);
            default -> LocalToolResult.error("Unknown action: " + action + ". Use 'create', 'list', or 'delete'");
        };
    }

    private static LocalToolResult createDirectory(Path path, boolean recursive) {
        try {
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    return LocalToolResult.success("Directory already exists: " + path, Map.of(
                            "exists", true,
                            "isDirectory", true,
                            "path", path.toAbsolutePath().toString()
                    ));
                } else {
                    return LocalToolResult.error("A file with the same name already exists: " + path);
                }
            }

            if (recursive) {
                Files.createDirectories(path);
            } else {
                Files.createDirectory(path);
            }

            log.info("创建目录：{}", path);
            return LocalToolResult.success("Directory created: " + path, Map.of(
                    "created", true,
                    "path", path.toAbsolutePath().toString(),
                    "recursive", recursive
            ));

        } catch (IOException e) {
            log.error("创建目录失败：{}", path, e);
            return LocalToolResult.error("Failed to create directory: " + e.getMessage());
        }
    }

    private static LocalToolResult listDirectory(Path path) {
        try {
            if (!Files.exists(path)) {
                return LocalToolResult.error("Directory does not exist: " + path);
            }

            if (!Files.isDirectory(path)) {
                return LocalToolResult.error("Path is not a directory: " + path);
            }

            List<Map<String, Object>> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    Map<String, Object> entryInfo = Map.of(
                            "name", entry.getFileName().toString(),
                            "path", entry.toAbsolutePath().toString(),
                            "isDirectory", Files.isDirectory(entry),
                            "size", Files.isDirectory(entry) ? 0 : Files.size(entry)
                    );
                    entries.add(entryInfo);
                }
            }

            return LocalToolResult.success("Listed directory: " + path, Map.of(
                    "path", path.toAbsolutePath().toString(),
                    "entries", entries,
                    "count", entries.size()
            ));

        } catch (IOException e) {
            log.error("列出目录失败：{}", path, e);
            return LocalToolResult.error("Failed to list directory: " + e.getMessage());
        }
    }

    private static LocalToolResult deleteDirectory(Path path) {
        try {
            if (!Files.exists(path)) {
                return LocalToolResult.error("Directory does not exist: " + path);
            }

            if (!Files.isDirectory(path)) {
                return LocalToolResult.error("Path is not a directory: " + path);
            }

            // 检查是否为空目录
            boolean isEmpty;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                isEmpty = !stream.iterator().hasNext();
            }

            if (!isEmpty) {
                return LocalToolResult.error("Directory is not empty. Use recursive delete or remove contents first.");
            }

            Files.delete(path);
            log.info("删除目录：{}", path);
            return LocalToolResult.success("Directory deleted: " + path, Map.of(
                    "deleted", true,
                    "path", path.toAbsolutePath().toString()
            ));

        } catch (IOException e) {
            log.error("删除目录失败：{}", path, e);
            return LocalToolResult.error("Failed to delete directory: " + e.getMessage());
        }
    }
}
