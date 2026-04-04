package demo.k8s.agent.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 管理 HTTP API
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpClientService mcpClientService;
    private final McpProperties mcpProperties;

    public McpController(McpClientService mcpClientService, McpProperties mcpProperties) {
        this.mcpClientService = mcpClientService;
        this.mcpProperties = mcpProperties;
    }

    /**
     * 获取所有 MCP 服务器状态
     */
    @GetMapping("/servers")
    public List<McpClientService.McpServerStatus> getServerStatuses() {
        return mcpClientService.getAllServerStatuses();
    }

    /**
     * 注册新的 MCP 服务器
     */
    @PostMapping("/servers/register")
    public ResponseEntity<?> registerServer(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String url = (String) request.get("url");
        String transport = (String) request.getOrDefault("transport", "stdio");
        boolean autoConnect = (boolean) request.getOrDefault("autoConnect", true);

        if (name == null || url == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "name and url are required"));
        }

        McpClientService.McpServerConfig config = McpClientService.builder()
                .name(name)
                .url(url)
                .transport(transport)
                .autoConnect(autoConnect)
                .build();

        mcpClientService.registerServer(config);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Server registered: " + name
        ));
    }

    /**
     * 连接到 MCP 服务器
     */
    @PostMapping("/servers/connect")
    public ResponseEntity<?> connectToServer(@RequestBody Map<String, String> request) {
        String serverName = request.get("serverName");

        if (serverName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "serverName is required"));
        }

        boolean success = mcpClientService.connectToServer(serverName);

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Connected to server: " + serverName
            ));
        } else {
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to connect to server: " + serverName
                    ));
        }
    }

    /**
     * 断开 MCP 服务器连接
     */
    @PostMapping("/servers/disconnect")
    public ResponseEntity<?> disconnectFromServer(@RequestBody Map<String, String> request) {
        String serverName = request.get("serverName");

        if (serverName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "serverName is required"));
        }

        mcpClientService.disconnectFromServer(serverName);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Disconnected from server: " + serverName
        ));
    }

    /**
     * 获取 MCP 服务器发现的工具列表
     */
    @GetMapping("/servers/{serverName}/tools")
    public List<Map<String, Object>> getServerTools(@PathVariable String serverName) {
        McpClientService.McpServerStatus status = mcpClientService.getServerStatus(serverName);
        if (status == null) {
            return List.of();
        }

        return status.getTools().stream()
                .<Map<String, Object>>map(tool -> Map.of("name", tool))
                .toList();
    }

    /**
     * 执行健康检查
     */
    @PostMapping("/servers/{serverName}/health")
    public ResponseEntity<?> runHealthCheck(@PathVariable String serverName) {
        mcpClientService.runHealthCheck(serverName);

        McpClientService.McpServerStatus status = mcpClientService.getServerStatus(serverName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "status", status
        ));
    }

    /**
     * 获取 MCP 配置
     */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return Map.of(
                "enabled", mcpProperties.isEnabled(),
                "servers", mcpProperties.getServers().stream()
                        .filter(s -> s.isEnabled())
                        .map(s -> Map.of(
                                "name", s.getName(),
                                "url", s.getUrl(),
                                "transport", s.getTransport(),
                                "autoConnect", s.isAutoConnect()))
                        .toList()
        );
    }
}
