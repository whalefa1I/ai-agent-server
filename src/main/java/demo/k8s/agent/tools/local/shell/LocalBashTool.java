package demo.k8s.agent.tools.local.shell;

import com.fasterxml.jackson.databind.JsonNode;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 本地 Bash 工具 - 执行 Shell 命令。
 * 与 claude-code 的 BashTool 协议保持一致。
 */
public class LocalBashTool {

    private static final Logger log = LoggerFactory.getLogger(LocalBashTool.class);

    private static final int DEFAULT_TIMEOUT_MS = 60000; // 60 秒
    private static final int MAX_OUTPUT_SIZE = 10000; // 最大输出行数

    // 危险命令模式
    private static final Pattern[] DANGEROUS_PATTERNS = {
            Pattern.compile("rm\\s+(-[rf]+\\s+)?/\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rm\\s+(-[rf]+\\s+)?/\\*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dd\\s+if=/", Pattern.CASE_INSENSITIVE),
            Pattern.compile(":\\(\\)\\{:&\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mkfs", Pattern.CASE_INSENSITIVE),
            Pattern.compile("chmod\\s+-R\\s+777\\s+/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("curl.*\\|\\s*(ba)?sh", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wget.*\\|\\s*(ba)?sh", Pattern.CASE_INSENSITIVE),
    };

    /**
     * Bash 工具提示词（与 Claude Code 对齐）
     */
    private static final String BASH_PROMPT = """
            Executes a given bash command and returns its output.

            The working directory persists between commands, but shell state does not. The shell environment is initialized from the user's profile (bash or zsh).

            IMPORTANT: Avoid using this tool to run `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or after you have verified that a dedicated tool cannot accomplish your task. Instead, use the appropriate dedicated tool as this will provide a much better experience for the user:

            - File search: Use glob (NOT find or ls)
            - Content search: Use grep (NOT grep or rg)
            - Read files: Use file_read (NOT cat/head/tail)
            - Edit files: Use file_edit (NOT sed/awk)
            - Write files: Use file_write (NOT echo >/cat <<EOF)
            - Communication: Output text directly (NOT echo/printf)

            While the bash tool can do similar things, it's better to use the built-in tools as they provide a better user experience and make it easier to review tool calls and give permission.

            # Instructions

            - If your command will create new directories or files, first use this tool to run `ls` to verify the parent directory exists and is the correct location.
            - Always quote file paths that contain spaces with double quotes in your command (e.g., cd "path with spaces/file.txt")
            - Try to maintain your current working directory throughout the session by using absolute paths and avoiding usage of `cd`. You may use `cd` if the User explicitly requests it.
            - You may specify an optional timeout in milliseconds (up to 600000ms / 10 minutes). By default, your command will timeout after 60000ms (1 minute).
            - You can use the `run_in_background` parameter to run the command in the background. Only use this if you don't need the result immediately and are OK being notified when the command completes later.

            When issuing multiple commands:
            - If the commands are independent and can run in parallel, make multiple bash tool calls in a single message. Example: if you need to run "git status" and "git diff", send a single message with two bash tool calls in parallel.
            - If the commands depend on each other and must run sequentially, use a single bash call with '&&' to chain them together.
            - Use ';' only when you need to run commands sequentially but don't care if earlier commands fail.
            - DO NOT use newlines to separate commands (newlines are ok in quoted strings).

            For git commands:
            - Prefer to create a new commit rather than amending an existing commit.
            - Before running destructive operations (e.g., git reset --hard, git push --force), consider whether there is a safer alternative. Only use destructive operations when they are truly the best approach.
            - Never skip hooks (--no-verify) or bypass signing (--no-gpg-sign) unless the user has explicitly asked for it.

            Avoid unnecessary `sleep` commands:
            - Do not sleep between commands that can run immediately - just run them.
            - If your command is long running and you would like to be notified when it finishes - use `run_in_background`. No sleep needed.
            - Do not retry failing commands in a sleep loop - diagnose the root cause.
            - If you must poll an external process, use a check command rather than sleeping first.
            - If you must sleep, keep the duration short (1-5 seconds) to avoid blocking the user.
            """;

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"command\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Shell command to execute\"" +
            "    }," +
            "    \"timeout\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Timeout in milliseconds (defaults to 60000)\"" +
            "    }," +
            "    \"run_in_background\": {" +
            "      \"type\": \"boolean\"," +
            "      \"description\": \"Set to true to run this command in the background. Use Read to read the output later.\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"command\"]" +
            "}";

    /**
     * 创建工具定义（带权限检查）
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "bash",
                        ToolCategory.SHELL,
                        "Execute shell commands. Supports background execution with run_in_background flag.",
                        INPUT_SCHEMA,
                        null,
                        false),
                // 权限检查器
                LocalBashTool::checkPermissions,
                // 输入验证器
                LocalBashTool::validateInput,
                // isConcurrencySafe: 只读命令视为并发安全（类似 Claude Code）
                LocalBashTool::isConcurrencySafe);
    }

    /**
     * 判断 bash 命令是否并发安全
     * 参考 Claude Code 的实现：只读命令（curl, grep, cat, ls 等）视为并发安全
     */
    private static boolean isConcurrencySafe(JsonNode input) {
        String command = input.has("command") ? input.get("command").asText("") : "";
        if (command.isBlank()) {
            return false;
        }

        // 提取命令的第一个词（忽略前导空格和管道）
        String firstCmd = command.trim().split("[|&;\\s]+")[0].toLowerCase();

        // 只读命令列表（类似 Claude Code 的 checkReadOnlyConstraints）
        return switch (firstCmd) {
            case "curl", "wget", "httpie" -> true;  // HTTP 下载
            case "cat", "head", "tail", "less", "more" -> true;  // 文件读取
            case "grep", "egrep", "fgrep", "rg", "ripgrep" -> true;  // 搜索
            case "ls", "dir", "vdir" -> true;  // 目录列表
            case "glob", "find", "fd" -> true;  // 文件查找
            case "wc", "sort", "uniq", "cut", "awk", "sed" -> true;  // 文本处理（只读模式）
            case "echo", "printf" -> true;  // 输出
            case "date", "time", "whoami", "pwd", "hostname", "uname" -> true;  // 系统信息
            case "git", "git2" -> isReadOnlyGitCommand(command);  // git 只读命令
            default -> false;
        };
    }

    /**
     * 判断 git 命令是否是只读的
     */
    private static boolean isReadOnlyGitCommand(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 2) return false;

        String subcommand = parts[1].toLowerCase();
        return switch (subcommand) {
            case "status", "log", "diff", "show", "branch", "tag", "remote", "config" -> true;
            case "ls-remote", "describe", "rev-parse", "rev-list" -> true;
            case "stash", "reflog" -> true;
            default -> false;  // commit, push, pull, merge, rebase 等都不是只读
        };
    }

    /**
     * 权限检查方法 - 与 Claude Code 的 bashToolHasPermission 对齐
     */
    public static PermissionResult checkPermissions(String argumentsJson, ToolPermissionContext ctx) {
        try {
            JsonNode input = new com.fasterxml.jackson.databind.ObjectMapper().readTree(argumentsJson);
            String command = input.has("command") ? input.get("command").asText("") : "";

            if (command.isBlank()) {
                return PermissionResult.deny("command is required");
            }

            // 1. 检查危险命令模式（始终拒绝）
            for (Pattern pattern : DANGEROUS_PATTERNS) {
                if (pattern.matcher(command).find()) {
                    return PermissionResult.deny("Dangerous command detected: " + command, PermissionLevel.DESTRUCTIVE);
                }
            }

            // 2. 多行命令在开发阶段允许，交由 bash -c 原生解析

            // 3. 检查路径约束（可选：检查工作目录是否在允许范围内）
            String workdir = input.has("workdir") ? input.get("workdir").asText("") : "";
            if (!workdir.isBlank() && !isPathAllowed(workdir)) {
                return PermissionResult.deny("Working directory is not allowed: " + workdir, PermissionLevel.MODIFY_STATE);
            }

            // 4. 提取命令前缀用于规则匹配（如 "git commit"）
            String commandPrefix = ShellRuleMatcher.extractCommandPrefix(command);

            // 5. 通过 PermissionManager 检查是否有匹配的规则或授权
            // 注意：这里简化处理，实际应该在 PermissionManager 中统一检查

            // 6. 默认需要用户确认
            return PermissionResult.allow();

        } catch (Exception e) {
            log.error("Bash permission check failed", e);
            return PermissionResult.deny("Permission check error: " + e.getMessage());
        }
    }

    /**
     * 输入验证方法
     */
    public static String validateInput(JsonNode input) {
        String command = input.has("command") ? input.get("command").asText("") : "";

        if (command.isBlank()) {
            return "command is required";
        }

        // 验证 timeout
        if (input.has("timeout")) {
            int timeout = input.get("timeout").asInt(-1);
            if (timeout <= 0 || timeout > 600000) {
                return "timeout must be between 1 and 600000 ms";
            }
        }

        return null; // 通过验证
    }

    /**
     * 检查路径是否在允许范围内
     */
    private static boolean isPathAllowed(String path) {
        // 简化实现：允许所有绝对路径
        // 生产环境应该实现更严格的路径约束检查
        return path.startsWith("/") || path.matches("^[A-Za-z]:\\\\.*");
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String command = (String) input.get("command");
        Boolean runInBackground = getBoolean(input, "run_in_background", false);

        if (command == null || command.isEmpty()) {
            return LocalToolResult.error("command is required");
        }

        // 后台执行模式（简化实现，返回后台执行提示）
        if (runInBackground) {
            return LocalToolResult.builder()
                    .success(true)
                    .content("Command started in background. Note: Full background session management is not yet implemented in this demo.\nCommand: " + command)
                    .executionLocation("local")
                    .build();
        }

        // 同步执行模式
        return executeSynchronous(input, command);
    }

    /**
     * 同步执行模式
     */
    private static LocalToolResult executeSynchronous(Map<String, Object> input, String command) {
        long startTime = System.currentTimeMillis();

        try {
            int timeout = getInt(input, "timeout", DEFAULT_TIMEOUT_MS);

            // 检查危险命令
            for (Pattern pattern : DANGEROUS_PATTERNS) {
                if (pattern.matcher(command).find()) {
                    return LocalToolResult.error("Dangerous command detected: " + command);
                }
            }

            // 允许：多行、|, ||, &&, >, <, 2>&1, $(...) 等常见 shell 语法。
            // 仅阻止单独的 &（后台执行，与 run_in_background 模式冲突）。
            if (command.matches(".*[^&]&([^=].*|$)") && !command.contains("&&") && !command.contains("2>&1")) {
                return LocalToolResult.error("Background execution (&) is not supported in sync mode. Use run_in_background=true instead.");
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            Map<String, String> env = processBuilder.environment();
            env.put("LC_ALL", "en_US.UTF-8");

            String workdir = (String) input.get("workdir");
            if (workdir != null && !workdir.isEmpty()) {
                processBuilder.directory(new java.io.File(workdir));
            }

            // 使用 bash -c 执行命令，合并 stderr 到 stdout
            processBuilder.command("bash", "-c", command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // 读取输出
            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputLines.size() < MAX_OUTPUT_SIZE) {
                        outputLines.add(line);
                    }
                }
            }

            // 等待完成
            boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return LocalToolResult.error("Command timed out after " + timeout + "ms");
            }

            int exitCode = process.exitValue();
            StringBuilder output = new StringBuilder();
            output.append("Command: ").append(command).append("\n");
            if (workdir != null) {
                output.append("Directory: ").append(workdir).append("\n");
            }
            output.append("Exit code: ").append(exitCode).append("\n");
            output.append("Duration: ").append(System.currentTimeMillis() - startTime).append("ms\n\n");

            if (exitCode == 0) {
                output.append("STDOUT:\n");
            } else {
                output.append("OUTPUT:\n");
            }

            for (String line : outputLines) {
                output.append(line).append("\n");
            }

            if (outputLines.size() >= MAX_OUTPUT_SIZE) {
                output.append("\n(Output truncated at ").append(MAX_OUTPUT_SIZE).append(" lines)");
            }

            LocalToolResult.LocalToolResultBuilder builder = LocalToolResult.builder()
                    .executionLocation("local")
                    .durationMs(System.currentTimeMillis() - startTime);

            if (exitCode == 0) {
                builder.success(true).content(output.toString());
            } else {
                builder.success(false).content(output.toString()).error("Exit code: " + exitCode);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Bash execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

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
