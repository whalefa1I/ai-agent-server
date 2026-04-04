package demo.k8s.agent.tools.local.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 本地 Git 工具 - 执行 Git 命令并返回结果。
 * <p>
 * 支持的操作：
 * - status: 查看仓库状态
 * - diff: 生成差异
 * - log: 查看提交历史
 * - add: 添加文件到暂存区
 * - commit: 提交变更
 * - checkout: 切换分支/恢复文件
 * - branch: 分支管理
 * - merge: 合并分支
 * - rebase: 变基
 * - stash: 暂存变更
 * - show: 显示提交内容
 * - blame: 查看代码责任人
 */
public class LocalGitTool {

    private static final Logger log = LoggerFactory.getLogger(LocalGitTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LINES = 1000;

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"command\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Git command to execute (e.g., 'status', 'diff', 'log')\"" +
            "    }," +
            "    \"args\": {" +
            "      \"type\": \"array\"," +
            "      \"items\": {\"type\": \"string\"}," +
            "      \"description\": \"Arguments to pass to the git command\"" +
            "    }," +
            "    \"cwd\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Working directory (must be inside a git repository)\"" +
            "    }," +
            "    \"timeout\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Timeout in seconds (default: 30)\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"command\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "git",
                        ToolCategory.FILE_SYSTEM,
                        "Execute git commands in the local repository. Supports: status, diff, log, add, commit, checkout, branch, merge, rebase, stash, show, blame",
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
            String command = (String) input.get("command");
            @SuppressWarnings("unchecked")
            List<String> args = (List<String>) input.get("args");
            String cwdStr = (String) input.get("cwd");
            int timeout = getInt(input, "timeout", DEFAULT_TIMEOUT_SECONDS);

            if (command == null || command.isEmpty()) {
                return LocalToolResult.error("command is required");
            }

            // 验证命令白名单
            if (!isAllowedCommand(command)) {
                return LocalToolResult.error("Command not allowed: " + command +
                    ". Allowed commands: status, diff, log, add, commit, checkout, branch, merge, rebase, stash, show, blame, rev-parse, ls-files, cat-file");
            }

            // 确定工作目录
            Path workingDir = getWorkingDir(cwdStr);
            if (workingDir == null) {
                return LocalToolResult.error("Invalid working directory");
            }

            // 验证是 git 仓库
            if (!isGitRepository(workingDir)) {
                return LocalToolResult.error("Not a git repository: " + workingDir);
            }

            // 构建命令
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add("git");
            fullCommand.add(command);
            if (args != null) {
                fullCommand.addAll(args);
            }

            // 执行命令
            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return LocalToolResult.error("Command timed out after " + timeout + " seconds");
            }

            // 读取输出
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines()
                        .limit(MAX_OUTPUT_LINES)
                        .collect(Collectors.joining("\n"));
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - startTime;

            if (exitCode != 0) {
                return LocalToolResult.builder()
                        .success(false)
                        .error("Git command failed with exit code " + exitCode)
                        .content(output)
                        .executionLocation("local")
                        .durationMs(duration)
                        .metadata(objectMapper.valueToTree(
                                Map.of("exitCode", exitCode, "command", command)))
                        .build();
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(output)
                    .executionLocation("local")
                    .durationMs(duration)
                    .metadata(objectMapper.valueToTree(
                            Map.of("command", command, "outputLines", countLines(output))))
                    .build();

        } catch (IOException e) {
            log.error("Git command IO error", e);
            return LocalToolResult.error("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Git command interrupted", e);
            return LocalToolResult.error("Command interrupted");
        } catch (Exception e) {
            log.error("Git command execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 检查是否是允许的 git 命令
     */
    private static boolean isAllowedCommand(String command) {
        List<String> allowed = List.of(
                "status", "diff", "log", "add", "commit",
                "checkout", "branch", "merge", "rebase", "stash",
                "show", "blame", "rev-parse", "ls-files", "cat-file",
                "describe", "tag", "remote", "fetch", "pull", "push"
        );
        return allowed.contains(command);
    }

    /**
     * 获取工作目录
     */
    private static Path getWorkingDir(String cwdStr) {
        if (cwdStr != null && !cwdStr.isEmpty()) {
            Path path = Paths.get(cwdStr);
            if (Files.exists(path) && Files.isDirectory(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        // 默认使用当前目录
        return Paths.get("").toAbsolutePath().normalize();
    }

    /**
     * 检查是否是 git 仓库
     */
    private static boolean isGitRepository(Path dir) {
        Path gitDir = dir.resolve(".git");
        if (Files.exists(gitDir)) {
            return true;
        }
        // 向上查找父目录
        Path parent = dir.getParent();
        if (parent != null && !parent.equals(dir)) {
            return isGitRepository(parent);
        }
        return false;
    }

    /**
     * 辅助方法：获取整数参数
     */
    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 辅助方法：计算行数
     */
    private static int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) text.chars().filter(c -> c == '\n').count() + 1;
    }
}
