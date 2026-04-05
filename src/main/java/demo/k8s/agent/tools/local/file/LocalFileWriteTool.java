package demo.k8s.agent.tools.local.file;

import com.fasterxml.jackson.databind.JsonNode;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
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
            "    \"file_path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"The absolute path to the file to write (must be absolute, not relative)\"" +
            "    }," +
            "    \"content\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"The content to write to the file\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"file_path\", \"content\"]" +
            "}";

    /**
     * 创建工具定义（带权限检查）
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_write",
                        ToolCategory.FILE_SYSTEM,
                        "Create a new file or overwrite an existing file",
                        INPUT_SCHEMA,
                        null,
                        false),
                // 权限检查器
                LocalFileWriteTool::checkPermissions,
                // 输入验证器
                LocalFileWriteTool::validateInput);
    }

    /**
     * 权限检查方法 - 与 Claude Code 的 checkWritePermissionForTool 对齐
     */
    public static PermissionResult checkPermissions(String argumentsJson, ToolPermissionContext ctx) {
        try {
            JsonNode input = new com.fasterxml.jackson.databind.ObjectMapper().readTree(argumentsJson);
            String filePath = input.has("file_path") ? input.get("file_path").asText("") : "";

            if (filePath.isBlank()) {
                return PermissionResult.deny("file_path is required");
            }

            // 1. 检查路径约束
            if (!isPathAllowed(filePath)) {
                return PermissionResult.deny("File path is not allowed: " + filePath, PermissionLevel.MODIFY_STATE);
            }

            // 2. 检查是否在敏感目录
            if (isSensitivePath(filePath)) {
                return PermissionResult.deny("Cannot write to sensitive system file: " + filePath, PermissionLevel.DESTRUCTIVE);
            }

            // 3. 允许（需要用户确认，除非有规则匹配）
            return PermissionResult.allow();

        } catch (Exception e) {
            log.error("FileWrite permission check failed", e);
            return PermissionResult.deny("Permission check error: " + e.getMessage());
        }
    }

    /**
     * 输入验证方法
     */
    public static String validateInput(JsonNode input) {
        String filePath = input.has("file_path") ? input.get("file_path").asText("") : "";
        String content = input.has("content") ? input.get("content").asText("") : "";

        if (filePath.isBlank()) {
            return "file_path is required";
        }

        if (content.isBlank()) {
            return "content is required";
        }

        return null; // 通过验证
    }

    /**
     * 检查路径是否在允许范围内
     */
    private static boolean isPathAllowed(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        // 必须是绝对路径
        if (!path.startsWith("/") && !path.matches("^[A-Za-z]:\\\\.*")) {
            return false;
        }

        // 不允许在项目目录外
        String cwd = System.getProperty("user.dir");
        return path.startsWith(cwd);
    }

    /**
     * 检查是否为敏感路径
     */
    private static boolean isSensitivePath(String path) {
        String lower = path.toLowerCase();

        // 系统敏感路径
        String[] sensitivePatterns = {
            "/etc/", "/usr/bin/", "/usr/sbin/", "/bin/", "/sbin/",
            "c:\\\\windows\\\\", "c:\\\\system32\\\\",
            "/.env", ".env.", "/.git/",
            "/node_modules/", "/__pycache__/"
        };

        for (String pattern : sensitivePatterns) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();

        try {
            String filePath = (String) input.get("file_path");
            String content = (String) input.get("content");

            if (filePath == null || filePath.isEmpty()) {
                return LocalToolResult.error("file_path is required");
            }

            if (content == null) {
                return LocalToolResult.error("content is required");
            }

            Path path = Paths.get(filePath);
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Charset charset = StandardCharsets.UTF_8;

            // 原子写入：先写 temp 文件，再 move
            // 注意：path.getParent() 可能为 null（当路径没有父目录时），需要使用当前目录
            Path parentDir = path.getParent();
            if (parentDir == null) {
                parentDir = Paths.get(System.getProperty("java.io.tmpdir"));
            }
            Path tempFile = Files.createTempFile(parentDir, ".tmp-", ".txt");
            try {
                Files.writeString(tempFile, content, charset);
                // 先尝试原子移动（同目录时有效）
                try {
                    Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    // 跨磁盘时，使用普通复制 + 删除
                    Files.copy(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tempFile);
                }
            } catch (IOException e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }

            boolean overwritten = Files.exists(path) && Files.size(path) > 0;
            StringBuilder result = new StringBuilder();
            result.append("Successfully wrote ").append(content.length()).append(" bytes to ").append(filePath);
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
