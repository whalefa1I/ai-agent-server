package demo.k8s.agent.tools.local.file;

import com.fasterxml.jackson.databind.JsonNode;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本地多编辑工具 - 一次性对多个文件进行编辑操作
 * <p>
 * 功能：
 * - 支持对单个文件进行多次编辑
 * - 支持对多个文件进行编辑
 * - 所有编辑操作原子执行（要么全部成功，要么全部回滚）
 */
public class LocalMultiEditTool {

    private static final Logger log = LoggerFactory.getLogger(LocalMultiEditTool.class);

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"edits\": {" +
            "      \"type\": \"array\"," +
            "      \"description\": \"Array of edit operations\"," +
            "      \"items\": {" +
            "        \"type\": \"object\"," +
            "        \"properties\": {" +
            "          \"file_path\": {" +
            "            \"type\": \"string\"," +
            "            \"description\": \"The absolute path to the file to modify\"" +
            "          }," +
            "          \"old_string\": {" +
            "            \"type\": \"string\"," +
            "            \"description\": \"The text to replace\"" +
            "          }," +
            "          \"new_string\": {" +
            "            \"type\": \"string\"," +
            "            \"description\": \"The text to replace it with\"" +
            "          }," +
            "          \"replace_all\": {" +
            "            \"type\": \"boolean\"," +
            "            \"description\": \"Replace all occurrences (default false)\"" +
            "          }" +
            "        }," +
            "        \"required\": [\"file_path\", \"old_string\", \"new_string\"]" +
            "      }" +
            "    }" +
            "  }," +
            "  \"required\": [\"edits\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "multi_edit",
                        ToolCategory.FILE_SYSTEM,
                        "Make multiple edits to one or more files atomically",
                        INPUT_SCHEMA,
                        null,
                        false),
                // 权限检查器
                LocalMultiEditTool::checkPermissions,
                // 输入验证器
                LocalMultiEditTool::validateInput);
    }

    /**
     * 权限检查方法
     */
    public static PermissionResult checkPermissions(String argumentsJson, ToolPermissionContext ctx) {
        try {
            JsonNode input = new com.fasterxml.jackson.databind.ObjectMapper().readTree(argumentsJson);
            JsonNode edits = input.get("edits");

            if (edits == null || !edits.isArray()) {
                return PermissionResult.deny("edits array is required");
            }

            for (JsonNode edit : edits) {
                String filePath = edit.has("file_path") ? edit.get("file_path").asText("") : "";

                if (filePath.isBlank()) {
                    return PermissionResult.deny("file_path is required for all edits");
                }

                if (!isPathAllowed(filePath)) {
                    return PermissionResult.deny("File path is not allowed: " + filePath, PermissionLevel.MODIFY_STATE);
                }

                if (isSensitivePath(filePath)) {
                    return PermissionResult.deny("Cannot modify sensitive system file: " + filePath, PermissionLevel.DESTRUCTIVE);
                }
            }

            return PermissionResult.allow();

        } catch (Exception e) {
            log.error("MultiEdit permission check failed", e);
            return PermissionResult.deny("Permission check error: " + e.getMessage());
        }
    }

    /**
     * 输入验证方法
     */
    public static String validateInput(JsonNode input) {
        JsonNode edits = input.get("edits");

        if (edits == null || !edits.isArray()) {
            return "edits array is required";
        }

        if (edits.size() == 0) {
            return "edits array cannot be empty";
        }

        for (int i = 0; i < edits.size(); i++) {
            JsonNode edit = edits.get(i);
            String filePath = edit.has("file_path") ? edit.get("file_path").asText("") : "";
            String oldString = edit.has("old_string") ? edit.get("old_string").asText("") : "";
            String newString = edit.has("new_string") ? edit.get("new_string").asText("") : "";

            if (filePath.isBlank()) {
                return "file_path is required for edit #" + (i + 1);
            }

            if (oldString.isBlank()) {
                return "old_string is required for edit #" + (i + 1);
            }

            if (newString.isBlank()) {
                return "new_string is required for edit #" + (i + 1);
            }

            if (oldString.equals(newString)) {
                return "old_string and new_string must be different for edit #" + (i + 1);
            }
        }

        return null;
    }

    /**
     * 检查路径是否在允许范围内
     */
    private static boolean isPathAllowed(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        if (!path.startsWith("/") && !path.matches("^[A-Za-z]:\\\\.*")) {
            return false;
        }

        String cwd = System.getProperty("user.dir");
        return path.startsWith(cwd) || path.startsWith(cwd.replace("\\", "/"));
    }

    /**
     * 检查是否为敏感路径
     */
    private static boolean isSensitivePath(String path) {
        String lower = path.toLowerCase();

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
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edits = (List<Map<String, Object>>) input.get("edits");

            if (edits == null || edits.isEmpty()) {
                return LocalToolResult.error("edits array is required and cannot be empty");
            }

            // 按文件分组编辑操作
            Map<String, List<EditOperation>> editsByFile = groupEditsByFile(edits);

            // 验证所有文件存在且大小合适
            for (String filePath : editsByFile.keySet()) {
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    return LocalToolResult.error("File does not exist: " + filePath);
                }

                long fileSize = Files.size(path);
                if (fileSize > MAX_FILE_SIZE) {
                    return LocalToolResult.error("File too large: " + filePath + " (" + fileSize + " bytes)");
                }
            }

            // 备份所有文件（用于回滚）
            Map<String, String> originalContents = new java.util.HashMap<>();
            List<String> modifiedFiles = new ArrayList<>();

            try {
                // 执行所有编辑
                for (Map.Entry<String, List<EditOperation>> entry : editsByFile.entrySet()) {
                    String filePath = entry.getKey();
                    List<EditOperation> fileEdits = entry.getValue();

                    Path path = Paths.get(filePath);
                    String content = Files.readString(path, StandardCharsets.UTF_8);

                    // 保存原始内容用于回滚
                    originalContents.put(filePath, content);

                    // 应用所有编辑
                    for (EditOperation edit : fileEdits) {
                        int matchIndex = content.indexOf(edit.oldString);
                        if (matchIndex == -1) {
                            throw new IOException("old_string not found in file " + filePath + ": " + truncate(edit.oldString, 50));
                        }

                        if (edit.replaceAll) {
                            content = content.replace(Pattern.quote(edit.oldString), edit.newString);
                        } else {
                            content = content.replaceFirst(Pattern.quote(edit.oldString), edit.newString);
                        }
                    }

                    // 写入临时文件（不立即覆盖）
                    modifiedFiles.add(filePath);
                    writeTempFile(path, content);
                }

                // 所有编辑成功后，原子移动临时文件
                for (String filePath : modifiedFiles) {
                    Path path = Paths.get(filePath);
                    Path tempPath = path.getParent().resolve(path.getFileName() + ".tmp");
                    Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }

                // 生成输出
                StringBuilder output = new StringBuilder();
                output.append("Successfully applied ").append(edits.size()).append(" edit(s) to ").append(editsByFile.size()).append(" file(s):\n");

                for (Map.Entry<String, List<EditOperation>> entry : editsByFile.entrySet()) {
                    String filePath = entry.getKey();
                    List<EditOperation> fileEdits = entry.getValue();

                    output.append("\n").append(filePath).append(":\n");
                    for (int i = 0; i < fileEdits.size(); i++) {
                        EditOperation edit = fileEdits.get(i);
                        output.append("  ").append(i + 1).append(". ").append(truncate(edit.oldString, 50));
                        output.append(" → ").append(truncate(edit.newString, 50)).append("\n");
                    }
                }

                return LocalToolResult.builder()
                        .success(true)
                        .content(output.toString())
                        .executionLocation("local")
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();

            } catch (Exception e) {
                // 回滚所有修改
                rollbackFiles(originalContents, modifiedFiles);
                return LocalToolResult.error("Edit failed, changes rolled back: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Multi-edit execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 编辑操作内部类
     */
    private static class EditOperation {
        String file_path;
        String oldString;
        String newString;
        boolean replaceAll;

        EditOperation(String file_path, String oldString, String newString, boolean replaceAll) {
            this.file_path = file_path;
            this.oldString = oldString;
            this.newString = newString;
            this.replaceAll = replaceAll;
        }
    }

    /**
     * 按文件分组编辑操作
     */
    private static Map<String, List<EditOperation>> groupEditsByFile(List<Map<String, Object>> edits) {
        Map<String, List<EditOperation>> result = new java.util.LinkedHashMap<>();

        for (Map<String, Object> edit : edits) {
            String filePath = (String) edit.get("file_path");
            String oldString = (String) edit.get("old_string");
            String newString = (String) edit.get("new_string");
            boolean replaceAll = getBoolean(edit, "replace_all", false);

            EditOperation op = new EditOperation(filePath, oldString, newString, replaceAll);

            result.computeIfAbsent(filePath, k -> new ArrayList<>()).add(op);
        }

        return result;
    }

    /**
     * 写入临时文件
     */
    private static void writeTempFile(Path path, String content) throws IOException {
        Path tempPath = path.getParent().resolve(path.getFileName() + ".tmp");
        Files.writeString(tempPath, content, StandardCharsets.UTF_8);
    }

    /**
     * 回滚文件
     */
    private static void rollbackFiles(Map<String, String> originalContents, List<String> modifiedFiles) {
        for (String filePath : modifiedFiles) {
            try {
                String originalContent = originalContents.get(filePath);
                if (originalContent != null) {
                    Path path = Paths.get(filePath);
                    Path tempPath = path.getParent().resolve(path.getFileName() + ".tmp");
                    Files.deleteIfExists(tempPath);
                    Files.writeString(path, originalContent, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.error("Failed to rollback file: {}", filePath, e);
            }
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
