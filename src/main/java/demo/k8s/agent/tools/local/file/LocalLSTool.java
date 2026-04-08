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
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.List;
import java.util.Map;

/**
 * 本地目录列表工具（LS）
 * <p>
 * 功能：列出目录内容，显示文件/子目录的详细信息
 */
public class LocalLSTool {

    private static final Logger log = LoggerFactory.getLogger(LocalLSTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"path\": {\"type\": \"string\", \"description\": \"Directory path to list\"}," +
            "    \"recursive\": {\"type\": \"boolean\", \"description\": \"List recursively (default: false)\"}" +
            "  }," +
            "  \"required\": [\"path\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "ls",
                        ToolCategory.FILE_SYSTEM,
                        "List directory contents with detailed file information",
                        INPUT_SCHEMA,
                        null,
                        true)); // 只读工具
    }

    /**
     * 列出目录内容
     *
     * @param input 输入参数
     *              - path: 目录路径
     *              - recursive: 是否递归列出（可选，默认 false）
     * @return 目录内容列表
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String pathStr = FilesystemPathArgs.readPathOrAlias(input);
        Boolean recursive = (Boolean) input.getOrDefault("recursive", false);

        if (pathStr == null || pathStr.isBlank()) {
            return LocalToolResult.error("path is required");
        }

        Path path = Paths.get(pathStr);

        if (!Files.exists(path)) {
            return LocalToolResult.error("Directory does not exist: " + path);
        }

        if (!Files.isDirectory(path)) {
            return LocalToolResult.error("Path is not a directory: " + path);
        }

        if (Boolean.TRUE.equals(recursive)) {
            return listDirectoryRecursive(path);
        } else {
            return listDirectory(path);
        }
    }

    private static LocalToolResult listDirectory(Path path) {
        try {
            List<Map<String, Object>> entries = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);

                    Map<String, Object> entryInfo = Map.of(
                            "name", entry.getFileName().toString(),
                            "path", entry.toAbsolutePath().toString(),
                            "isDirectory", Files.isDirectory(entry),
                            "isSymbolicLink", Files.isSymbolicLink(entry),
                            "size", Files.isDirectory(entry) ? 0 : attrs.size(),
                            "lastModifiedTime", attrs.lastModifiedTime().toInstant().toString(),
                            "creationTime", attrs.creationTime().toInstant().toString()
                    );
                    entries.add(entryInfo);
                }
            }

            log.info("列出目录：{} (共 {} 项)", path, entries.size());
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

    private static LocalToolResult listDirectoryRecursive(Path path) {
        try {
            List<Map<String, Object>> entries = new ArrayList<>();

            try (Stream<Path> stream = Files.walk(path)) {
                stream.forEach(entry -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);

                        Map<String, Object> entryInfo = Map.of(
                                "name", entry.getFileName().toString(),
                                "path", entry.toAbsolutePath().toString(),
                                "isDirectory", Files.isDirectory(entry),
                                "isSymbolicLink", Files.isSymbolicLink(entry),
                                "size", Files.isDirectory(entry) ? 0 : attrs.size(),
                                "lastModifiedTime", attrs.lastModifiedTime().toInstant().toString(),
                                "creationTime", attrs.creationTime().toInstant().toString()
                        );
                        entries.add(entryInfo);
                    } catch (IOException e) {
                        log.warn("无法读取文件属性：{}", entry, e);
                    }
                });
            }

            // 排除根目录本身
            entries.removeIf(e -> e.get("path").equals(path.toAbsolutePath().toString()));

            log.info("递归列出目录：{} (共 {} 项)", path, entries.size());
            return LocalToolResult.success("Listed directory recursively: " + path, Map.of(
                    "path", path.toAbsolutePath().toString(),
                    "entries", entries,
                    "count", entries.size(),
                    "recursive", true
            ));

        } catch (IOException e) {
            log.error("递归列出目录失败：{}", path, e);
            return LocalToolResult.error("Failed to list directory recursively: " + e.getMessage());
        }
    }
}
