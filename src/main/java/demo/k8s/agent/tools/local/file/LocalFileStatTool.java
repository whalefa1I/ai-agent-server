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
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;

/**
 * 本地文件元数据工具
 * <p>
 * 功能：
 * - 获取文件大小、类型、权限
 * - 获取创建时间、修改时间、访问时间
 * - 计算文件哈希（MD5/SHA256）
 */
public class LocalFileStatTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStatTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"path\": {\"type\": \"string\", \"description\": \"File path\"}," +
            "    \"includeHash\": {\"type\": \"boolean\", \"description\": \"Include file hash (default false)\"}," +
            "    \"hashAlgorithm\": {\"type\": \"string\", \"enum\": [\"md5\", \"sha256\", \"sha1\"], \"description\": \"Hash algorithm (default sha256)\"}" +
            "  }," +
            "  \"required\": [\"path\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "local_file_stat",
                        ToolCategory.FILE_SYSTEM,
                        "Get file metadata including size, timestamps, permissions, and optional hash",
                        INPUT_SCHEMA,
                        null,
                        true)); // 只读操作
    }

    /**
     * 执行文件元数据查询
     *
     * @param input 输入参数
     *              - path: 文件路径
     *              - includeHash: 是否包含哈希值（默认 false）
     *              - hashAlgorithm: 哈希算法 "md5" | "sha256"（默认 "sha256"）
     * @return 文件元数据
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String pathStr = (String) input.get("path");
        Boolean includeHash = (Boolean) input.getOrDefault("includeHash", false);
        String hashAlgorithm = (String) input.getOrDefault("hashAlgorithm", "sha256");

        if (pathStr == null || pathStr.isBlank()) {
            return LocalToolResult.error("path is required");
        }

        Path path = Paths.get(pathStr);

        try {
            if (!Files.exists(path)) {
                return LocalToolResult.error("File does not exist: " + path);
            }

            Map<String, Object> statInfo = getFileInfo(path, includeHash, hashAlgorithm);
            return LocalToolResult.success("File info retrieved: " + path, statInfo);

        } catch (IOException e) {
            log.error("获取文件元数据失败：{}", path, e);
            return LocalToolResult.error("Failed to get file info: " + e.getMessage());
        }
    }

    private static Map<String, Object> getFileInfo(Path path, boolean includeHash, String hashAlgorithm) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

        Map<String, Object> info = Map.of(
                "path", path.toAbsolutePath().toString(),
                "name", path.getFileName().toString(),
                "isFile", attrs.isRegularFile(),
                "isDirectory", attrs.isDirectory(),
                "isSymbolicLink", attrs.isSymbolicLink(),
                "isOther", attrs.isOther(),
                "size", attrs.size(),
                "createdAt", attrs.creationTime().toString(),
                "modifiedAt", attrs.lastModifiedTime().toString(),
                "accessedAt", attrs.lastAccessTime().toString()
        );

        // 添加 POSIX 权限信息（仅类 Unix 系统）
        if (!attrs.isSymbolicLink() && Files.isReadable(path)) {
            try {
                PosixFileAttributes posixAttrs = Files.readAttributes(path, PosixFileAttributes.class);
                Set<PosixFilePermission> permissions = posixAttrs.permissions();
                info.put("permissions", permissionsToString(permissions));
                info.put("owner", posixAttrs.owner().getName());
                info.put("group", posixAttrs.group().getName());
            } catch (UnsupportedOperationException e) {
                // Windows 系统不支持 POSIX
                info.put("permissions", Files.isReadable(path) ? "readable" : "not-readable");
            }
        }

        // 添加文件哈希
        if (includeHash && attrs.isRegularFile()) {
            try {
                String hash = computeHash(path, hashAlgorithm);
                info.put("hash", hash);
                info.put("hashAlgorithm", hashAlgorithm);
            } catch (Exception e) {
                info.put("hashError", e.getMessage());
            }
        }

        return info;
    }

    private static String permissionsToString(Set<PosixFilePermission> permissions) {
        StringBuilder sb = new StringBuilder(9);
        sb.append(permissions.contains(PosixFilePermission.OWNER_READ) ? 'r' : '-');
        sb.append(permissions.contains(PosixFilePermission.OWNER_WRITE) ? 'w' : '-');
        sb.append(permissions.contains(PosixFilePermission.OWNER_EXECUTE) ? 'x' : '-');
        sb.append(permissions.contains(PosixFilePermission.GROUP_READ) ? 'r' : '-');
        sb.append(permissions.contains(PosixFilePermission.GROUP_WRITE) ? 'w' : '-');
        sb.append(permissions.contains(PosixFilePermission.GROUP_EXECUTE) ? 'x' : '-');
        sb.append(permissions.contains(PosixFilePermission.OTHERS_READ) ? 'r' : '-');
        sb.append(permissions.contains(PosixFilePermission.OTHERS_WRITE) ? 'w' : '-');
        sb.append(permissions.contains(PosixFilePermission.OTHERS_EXECUTE) ? 'x' : '-');
        return sb.toString();
    }

    private static String computeHash(Path path, String algorithm) throws Exception {
        MessageDigest digest = switch (algorithm.toLowerCase()) {
            case "md5" -> MessageDigest.getInstance("MD5");
            case "sha256" -> MessageDigest.getInstance("SHA-256");
            case "sha1" -> MessageDigest.getInstance("SHA-1");
            default -> MessageDigest.getInstance("SHA-256");
        };

        byte[] fileBytes = Files.readAllBytes(path);
        byte[] hashBytes = digest.digest(fileBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
