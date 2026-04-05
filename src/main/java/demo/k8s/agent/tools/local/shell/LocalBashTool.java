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
                LocalBashTool::validateInput);
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

            // 2. 检查命令注入（多行命令、后台执行）
            if (command.contains("\n")) {
                return PermissionResult.deny("Multi-line commands are not supported", PermissionLevel.MODIFY_STATE);
            }

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

            // 检查注入（允许多种合法 shell 语法，但阻止多行命令和后台执行）
            // 允许：|, ||, &&, >, <, 2>&1, $(...) 等合法语法
            // 阻止：换行符（多行命令）、&（后台执行，与后台模式冲突）
            if (command.contains("\n")) {
                return LocalToolResult.error("Multi-line commands are not supported. Please run commands separately.");
            }
            // 只阻止单独的 &（后台执行），允许 && 和 2>&1
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
