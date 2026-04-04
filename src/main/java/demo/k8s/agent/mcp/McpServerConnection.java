package demo.k8s.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * MCP 服务器连接 - 封装与单个 MCP 服务器的连接。
 * <p>
 * 支持传输：
 * - stdio: 子进程通信
 * - SSE: Server-Sent Events
 * - WebSocket: WebSocket 通信
 */
public class McpServerConnection {

    private static final Logger log = LoggerFactory.getLogger(McpServerConnection.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final McpClientService.McpServerConfig config;
    private final RestClient restClient;
    private final Map<String, ToolCallback> toolCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Object> serverInfo = new ConcurrentHashMap<>();

    private volatile boolean connected = false;
    private volatile Process stdioProcess;
    private volatile Thread stdioReaderThread;
    private final Map<Object, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    public McpServerConnection(McpClientService.McpServerConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .build();
    }

    /**
     * 连接到服务器
     */
    public void connect() throws IOException {
        log.info("连接到 MCP 服务器：{} ({})", config.getName(), config.getUrl());

        switch (config.getTransport()) {
            case "stdio" -> connectStdio();
            case "sse" -> connectSse();
            case "websocket" -> connectWebSocket();
            default -> throw new IllegalArgumentException("Unsupported transport: " + config.getTransport());
        }

        // 获取服务器信息
        fetchServerInfo();

        connected = true;
    }

    /**
     * 断开连接
     */
    public void close() {
        log.info("断开 MCP 服务器连接：{}", config.getName());

        if (stdioReaderThread != null) {
            stdioReaderThread.interrupt();
        }
        if (stdioProcess != null) {
            stdioProcess.destroyForcibly();
        }

        // 完成所有 pending 请求
        for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
            future.complete(null);
        }
        pendingRequests.clear();

        connected = false;
        toolCallbacks.clear();
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        if (!connected) {
            return false;
        }

        try {
            // 尝试调用 ping 方法
            JsonNode result = callTool("ping", Map.of());
            return result != null;
        } catch (Exception e) {
            log.warn("健康检查失败：{} - {}", config.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 发现工具
     */
    public List<ToolCallback> discoverTools() {
        if (!connected) {
            return List.of();
        }

        List<ToolCallback> discovered = new ArrayList<>();

        try {
            // 调用 MCP list_tools 方法
            JsonNode response = callMethod("tools/list", Map.of());

            if (response != null && response.has("tools")) {
                JsonNode toolsNode = response.get("tools");

                for (JsonNode toolNode : toolsNode) {
                    String toolName = toolNode.path("name").asText();
                    String description = toolNode.path("description").asText();
                    JsonNode inputSchema = toolNode.path("inputSchema");

                    if (toolName.isEmpty()) {
                        continue;
                    }

                    // 创建 ToolCallback
                    ToolCallback callback = createMcpToolCallback(toolName, description, inputSchema);
                    toolCallbacks.put(toolName, callback);
                    discovered.add(callback);

                    log.debug("发现 MCP 工具：{}", toolName);
                }
            }
        } catch (Exception e) {
            log.error("发现 MCP 工具失败：{} - {}", config.getName(), e.getMessage());
        }

        return discovered;
    }

    /**
     * 获取工具回调列表
     */
    public List<ToolCallback> getToolCallbacks() {
        return new ArrayList<>(toolCallbacks.values());
    }

    /**
     * 获取服务器信息
     */
    public Map<String, Object> getServerInfo() {
        return Map.copyOf(serverInfo);
    }

    // ===== 内部方法 =====

    private void connectStdio() throws IOException {
        log.info("使用 stdio 方式连接 MCP 服务器：{}", config.getName());

        // 构建命令（从 URL 解析）
        // 例如：url = "stdio://npx -y @modelcontextprotocol/server-filesystem /workspace"
        String[] command = parseStdioCommand(config.getUrl());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            pb.environment().putAll(config.getEnv());
        }

        stdioProcess = pb.start();

        // 启动读取线程处理 stdout
        stdioReaderThread = new Thread(this::readStdioOutput, "MCP-Stdio-Reader-" + config.getName());
        stdioReaderThread.setDaemon(true);
        stdioReaderThread.start();

        log.info("Stdio 进程已启动：PID {}", stdioProcess.pid());
    }

    /**
     * 读取 stdio 输出并解析 JSON-RPC 响应
     */
    private void readStdioOutput() {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(stdioProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

            String line;
            while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    // 解析 JSON-RPC 响应
                    JsonNode response = OBJECT_MAPPER.readTree(line);
                    handleJsonRpcResponse(response);
                } catch (Exception e) {
                    log.warn("解析 JSON-RPC 响应失败：{}", e.getMessage());
                }
            }
        } catch (Exception e) {
            if (connected) {
                log.error("读取 stdio 输出失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 处理 JSON-RPC 响应
     */
    private void handleJsonRpcResponse(JsonNode response) {
        if (!response.has("id")) {
            // 可能是通知（notification），忽略
            return;
        }

        Object id = response.get("id").asText();
        CompletableFuture<JsonNode> future = pendingRequests.remove(id);
        if (future != null) {
            if (response.has("error")) {
                log.error("JSON-RPC 错误：{}", response.get("error"));
                future.complete(null);
            } else {
                future.complete(response.get("result"));
            }
        }
    }

    private void connectSse() {
        log.info("使用 SSE 方式连接 MCP 服务器：{}", config.getUrl());

        // SSE 方式通过 HTTP 轮询实现
        // 首先发送 endpoint 请求获取会话信息
        try {
            String endpointResponse = restClient.get()
                    .uri(config.getUrl() + "/sse")
                    .retrieve()
                    .body(String.class);

            // 解析 endpoint 响应，获取 session ID
            log.debug("SSE endpoint 响应：{}", endpointResponse);

            // SSE 连接建立后，后续通过 HTTP POST 发送 JSON-RPC 消息
            connected = true;
        } catch (Exception e) {
            log.error("SSE 连接失败：{}", e.getMessage());
            throw new RuntimeException("Failed to connect via SSE", e);
        }
    }

    private void connectWebSocket() {
        log.info("使用 WebSocket 方式连接 MCP 服务器：{}", config.getUrl());

        // WebSocket 连接需要专门的客户端，这里使用简单的 HTTP 轮询作为降级方案
        // 在实际生产环境中，应该使用 Spring WebSocket 客户端
        try {
            // 尝试通过 HTTP 获取服务器信息
            String infoResponse = restClient.get()
                    .uri(config.getUrl())
                    .retrieve()
                    .body(String.class);

            log.debug("WebSocket server info: {}", infoResponse);
            connected = true;
        } catch (Exception e) {
            log.error("WebSocket 连接失败：{}", e.getMessage());
            throw new RuntimeException("Failed to connect via WebSocket", e);
        }
    }

    private void fetchServerInfo() {
        try {
            JsonNode info = callMethod("initialize", Map.of(
                    "protocolVersion", "2024-11-05",
                    "clientInfo", Map.of(
                            "name", "minimal-k8s-agent-demo",
                            "version", "0.1.0"
                    )
            ));

            if (info != null) {
                serverInfo.put("serverInfo", jsonToMap(info.path("serverInfo")));
                serverInfo.put("protocolVersion", info.path("protocolVersion").asText());
            }
        } catch (Exception e) {
            log.warn("获取服务器信息失败：{}", e.getMessage());
        }
    }

    private JsonNode callMethod(String method, Map<String, Object> params) {
        try {
            String requestId = String.valueOf(System.currentTimeMillis());
            Map<String, Object> request = Map.of(
                    "jsonrpc", JSON_RPC_VERSION,
                    "method", method,
                    "params", params,
                    "id", requestId
            );

            String requestBody = OBJECT_MAPPER.writeValueAsString(request);
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingRequests.put(requestId, future);

            if ("sse".equals(config.getTransport()) || "websocket".equals(config.getTransport())) {
                // HTTP POST 到 MCP 端点
                try {
                    String response = restClient.post()
                            .uri(config.getUrl() + "/message")
                            .header("Content-Type", "application/json")
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

                    JsonNode result = OBJECT_MAPPER.readTree(response);
                    if (result.has("result")) {
                        return result.get("result");
                    }
                    return result;
                } catch (Exception e) {
                    log.error("HTTP 请求失败：{}", e.getMessage());
                    pendingRequests.remove(requestId);
                    return null;
                }
            } else {
                // stdio: 写入进程输入流
                if (stdioProcess != null) {
                    synchronized (stdioProcess.getOutputStream()) {
                        stdioProcess.getOutputStream().write((requestBody + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        stdioProcess.getOutputStream().flush();
                    }

                    // 等待响应（带超时）
                    try {
                        return future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (java.util.concurrent.TimeoutException e) {
                        log.error("等待响应超时：{}", method);
                        pendingRequests.remove(requestId);
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            log.error("调用 MCP 方法失败：{} - {}", method, e.getMessage());
        }

        return null;
    }

    /**
     * 调用 MCP 工具
     */
    public JsonNode callTool(String toolName, Map<String, Object> arguments) {
        return callMethod("tools/call", Map.of(
                "name", toolName,
                "arguments", arguments
        ));
    }

    private ToolCallback createMcpToolCallback(String name, String description, JsonNode inputSchema) {
        // 创建 ToolDefinition
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(description != null ? description : "MCP tool: " + name)
                .inputSchema(inputSchema != null ? inputSchema.toString() : "{}")
                .build();

        // 创建 McpToolCall 包装器
        McpToolCall toolCall = new McpToolCall(config.getName(), name, this, definition);

        // 使用 FunctionToolCallback 创建 ToolCallback
        // FunctionToolCallback.builder 需要 (toolName, function) 两个参数
        return FunctionToolCallback.builder(name, (Function<String, String>) toolCall)
                .description(description != null ? description : "MCP tool: " + name)
                .inputSchema(inputSchema != null ? inputSchema.toString() : "{}")
                .build();
    }

    private String[] parseStdioCommand(String url) {
        // 解析 stdio:// 后面的命令
        // 例如：stdio://npx -y @modelcontextprotocol/server-filesystem /workspace
        String command = url.substring("stdio://".length());
        return command.split("\\s+");
    }

    private Map<String, Object> jsonToMap(JsonNode node) {
        return OBJECT_MAPPER.convertValue(node, Map.class);
    }

    /**
     * 获取服务器配置
     */
    public McpClientService.McpServerConfig getConfig() {
        return config;
    }
}
