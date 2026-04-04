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
 * 本地文件移动/重命名工具
 * <p>
 * 功能：
 * - 移动文件
 * - 移动目录
 * - 重命名文件/目录
 * - 原子操作保证
 */
public class LocalFileMoveTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileMoveTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"source\": {\"type\": \"string\", \"description\": \"Source file/directory path\"}," +
            "    \"destination\": {\"type\": \"string\", \"description\": \"Destination file/directory path\"}," +
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
                        "local_file_move",
                        ToolCategory.FILE_SYSTEM,
                        "Move or rename files and directories with atomic guarantee",
                        INPUT_SCHEMA,
                        null,
                        false)); // 不是只读
    }

    /**
     * 执行文件移动/重命名
     *
     * @param input 输入参数
     *              - source: 源文件路径
     *              - destination: 目标文件路径
     *              - overwrite: 是否覆盖已存在的文件（默认 false）
     * @return 移动结果
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String sourceStr = (String) input.get("source");
        String destStr = (String) input.get("destination");
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

            // 确保目标父目录存在
            Path parent = destination.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (overwrite) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            }

            log.info("移动文件：{} -> {}", source, destination);
            return LocalToolResult.success("Moved: " + source + " -> " + destination, Map.of(
                    "source", source.toAbsolutePath().toString(),
                    "destination", destination.toAbsolutePath().toString(),
                    "operation", Files.isDirectory(destination) ? "rename_directory" : "rename_file"
            ));

        } catch (FileAlreadyExistsException e) {
            return LocalToolResult.error("Destination already exists. Use overwrite=true to force.");
        } catch (IOException e) {
            // 原子移动失败时，尝试非原子移动
            try {
                if (overwrite) {
                    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(source, destination);
                }
                log.info("移动文件（非原子）：{} -> {}", source, destination);
                return LocalToolResult.success("Moved (non-atomic): " + source + " -> " + destination, Map.of(
                        "source", source.toAbsolutePath().toString(),
                        "destination", destination.toAbsolutePath().toString(),
                        "atomic", false
                ));
            } catch (IOException e2) {
                log.error("移动失败：{} -> {}", source, destination, e2);
                return LocalToolResult.error("Failed to move: " + e2.getMessage());
            }
        }
    }
}
