package demo.k8s.agent.tui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TUI 客户端 - 基于 JLine3 和 WebSocket 的终端界面。
 * <p>
 * 用户通过终端与远程 Agent 服务交互，WebSocket 实时通信。
 */
public class AgentTuiClient {

    private final String serverUrl;
    private final String authToken;
    private final Terminal terminal;
    private final LineReader lineReader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // WebSocket 客户端
    private TuiWebSocketClient wsClient;

    // 消息队列（用于处理异步接收的消息）
    private final BlockingQueue<JsonNode> messageQueue = new LinkedBlockingQueue<>();

    // 会话状态
    private String sessionId;
    private boolean connected = false;
    private Instant sessionStart;

    // 权限响应队列
    private final BlockingQueue<PermissionChoice> permissionResponseQueue = new LinkedBlockingQueue<>();

    public AgentTuiClient(String serverUrl, String authToken) {
        this.serverUrl = serverUrl;
        this.authToken = authToken;

        // 初始化终端
        this.terminal = TerminalBuilder.builder()
                .name("agent-tui")
                .build();

        // 初始化 LineReader（支持自动补全和历史）
        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    public AgentTuiClient(String serverUrl) {
        this(serverUrl, null);
    }

    /**
     * 启动 TUI 客户端
     */
    public void run() throws Exception {
        printBanner();

        // 连接 WebSocket
        connectWebSocket();

        // 进入主循环
        runMainLoop();

        // 清理
        cleanup();
    }

    /**
     * 打印欢迎横幅
     */
    private void printBanner() {
        terminal.puts(terminal.info().clearScreen());
        terminal.writer().println();
        terminal.writer().println(style("╔════════════════════════════════════════════════╗", AttributedStyle.BOLD));
        terminal.writer().println(style("║   minimal-k8s-agent-demo  TUI Client v0.1.0     ║", AttributedStyle.BOLD));
        terminal.writer().println(style("║   按 /help 查看帮助，/quit 退出                 ║", AttributedStyle.DEFAULT));
        terminal.writer().println(style("╚════════════════════════════════════════════════╝", AttributedStyle.BOLD));
        terminal.writer().println();
        terminal.writer().flush();
    }

    /**
     * 连接 WebSocket 服务器
     */
    private void connectWebSocket() throws Exception {
        terminal.writer().println("正在连接服务器：" + serverUrl);
        terminal.writer().flush();

        // 如果有 Token，添加到 URL
        String connectUrl = serverUrl;
        if (authToken != null && !authToken.isEmpty()) {
            // 将 Token 添加到 URL 路径
            if (serverUrl.contains("?")) {
                connectUrl = serverUrl + "&token=" + authToken;
            } else {
                // 对于 WebSocket，Token 作为路径的一部分
                connectUrl = serverUrl + "/" + authToken;
            }
            terminal.writer().println("使用 Token 认证");
        }

        wsClient = new TuiWebSocketClient(new URI(connectUrl));
        wsClient.connect();

        // 等待连接（最多 10 秒）
        for (int i = 0; i < 20; i++) {
            if (connected) {
                terminal.writer().println(style("✓ 已连接到服务器 (Session: " + sessionId + ")", AttributedStyle.GREEN));
                terminal.writer().println();
                terminal.writer().flush();
                sessionStart = Instant.now();
                return;
            }
            Thread.sleep(500);
        }

        throw new RuntimeException("连接超时");
    }

    /**
     * 主循环：读取用户输入，处理消息
     */
    private void runMainLoop() {
        // 启动消息处理线程
        Thread messageProcessor = new Thread(this::processMessages);
        messageProcessor.setDaemon(true);
        messageProcessor.start();

        while (true) {
            try {
                // 显示提示符
                String prompt = buildPrompt();
                String userInput = lineReader.readLine(prompt);

                if (userInput == null) {
                    // Ctrl+D 退出
                    break;
                }

                userInput = userInput.trim();
                if (userInput.isEmpty()) {
                    continue;
                }

                // 处理命令
                if (userInput.startsWith("/")) {
                    if (!handleCommand(userInput)) {
                        break; // 退出
                    }
                } else {
                    // 发送用户消息
                    sendMessage(userInput);
                }

            } catch (UserInterruptException e) {
                // Ctrl+C 中断当前操作
                terminal.writer().println();
                terminal.writer().println("已中断");
                terminal.writer().flush();
            } catch (EndOfFileException e) {
                break;
            } catch (Exception e) {
                terminal.writer().println("错误：" + e.getMessage());
                terminal.writer().flush();
            }
        }
    }

    /**
     * 构建提示符
     */
    private String buildPrompt() {
        Duration duration = Duration.between(sessionStart, Instant.now());
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;

        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("❯ ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
        return sb.toAnsi(terminal);
    }

    /**
     * 处理用户命令
     */
    private boolean handleCommand(String cmd) throws Exception {
        switch (cmd.toLowerCase()) {
            case "/quit":
            case "/exit":
                return false;

            case "/help":
                printHelp();
                break;

            case "/clear":
                terminal.puts(terminal.info().clearScreen());
                terminal.writer().flush();
                break;

            case "/history":
                requestHistory();
                break;

            case "/stats":
                requestStats();
                break;

            default:
                terminal.writer().println("未知命令：'" + cmd + "'，输入 /help 查看帮助");
                terminal.writer().flush();
        }
        return true;
    }

    /**
     * 打印帮助信息
     */
    private void printHelp() {
        terminal.writer().println();
        terminal.writer().println("可用命令：");
        terminal.writer().println("  /help     - 显示帮助");
        terminal.writer().println("  /quit     - 退出程序");
        terminal.writer().println("  /clear    - 清屏");
        terminal.writer().println("  /history  - 显示历史消息（最近 20 条）");
        terminal.writer().println("  /stats    - 显示会话统计");
        terminal.writer().println();
        terminal.writer().println("输入任意文本与 Agent 对话，工具调用时会弹出确认对话框。");
        terminal.writer().println();
        terminal.writer().flush();
    }

    /**
     * 发送用户消息
     */
    private void sendMessage(String content) throws Exception {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "USER_MESSAGE");
        msg.put("content", content);
        msg.put("requestId", "req_" + System.currentTimeMillis());
        msg.put("timestamp", Instant.now().toString());

        String json = objectMapper.writeValueAsString(msg);
        wsClient.send(json);
    }

    /**
     * 请求历史消息
     */
    private void requestHistory() throws Exception {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "GET_HISTORY");
        msg.put("limit", 20);
        msg.put("requestId", "history_" + System.currentTimeMillis());
        msg.put("timestamp", Instant.now().toString());

        String json = objectMapper.writeValueAsString(msg);
        wsClient.send(json);
    }

    /**
     * 请求统计信息
     */
    private void requestStats() throws Exception {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "GET_STATS");
        msg.put("requestId", "stats_" + System.currentTimeMillis());
        msg.put("timestamp", Instant.now().toString());

        String json = objectMapper.writeValueAsString(msg);
        wsClient.send(json);
    }

    /**
     * 处理接收到的消息（后台线程）
     */
    private void processMessages() {
        while (connected || wsClient.isOpen()) {
            try {
                JsonNode msg = messageQueue.poll(1, TimeUnit.SECONDS);
                if (msg == null) {
                    continue;
                }

                String type = msg.path("type").asText();

                terminal.io().stdout().write('\r');  // 回到行首
                terminal.io().stdout().write('\n');

                switch (type) {
                    case "TEXT_DELTA":
                        String delta = msg.path("delta").asText();
                        terminal.writer().print(delta);
                        terminal.writer().flush();
                        break;

                    case "TOOL_CALL":
                        String toolName = msg.path("toolName").asText();
                        String status = msg.path("status").asText();
                        if ("started".equals(status)) {
                            terminal.writer().println();
                            terminal.writer().println(style("🔧 正在调用工具：" + toolName, AttributedStyle.YELLOW));
                            terminal.writer().flush();
                        }
                        break;

                    case "PERMISSION_REQUEST":
                        handlePermissionRequest(msg);
                        break;

                    case "RESPONSE_COMPLETE":
                        terminal.writer().println();
                        terminal.writer().println();
                        terminal.writer().flush();
                        break;

                    case "HISTORY":
                        handleHistoryResponse(msg);
                        break;

                    case "STATS":
                        handleStatsResponse(msg);
                        break;

                    case "ERROR":
                        String code = msg.path("code").asText();
                        String message = msg.path("message").asText();
                        terminal.writer().println(style("❌ 错误 [" + code + "]: " + message, AttributedStyle.RED));
                        terminal.writer().flush();
                        break;
                }

                // 重新显示提示符
                if (connected) {
                    terminal.writer().print(buildPrompt());
                    terminal.writer().flush();
                }

            } catch (Exception e) {
                // 忽略处理错误
            }
        }
    }

    /**
     * 处理权限请求
     */
    private void handlePermissionRequest(JsonNode msg) {
        String id = msg.path("id").asText();
        String toolName = msg.path("toolName").asText();
        String levelLabel = msg.path("levelLabel").asText();
        String inputSummary = msg.path("inputSummary").asText();
        String riskExplanation = msg.path("riskExplanation").asText();

        try {
            // 显示对话框
            terminal.writer().println();
            terminal.writer().println(style("┌─────────────────────────────────────────────┐", AttributedStyle.BOLD));
            terminal.writer().println(style("│  🔐 工具调用确认                            │", AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW)));
            terminal.writer().println(style("├─────────────────────────────────────────────┤", AttributedStyle.BOLD));
            terminal.writer().println(style("│  工具：" + padRight(toolName, 30) + "│", AttributedStyle.DEFAULT));
            terminal.writer().println(style("│  风险：" + padRight(levelLabel, 25) + "│", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)));
            terminal.writer().println(style("├─────────────────────────────────────────────┤", AttributedStyle.BOLD));
            terminal.writer().println(style("│  " + truncate(inputSummary, 43) + "│", AttributedStyle.DEFAULT));
            terminal.writer().println(style("├─────────────────────────────────────────────┤", AttributedStyle.BOLD));
            terminal.writer().println(style("│  ⚠️  " + truncate(riskExplanation, 40) + "│", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)));
            terminal.writer().println(style("└─────────────────────────────────────────────┘", AttributedStyle.BOLD));
            terminal.writer().println();
            terminal.writer().println(style("[1] 本次允许   [2] 会话允许   [3] 始终允许   [4] 拒绝", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)));
            terminal.writer().flush();

            // 读取用户选择
            while (true) {
                int choice = terminal.reader().read();
                String choiceStr = String.valueOf((char) choice);

                if ("1234q".contains(choiceStr.toLowerCase())) {
                    // 发送响应
                    String permissionChoice = switch (choiceStr) {
                        case "1", "a" -> "ALLOW_ONCE";
                        case "2", "s" -> "ALLOW_SESSION";
                        case "3", "w" -> "ALLOW_ALWAYS";
                        case "4", "q" -> "DENY";
                        default -> "DENY";
                    };

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("type", "PERMISSION_RESPONSE");
                    resp.put("requestId", id);
                    resp.put("choice", permissionChoice);

                    String json = objectMapper.writeValueAsString(resp);
                    wsClient.send(json);

                    if ("DENY".equals(permissionChoice)) {
                        terminal.writer().println(style("已拒绝", AttributedStyle.RED));
                    } else {
                        terminal.writer().println(style("已授权 (" + permissionChoice + ")", AttributedStyle.GREEN));
                    }
                    terminal.writer().flush();
                    break;
                }
            }

        } catch (Exception e) {
            terminal.writer().println("处理权限请求失败：" + e.getMessage());
            terminal.writer().flush();
        }
    }

    /**
     * 处理历史消息响应
     */
    private void handleHistoryResponse(JsonNode msg) {
        try {
            JsonNode messagesNode = msg.path("messages");
            if (!messagesNode.isArray()) {
                terminal.writer().println("未找到历史消息");
                terminal.writer().flush();
                return;
            }

            terminal.writer().println();
            terminal.writer().println(style("═════════════════════════════════════════════════", AttributedStyle.BOLD));
            terminal.writer().println(style("  历史消息", AttributedStyle.BOLD.foreground(AttributedStyle.CYAN)));
            terminal.writer().println(style("═════════════════════════════════════════════════", AttributedStyle.BOLD));

            int count = 0;
            for (JsonNode msgNode : messagesNode) {
                String type = msgNode.path("type").asText();
                String content = msgNode.path("content").asText();
                String timestamp = msgNode.path("timestamp").asText();

                // 解析时间戳
                String displayTime = "";
                if (timestamp != null && !timestamp.isEmpty()) {
                    try {
                        Instant instant = Instant.parse(timestamp);
                        displayTime = "[" + instant.toString().replace("T", " ").substring(0, 19) + "] ";
                    } catch (Exception e) {
                        displayTime = "[" + timestamp + "] ";
                    }
                }

                // 根据类型显示
                AttributedStyle style = switch (type) {
                    case "USER" -> AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
                    case "ASSISTANT" -> AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
                    case "SYSTEM" -> AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
                    case "TOOL" -> AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA);
                    default -> AttributedStyle.DEFAULT;
                };

                String typeLabel = switch (type) {
                    case "USER" -> "👤 用户";
                    case "ASSISTANT" -> "🤖 助手";
                    case "SYSTEM" -> "⚙️ 系统";
                    case "TOOL" -> "🔧 工具";
                    default -> type;
                };

                terminal.writer().println();
                terminal.writer().println(style(displayTime + typeLabel, style));

                // 截断长内容
                if (content != null && !content.isEmpty()) {
                    String displayContent = content.length() > 200 ?
                            content.substring(0, 200) + "..." : content;
                    terminal.writer().println(style(displayContent, style));
                }

                count++;
            }

            if (count == 0) {
                terminal.writer().println("暂无历史消息");
            }

            terminal.writer().println();
            terminal.writer().println(style("═════════════════════════════════════════════════", AttributedStyle.BOLD));
            terminal.writer().flush();

        } catch (Exception e) {
            terminal.writer().println("处理历史消息失败：" + e.getMessage());
            terminal.writer().flush();
        }
    }

    /**
     * 处理统计信息响应
     */
    private void handleStatsResponse(JsonNode msg) {
        try {
            JsonNode statsNode = msg.path("stats");
            if (statsNode.isMissingNode()) {
                terminal.writer().println("未找到统计信息");
                terminal.writer().flush();
                return;
            }

            String sessionId = statsNode.path("sessionId").asText("");
            String sessionStarted = statsNode.path("sessionStartedAt").asText("N/A");
            long durationSeconds = statsNode.path("durationSeconds").asLong(0);
            long totalModelCalls = statsNode.path("totalModelCalls").asLong(0);
            long totalToolCalls = statsNode.path("totalToolCalls").asLong(0);
            long totalInputTokens = statsNode.path("totalInputTokens").asLong(0);
            long totalOutputTokens = statsNode.path("totalOutputTokens").asLong(0);

            terminal.writer().println();
            terminal.writer().println(style("╔════════════════════════════════════════════════╗", AttributedStyle.BOLD));
            terminal.writer().println(style("║           会话统计信息                          ║", AttributedStyle.BOLD.foreground(AttributedStyle.CYAN)));
            terminal.writer().println(style("╠════════════════════════════════════════════════╣", AttributedStyle.BOLD));
            terminal.writer().println(style("║  Session ID: " + padRight(sessionId, 32) + "║", AttributedStyle.DEFAULT));
            terminal.writer().println(style("║  开始时间：  " + padRight(sessionStarted.replace("T", " ").substring(0, 19), 32) + "║", AttributedStyle.DEFAULT));
            terminal.writer().println(style("╠════════════════════════════════════════════════╣", AttributedStyle.BOLD));
            terminal.writer().println(style("║  会话时长：  " + padRight(formatDuration(durationSeconds), 32) + "║", AttributedStyle.DEFAULT));
            terminal.writer().println(style("║  模型调用：  " + padRight(String.valueOf(totalModelCalls), 32) + "║", AttributedStyle.DEFAULT));
            terminal.writer().println(style("║  工具调用：  " + padRight(String.valueOf(totalToolCalls), 32) + "║", AttributedStyle.DEFAULT));
            terminal.writer().println(style("║  输入 Token: " + padRight(String.valueOf(totalInputTokens), 32) + "║", AttributedStyle.DEFAULT));
            terminal.writer().println(style("║  输出 Token: " + padRight(String.valueOf(totalOutputTokens), 32) + "║", AttributedStyle.DEFAULT));
            terminal.writer().println(style("╚════════════════════════════════════════════════╝", AttributedStyle.BOLD));
            terminal.writer().flush();

        } catch (Exception e) {
            terminal.writer().println("处理统计信息失败：" + e.getMessage());
            terminal.writer().flush();
        }
    }

    /**
     * 格式化持续时间
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%d 小时 %d 分 %d 秒", hours, minutes, secs);
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        try {
            if (wsClient != null && wsClient.isOpen()) {
                wsClient.close();
            }
            terminal.close();
        } catch (Exception e) {
            // 忽略
        }
    }

    // ===== 工具方法 =====

    private static String style(String text, AttributedStyle style) {
        return new AttributedStringBuilder()
                .append(text, style)
                .toAnsi();
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ===== WebSocket 客户端实现 =====

    private class TuiWebSocketClient extends WebSocketClient {

        public TuiWebSocketClient(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            connected = true;
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonNode json = objectMapper.readTree(message);

                // 提取 sessionId
                if ("CONNECTED".equals(json.path("type").asText())) {
                    sessionId = json.path("sessionId").asText();
                }

                messageQueue.offer(json);

            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            connected = false;
            terminal.writer().println();
            terminal.writer().println("已断开连接：" + reason);
            terminal.writer().flush();
        }

        @Override
        public void onError(Exception ex) {
            connected = false;
            terminal.writer().println("WebSocket 错误：" + ex.getMessage());
            terminal.writer().flush();
        }
    }

    // ===== Main 方法 =====

    public static void main(String[] args) throws Exception {
        String serverUrl = "ws://localhost:8080/ws/agent";
        String authToken = null;

        // 支持命令行参数
        for (int i = 0; i < args.length; i++) {
            if ("-s".equals(args[i]) || "--server".equals(args[i]) && i + 1 < args.length) {
                serverUrl = args[++i];
            } else if ("-t".equals(args[i]) || "--token".equals(args[i]) && i + 1 < args.length) {
                authToken = args[++i];
            }
        }

        AgentTuiClient tui = new AgentTuiClient(serverUrl, authToken);
        tui.run();
    }
}
