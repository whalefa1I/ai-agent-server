package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * 本地文件复制工具
 * <p>
 * 功能：
 * - 复制文件
 * - 复制目录（递归）
 * - 原子操作保证
 */
public class LocalFileCopyTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileCopyTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"source\": {\"type\": \"string\", \"description\": \"Source file/directory path\"}," +
            "    \"destination\": {\"type\": \"string\", \"description\": \"Destination file/directory path\"}," +
            "    \"recursive\": {\"type\": \"boolean\", \"description\": \"Recursively copy directories (default false)\"}," +
            "    \"overwrite\": {\"type\": \"boolean\", \"description\": \"Overwrite existing files (default false)\"}" +
            "  }," +
            "  \"required\": [\"source\", \"destination\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "local_file_copy",
                        ToolCategory.FILE_SYSTEM,
                        "Copy files or directories with optional recursive copy",
                        INPUT_SCHEMA,
                        null,
                        false)); // 不是只读
    }

    /**
     * 执行文件复制
     *
     * @param input 输入参数
     *              - source: 源文件路径
     *              - destination: 目标文件路径
     *              - recursive: 是否递归复制目录（默认 false）
     *              - overwrite: 是否覆盖已存在的文件（默认 false）
     * @return 复制结果
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String sourceStr = (String) input.get("source");
        String destStr = (String) input.get("destination");
        Boolean recursive = (Boolean) input.getOrDefault("recursive", false);
        Boolean overwrite = (Boolean) input.getOrDefault("overwrite", false);

        if (sourceStr == null || sourceStr.isBlank()) {
            return LocalToolResult.error("source is required");
        }
        if (destStr == null || destStr.isBlank()) {
            return LocalToolResult.error("destination is required");
        }

        Path source = Paths.get(sourceStr);
        Path destination = Paths.get(destStr);

        try {
            if (!Files.exists(source)) {
                return LocalToolResult.error("Source does not exist: " + source);
            }

            if (Files.isDirectory(source) && !recursive) {
                return LocalToolResult.error("Source is a directory. Use recursive=true to copy directories.");
            }

            // 确保目标父目录存在
            Path parent = destination.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.isDirectory(source)) {
                copyDirectory(source, destination, overwrite);
            } else {
                copyFile(source, destination, overwrite);
            }

            return LocalToolResult.success("Copied: " + source + " -> " + destination, Map.of(
                    "source", source.toAbsolutePath().toString(),
                    "destination", destination.toAbsolutePath().toString(),
                    "isDirectory", Files.isDirectory(source)
            ));

        } catch (FileAlreadyExistsException e) {
            return LocalToolResult.error("Destination already exists. Use overwrite=true to force.");
        } catch (IOException e) {
            log.error("复制失败：{} -> {}", source, destination, e);
            return LocalToolResult.error("Failed to copy: " + e.getMessage());
        }
    }

    private static void copyFile(Path source, Path destination, boolean overwrite) throws IOException {
        if (overwrite) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        }
        log.info("复制文件：{} -> {}", source, destination);
    }

    private static void copyDirectory(Path source, Path destination, boolean overwrite) throws IOException {
        if (!Files.exists(destination)) {
            Files.createDirectories(destination);
        }

        try (var stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                Path relativePath = source.relativize(sourcePath);
                Path destPath = destination.resolve(relativePath);

                try {
                    if (Files.isDirectory(sourcePath)) {
                        if (!Files.exists(destPath)) {
                            Files.createDirectories(destPath);
                        }
                    } else {
                        // 确保父目录存在
                        Path parent = destPath.getParent();
                        if (parent != null && !Files.exists(parent)) {
                            Files.createDirectories(parent);
                        }

                        if (overwrite) {
                            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        } else {
                            Files.copy(sourcePath, destPath, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                        log.debug("复制文件：{} -> {}", sourcePath, destPath);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + sourcePath + " -> " + destPath, e);
                }
            });
        }
        log.info("复制目录：{} -> {}", source, destination);
    }
}
