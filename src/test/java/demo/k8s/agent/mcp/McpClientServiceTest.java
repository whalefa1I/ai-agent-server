package demo.k8s.agent.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 系统单元测试
 */
@DisplayName("MCP 系统测试")
class McpClientServiceTest {

    private McpClientService mcpClientService;

    @BeforeEach
    void setUp() {
        mcpClientService = new McpClientService();
    }

    @Test
    @DisplayName("测试 MCP 服务注册")
    void testMcpClientService_RegisterServer() {
        McpClientService.McpServerConfig config = McpClientService.builder()
                .name("test-server")
                .url("stdio://echo test")
                .transport("stdio")
                .autoConnect(false)
                .build();

        mcpClientService.registerServer(config);

        McpClientService.McpServerStatus status = mcpClientService.getServerStatus("test-server");
        assertNotNull(status);
        assertEquals("test-server", status.getName());
        assertFalse(status.isConnected());
    }

    @Test
    @DisplayName("测试 MCP 服务器状态 - 初始状态")
    void testMcpClientService_InitialServerStatus() {
        McpClientService.McpServerConfig config = McpClientService.builder()
                .name("status-test-server")
                .url("stdio://echo test")
                .transport("stdio")
                .autoConnect(false)
                .build();

        mcpClientService.registerServer(config);

        McpClientService.McpServerStatus status = mcpClientService.getServerStatus("status-test-server");
        assertNotNull(status);
        assertFalse(status.isConnected());
        assertTrue(status.getTools().isEmpty());
        assertNull(status.getLastError());
        assertTrue(status.getLastHealthCheck() > 0);
    }

    @Test
    @DisplayName("测试 MCP 服务器配置构建器")
    void testMcpClientService_Builder() {
        McpClientService.McpServerConfig config = McpClientService.builder()
                .name("builder-test")
                .url("sse://https://mcp.example.com")
                .transport("sse")
                .headers(Map.of("Authorization", "Bearer token"))
                .env(Map.of("API_KEY", "secret"))
                .autoConnect(true)
                .build();

        assertEquals("builder-test", config.getName());
        assertEquals("sse://https://mcp.example.com", config.getUrl());
        assertEquals("sse", config.getTransport());
        assertEquals("Bearer token", config.getHeaders().get("Authorization"));
        assertEquals("secret", config.getEnv().get("API_KEY"));
        assertTrue(config.isAutoConnect());
    }

    @Test
    @DisplayName("测试获取所有服务器状态")
    void testMcpClientService_GetAllServerStatuses() {
        // 注册多个服务器
        mcpClientService.registerServer(McpClientService.builder()
                .name("server1")
                .url("stdio://echo 1")
                .autoConnect(false)
                .build());
        mcpClientService.registerServer(McpClientService.builder()
                .name("server2")
                .url("stdio://echo 2")
                .autoConnect(false)
                .build());

        List<McpClientService.McpServerStatus> statuses = mcpClientService.getAllServerStatuses();
        assertEquals(2, statuses.size());
    }

    @Test
    @DisplayName("测试断开未连接的服务器")
    void testMcpClientService_DisconnectUnconnectedServer() {
        McpClientService.McpServerConfig config = McpClientService.builder()
                .name("disconnect-test")
                .url("stdio://echo test")
                .autoConnect(false)
                .build();

        mcpClientService.registerServer(config);

        // 断开未连接的服务器应该不会抛出异常
        assertDoesNotThrow(() -> mcpClientService.disconnectFromServer("disconnect-test"));
    }

    @Test
    @DisplayName("测试获取未知服务器状态")
    void testMcpClientService_GetUnknownServerStatus() {
        McpClientService.McpServerStatus status = mcpClientService.getServerStatus("unknown-server");
        assertNull(status);
    }

    @Test
    @DisplayName("测试 MCP 服务器配置 - stdio 传输")
    void testMcpServerConnection_StdioTransport() {
        McpClientService.McpServerConfig config = McpClientService.builder()
                .name("stdio-test")
                .url("stdio://echo hello")
                .transport("stdio")
                .build();

        assertEquals("stdio", config.getTransport());
        assertTrue(config.getUrl().startsWith("stdio://"));
    }

    @Test
    @DisplayName("测试 MCP 服务器配置 - SSE 传输")
    void testMcpServerConnection_SseTransport() {
        McpClientService.McpServerConfig config = McpClientService.builder()
                .name("sse-test")
                .url("https://mcp.example.com")
                .transport("sse")
                .build();

        assertEquals("sse", config.getTransport());
    }

    @Test
    @DisplayName("测试 MCP 服务器配置 - WebSocket 传输")
    void testMcpServerConnection_WebSocketTransport() {
        McpClientService.McpServerConfig config = McpClientService.builder()
                .name("ws-test")
                .url("ws://localhost:8080/mcp")
                .transport("websocket")
                .build();

        assertEquals("websocket", config.getTransport());
    }
}
