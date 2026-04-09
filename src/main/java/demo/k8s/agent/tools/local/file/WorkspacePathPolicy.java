package demo.k8s.agent.tools.local.file;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一路径策略：
 * - 相对路径：拼接到 workspaceRoot（默认 user.dir 或 DEMO_WORKSPACE_ROOT）
 * - 规范化：normalize + toAbsolutePath
 * - 越界保护：解析后必须仍在 workspaceRoot 下
 */
public final class WorkspacePathPolicy {

    private WorkspacePathPolicy() {}

    public static String workspaceRoot() {
        String env = System.getenv("DEMO_WORKSPACE_ROOT");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return System.getProperty("user.dir");
    }

    public static ResolvedPath resolveToWorkspace(String rawPath) {
        return resolveToWorkspace(rawPath, workspaceRoot());
    }

    public static ResolvedPath resolveToWorkspace(String rawPath, String workspaceRoot) {
        if (rawPath == null) {
            return ResolvedPath.invalid("path is required", null, workspaceRoot);
        }
        String trimmed = rawPath.trim();
        if (trimmed.isEmpty()) {
            return ResolvedPath.invalid("path is required", rawPath, workspaceRoot);
        }

        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        Path p = Paths.get(trimmed);
        boolean wasRelative = !p.isAbsolute();
        Path resolved = (wasRelative ? root.resolve(p) : p).toAbsolutePath().normalize();

        if (!resolved.startsWith(root)) {
            return ResolvedPath.invalid(
                    "path escapes workspaceRoot (resolved=" + resolved + ", workspaceRoot=" + root + ")",
                    trimmed,
                    root.toString());
        }
        return ResolvedPath.ok(trimmed, resolved.toString(), root.toString(), wasRelative);
    }

    public record ResolvedPath(
            boolean ok,
            boolean wasRelative,
            String raw,
            String resolved,
            String workspaceRoot,
            String error) {
        public static ResolvedPath ok(String raw, String resolved, String workspaceRoot, boolean wasRelative) {
            return new ResolvedPath(true, wasRelative, raw, resolved, workspaceRoot, null);
        }

        public static ResolvedPath invalid(String error, String raw, String workspaceRoot) {
            return new ResolvedPath(false, false, raw, null, workspaceRoot, error);
        }
    }
}

