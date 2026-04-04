package demo.k8s.agent.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务管理器 - 管理 MCP 服务器连接和工具发现。
 * <p>
 * 功能：
 * - MCP 服务器注册与连接
 * - 工具动态发现
 * - 服务器健康检查
 * - OAuth 认证管理
 */
@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    /**
     * MCP 服务器配置
     */
    public record McpServerConfig(
            String name,
            String url,
            String transport, // "stdio" | "sse" | "websocket"
            Map<String, String> headers,
            Map<String, String> env,
            boolean autoConnect
    ) {
        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getTransport() { return transport; }
        public Map<String, String> getHeaders() { return headers; }
        public Map<String, String> getEnv() { return env; }
        public boolean isAutoConnect() { return autoConnect; }
    }

    /**
     * MCP 服务器状态
     */
    public record McpServerStatus(
            String name,
            boolean connected,
            List<String> tools,
            String lastError,
            long lastHealthCheck
    ) {
        public String getName() { return name; }
        public boolean isConnected() { return connected; }
        public List<String> getTools() { return tools; }
        public String getLastError() { return lastError; }
        public long getLastHealthCheck() { return lastHealthCheck; }
    }

    // 已注册的服务器配置
    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();

    // 已连接的服务器（实际客户端）
    private final Map<String, McpServerConnection> serverConnections = new ConcurrentHashMap<>();

    // 服务器状态缓存
    private final Map<String, McpServerStatus> serverStatuses = new ConcurrentHashMap<>();

    public McpClientService() {
        // 启动健康检查线程
        startHealthChecker();
    }

    /**
     * 注册 MCP 服务器
     */
    public void registerServer(McpServerConfig config) {
        log.info("注册 MCP 服务器：{} ({})", config.name, config.url);
        serverConfigs.put(config.name, config);
        serverStatuses.put(config.name, new McpServerStatus(
                config.name, false, List.of(), null, System.currentTimeMillis()
        ));

        if (config.autoConnect()) {
            connectToServer(config.name());
        }
    }

    /**
     * 连接到 MCP 服务器
     */
    public boolean connectToServer(String serverName) {
        McpServerConfig config = serverConfigs.get(serverName);
        if (config == null) {
            log.error("未知的 MCP 服务器：{}", serverName);
            return false;
        }

        try {
            log.info("正在连接 MCP 服务器：{}", serverName);

            // 创建连接（实际实现会使用 Spring AI MCP Client）
            McpServerConnection connection = new McpServerConnection(config);
            connection.connect();

            serverConnections.put(serverName, connection);

            // 更新状态
            List<String> tools = connection.discoverTools().stream()
                    .map(tc -> tc.getToolDefinition().name())
                    .toList();
            updateServerStatus(serverName, true, tools, null);

            log.info("成功连接到 MCP 服务器：{}, 发现 {} 个工具", serverName, tools.size());
            return true;

        } catch (Exception e) {
            log.error("连接 MCP 服务器失败：{} - {}", serverName, e.getMessage());
            updateServerStatus(serverName, false, List.of(), e.getMessage());
            return false;
        }
    }

    /**
     * 断开 MCP 服务器连接
     */
    public void disconnectFromServer(String serverName) {
        McpServerConnection connection = serverConnections.remove(serverName);
        if (connection != null) {
            connection.close();
            updateServerStatus(serverName, false, List.of(), null);
            log.info("已断开 MCP 服务器连接：{}", serverName);
        }
    }

    /**
     * 获取 MCP 服务器发现的所有工具
     */
    public List<ToolCallback> getDiscoveredTools() {
        List<ToolCallback> allTools = new ArrayList<>();

        for (McpServerConnection connection : serverConnections.values()) {
            if (connection.isConnected()) {
                allTools.addAll(connection.getToolCallbacks());
            }
        }

        return allTools;
    }

    /**
     * 获取 MCP 服务器状态
     */
    public McpServerStatus getServerStatus(String serverName) {
        return serverStatuses.get(serverName);
    }

    /**
     * 获取所有服务器状态
     */
    public List<McpServerStatus> getAllServerStatuses() {
        return new ArrayList<>(serverStatuses.values());
    }

    /**
     * 执行服务器健康检查
     */
    public void runHealthCheck(String serverName) {
        McpServerConnection connection = serverConnections.get(serverName);
        if (connection == null || !connection.isConnected()) {
            return;
        }

        try {
            boolean healthy = connection.healthCheck();
            if (healthy) {
                List<String> tools = connection.discoverTools().stream()
                        .map(tc -> tc.getToolDefinition().name())
                        .toList();
                updateServerStatus(serverName, true, tools, null);
            } else {
                updateServerStatus(serverName, false, List.of(), "Health check failed");
            }
        } catch (Exception e) {
            updateServerStatus(serverName, false, List.of(), e.getMessage());
        }
    }

    /**
     * 执行所有服务器的健康检查
     */
    public void runAllHealthChecks() {
        for (String serverName : serverConnections.keySet()) {
            runHealthCheck(serverName);
        }
    }

    private void updateServerStatus(String serverName, boolean connected,
                                     List<String> tools, String error) {
        McpServerConfig config = serverConfigs.get(serverName);
        if (config == null) return;

        serverStatuses.put(serverName, new McpServerStatus(
                serverName,
                connected,
                tools,
                error,
                System.currentTimeMillis()
        ));
    }

    private void startHealthChecker() {
        Thread healthChecker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // 30 秒检查一次
                    runAllHealthChecks();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("健康检查失败", e);
                }
            }
        }, "MCP-HealthChecker");
        healthChecker.setDaemon(true);
        healthChecker.start();
    }

    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 服务器配置构建器
     */
    public static class Builder {
        private String name;
        private String url;
        private String transport = "stdio";
        private Map<String, String> headers = Map.of();
        private Map<String, String> env = Map.of();
        private boolean autoConnect = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder transport(String transport) {
            this.transport = transport;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder autoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
            return this;
        }

        public McpServerConfig build() {
            return new McpServerConfig(name, url, transport, headers, env, autoConnect);
        }
    }
}
