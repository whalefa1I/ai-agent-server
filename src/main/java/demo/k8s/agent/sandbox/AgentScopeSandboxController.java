package demo.k8s.agent.sandbox;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AgentScope 沙盒管理 HTTP API
 *
 * @deprecated 暂时禁用，修复 bean 冲突问题
 */
// @RestController  // 已禁用 - 注释掉以避免 bean 冲突
@RequestMapping("/api/sandbox")
@org.springframework.context.annotation.Profile("sandbox")  // 仅在 sandbox profile 启用
public class AgentScopeSandboxController {

    private final AgentScopeSandboxService sandboxService;
    private final AgentScopeSandboxProperties properties;

    public AgentScopeSandboxController(AgentScopeSandboxService sandboxService,
                                       AgentScopeSandboxProperties properties) {
        this.sandboxService = sandboxService;
        this.properties = properties;
    }

    /**
     * 创建新的沙盒会话
     */
    @PostMapping("/session/create")
    public ResponseEntity<?> createSession(@RequestBody Map<String, String> request) {
        String sandboxType = request.getOrDefault("sandbox_type", properties.getDefaultSandboxType());
        String sessionId = request.get("session_id");
        String userId = request.get("user_id");

        if (userId == null) {
            userId = "default_user";
        }

        SandboxSessionInfo session = sandboxService.createSession(sandboxType, sessionId, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "session", session
        ));
    }

    /**
     * 关闭沙盒会话
     */
    @PostMapping("/session/close")
    public ResponseEntity<?> closeSession(@RequestBody Map<String, String> request) {
        String sessionId = request.get("session_id");
        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "session_id is required"
            ));
        }

        sandboxService.closeSession(sessionId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 获取活跃会话列表
     */
    @GetMapping("/sessions")
    public List<SandboxSessionInfo> getActiveSessions() {
        return sandboxService.getActiveSessions();
    }

    /**
     * 执行 Python 代码
     */
    @PostMapping("/execute/python")
    public ResponseEntity<?> executePython(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("session_id");
        String code = (String) request.get("code");

        if (sessionId == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "session_id and code are required"
            ));
        }

        SandboxToolResult result = sandboxService.executePython(sessionId, code);
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "output", result.output()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", result.error()
            ));
        }
    }

    /**
     * 执行 Shell 命令
     */
    @PostMapping("/execute/shell")
    public ResponseEntity<?> executeShell(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("session_id");
        String command = (String) request.get("command");

        if (sessionId == null || command == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "session_id and command are required"
            ));
        }

        SandboxToolResult result = sandboxService.executeShell(sessionId, command);
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "output", result.output()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", result.error()
            ));
        }
    }

    /**
     * 列出可用工具
     */
    @GetMapping("/tools")
    public List<SandboxToolDefinition> listTools(@RequestParam String session_id) {
        return sandboxService.listTools(session_id);
    }

    /**
     * 调用沙盒工具
     */
    @PostMapping("/tool/call")
    public ResponseEntity<?> callTool(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("session_id");
        String toolName = (String) request.get("tool_name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");

        if (sessionId == null || toolName == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "session_id and tool_name are required"
            ));
        }

        SandboxToolResult result = sandboxService.callTool(sessionId, toolName, arguments);
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "output", result.output(),
                    "content", result.content(),
                    "metadata", result.metadata()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", result.error()
            ));
        }
    }

    /**
     * 添加 MCP 服务器
     */
    @PostMapping("/mcp/add")
    public ResponseEntity<?> addMcpServer(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("session_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) request.get("server_config");

        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "session_id is required"
            ));
        }

        boolean success = sandboxService.addMcpServer(sessionId, serverConfig);
        return ResponseEntity.ok(Map.of("success", success));
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        boolean healthy = sandboxService.isHealthy();
        if (healthy) {
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "service", "agentscope-sandbox",
                    "activeSessions", sandboxService.getActiveSessions().size()
            ));
        } else {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "unhealthy",
                    "service", "agentscope-sandbox"
            ));
        }
    }
}
