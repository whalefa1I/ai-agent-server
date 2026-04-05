package demo.k8s.agent.tools.local.shell;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 本地 Shell 命令执行工具 - 执行 Bash/Shell 命令。
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
            "    \"workdir\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Working directory (defaults to current directory)\"" +
            "    }," +
            "    \"timeout\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Timeout in milliseconds (defaults to 60000)\"" +
            "    }," +
            "    \"background\": {" +
            "      \"type\": \"boolean\"," +
            "      \"description\": \"Run in background (returns session ID for later retrieval)\"" +
            "    }," +
            "    \"sessionId\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Session ID to query/stop (used with background mode)\"" +
            "    }," +
            "    \"action\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Action for session: 'start', 'status', 'output', 'stop' (background mode only)\"" +
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
                        "bash",
                        ToolCategory.SHELL,
                        "Execute shell commands. Supports synchronous execution (default) or background mode with session management. Use action='start' to run in background, action='status'/'output' to query, action='stop' to terminate.",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String command = (String) input.get("command");
        Boolean background = getBoolean(input, "background", false);
        String sessionId = (String) input.get("sessionId");
        String action = (String) input.get("action");

        // 后台会话模式
        if (background || action != null) {
            return executeBackground(input, command, sessionId, action);
        }

        // 同步执行模式（原有逻辑）
        return executeSynchronous(input, command);
    }

    /**
     * 后台执行模式
     */
    private static LocalToolResult executeBackground(Map<String, Object> input, String command,
                                                      String sessionId, String action) {
        try {
            String workdir = (String) input.get("workdir");
            int timeout = getInt(input, "timeout", DEFAULT_TIMEOUT_MS);
            Boolean background = getBoolean(input, "background", false);

            if ("stop".equals(action)) {
                if (sessionId == null) {
                    return LocalToolResult.error("sessionId required for stop action");
                }
                boolean stopped = BashSessionManager.stopSession(sessionId);
                return LocalToolResult.success(stopped ? "Session stopped: " + sessionId : "Session not found");
            }

            if ("status".equals(action)) {
                if (sessionId == null) {
                    return LocalToolResult.error("sessionId required for status action");
                }
                BashSessionManager.BashSession session = BashSessionManager.getSession(sessionId);
                if (session == null) {
                    return LocalToolResult.error("Session not found: " + sessionId);
                }
                StringBuilder status = new StringBuilder();
                status.append("Session: ").append(sessionId).append("\n");
                status.append("Command: ").append(session.command).append("\n");
                status.append("Status: ").append(session.isRunning() ? "Running" : "Completed").append("\n");
                if (session.exitCode != null) {
                    status.append("Exit Code: ").append(session.exitCode).append("\n");
                }
                status.append("Duration: ").append(System.currentTimeMillis() - session.startTime).append("ms\n");
                status.append("Output Length: ").append(session.output.length()).append(" bytes\n");
                return LocalToolResult.success(status.toString());
            }

            if ("output".equals(action)) {
                if (sessionId == null) {
                    return LocalToolResult.error("sessionId required for output action");
                }
                String output = BashSessionManager.getSessionOutput(sessionId);
                if (output == null) {
                    return LocalToolResult.error("Session not found: " + sessionId);
                }
                return LocalToolResult.success(output);
            }

            if ("start".equals(action) || background) {
                String newSessionId = BashSessionManager.startSession(command, workdir, timeout);
                StringBuilder response = new StringBuilder();
                response.append("Background session started: ").append(newSessionId).append("\n");
                response.append("Command: ").append(command).append("\n");
                response.append("Use sessionId to query status/output or stop the session.\n");
                response.append("Example: {\"command\": \"\", \"sessionId\": \"").append(newSessionId)
                        .append("\", \"action\": \"status\"}");

                com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
                return LocalToolResult.builder()
                        .success(true)
                        .content(response.toString())
                        .executionLocation("local")
                        .metadata(mapper.valueToTree(Map.of("sessionId", newSessionId)))
                        .build();
            }

            return LocalToolResult.error("Unknown action: " + action);

        } catch (Exception e) {
            log.error("Bash background execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 同步执行模式
     */
    private static LocalToolResult executeSynchronous(Map<String, Object> input, String command) {
        long startTime = System.currentTimeMillis();

        try {
            String workdir = (String) input.get("workdir");
            int timeout = getInt(input, "timeout", DEFAULT_TIMEOUT_MS);

            if (command == null || command.isEmpty()) {
                return LocalToolResult.error("command is required");
            }

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
                return LocalToolResult.error("Background execution (&) is not supported in sync mode. Use background mode instead.");
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            Map<String, String> env = processBuilder.environment();
            env.put("LC_ALL", "en_US.UTF-8");

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
