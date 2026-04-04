package demo.k8s.agent.toolsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.metrics.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionManager 测试
 */
class PermissionManagerTest {

    private PermissionManager permissionManager;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 清除持久化授权文件，确保测试隔离
        try {
            java.nio.file.Files.deleteIfExists(
                java.nio.file.Path.of(System.getProperty("user.home"), ".claude", "permission-grants.json"));
        } catch (Exception e) {
            // 忽略删除失败
        }

        // 创建测试用的依赖项
        EventBus eventBus = new EventBus();
        MetricsCollector metricsCollector = new MetricsCollector(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        permissionManager = new PermissionManager(eventBus, metricsCollector);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testPermissionManagerCreation() {
        assertNotNull(permissionManager);
        assertTrue(permissionManager.getPendingRequests().isEmpty());
        assertTrue(permissionManager.getSessionGrants().isEmpty());
    }

    @Test
    void testRequiresPermissionBypassMode() throws Exception {
        ClaudeLikeTool tool = createMockTool("test_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.BYPASS,
                Set.of(),
                Set.of()
        );

        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);

        // BYPASS 模式应该直接放行
        assertNull(request);
    }

    @Test
    void testRequiresPermissionReadOnlyModeWithReadOnlyTool() throws Exception {
        ClaudeLikeTool tool = createReadOnlyTool("read_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.READ_ONLY,
                Set.of(),
                Set.of()
        );

        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);

        // 只读工具在 READ_ONLY 模式下应该放行
        assertNull(request);
    }

    @Test
    void testRequiresPermissionReadOnlyModeWithWriteTool() throws Exception {
        ClaudeLikeTool tool = createWriteTool("write_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.READ_ONLY,
                Set.of(),
                Set.of()
        );

        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);

        // 写工具在 READ_ONLY 模式下需要确认
        assertNotNull(request);
        assertEquals("write_tool", request.toolName());
    }

    @Test
    void testRequiresPermissionWithAlwaysAllowedTool() throws Exception {
        ClaudeLikeTool tool = createWriteTool("allowed_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        // 先授权
        PermissionRequest initialRequest = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        assertNotNull(initialRequest);

        PermissionResponse response = new PermissionResponse(
                initialRequest.id(),
                PermissionChoice.ALLOW_ALWAYS,
                null,
                null
        );
        permissionManager.handlePermissionResponse(response);

        // 再次检查，应该直接放行
        PermissionRequest request2 = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        assertNull(request2);
    }

    @Test
    void testRequiresPermissionWithSessionGrant() throws Exception {
        ClaudeLikeTool tool = createWriteTool("session_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );

        // 第一次需要确认
        PermissionRequest request1 = permissionManager.requiresPermission(tool, input, ctx);
        assertNotNull(request1);

        // 授权会话
        PermissionResponse response = new PermissionResponse(
                request1.id(),
                PermissionChoice.ALLOW_ONCE,
                null,
                null
        );
        PermissionResult result = permissionManager.handlePermissionResponse(response);
        assertTrue(result instanceof PermissionResult.Allow);

        // 第二次应该直接放行（有会话授权）
        PermissionRequest request2 = permissionManager.requiresPermission(tool, input, ctx);
        assertNull(request2);
    }

    @Test
    void testHandlePermissionResponseDeny() throws Exception {
        ClaudeLikeTool tool = createWriteTool("deny_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        PermissionRequest request = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        assertNotNull(request);

        PermissionResponse response = new PermissionResponse(
                request.id(),
                PermissionChoice.DENY,
                null,
                null
        );

        PermissionResult result = permissionManager.handlePermissionResponse(response);

        assertTrue(result instanceof PermissionResult.Deny);
        assertTrue(permissionManager.getPendingRequests().isEmpty());
    }

    @Test
    void testHandlePermissionResponseAllowOnce() throws Exception {
        ClaudeLikeTool tool = createWriteTool("once_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        PermissionRequest request = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        assertNotNull(request);

        PermissionResponse response = new PermissionResponse(
                request.id(),
                PermissionChoice.ALLOW_ONCE,
                null,
                null
        );

        PermissionResult result = permissionManager.handlePermissionResponse(response);

        assertTrue(result instanceof PermissionResult.Allow);
        assertFalse(permissionManager.getSessionGrants().isEmpty());
    }

    @Test
    void testHandlePermissionResponseAllowSession() throws Exception {
        ClaudeLikeTool tool = createWriteTool("session_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        PermissionRequest request = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        assertNotNull(request);

        PermissionResponse response = new PermissionResponse(
                request.id(),
                PermissionChoice.ALLOW_SESSION,
                30,
                null
        );

        PermissionResult result = permissionManager.handlePermissionResponse(response);

        assertTrue(result instanceof PermissionResult.Allow);
        assertFalse(permissionManager.getSessionGrants().isEmpty());
    }

    @Test
    void testHandlePermissionResponseAllowAlways() throws Exception {
        ClaudeLikeTool tool = createWriteTool("always_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        PermissionRequest request = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        assertNotNull(request);

        PermissionResponse response = new PermissionResponse(
                request.id(),
                PermissionChoice.ALLOW_ALWAYS,
                null,
                null
        );

        PermissionResult result = permissionManager.handlePermissionResponse(response);

        assertTrue(result instanceof PermissionResult.Allow);
        assertTrue(permissionManager.getAlwaysAllowedTools().contains("always_tool"));
    }

    @Test
    void testHandlePermissionResponseWithInvalidRequestId() {
        PermissionResponse response = new PermissionResponse(
                "invalid-id",
                PermissionChoice.DENY,
                null,
                null
        );

        PermissionResult result = permissionManager.handlePermissionResponse(response);

        assertTrue(result instanceof PermissionResult.Deny);
        assertTrue(result.getDenyReason().contains("未找到权限请求"));
    }

    @Test
    void testGetPendingRequests() throws Exception {
        ClaudeLikeTool tool1 = createWriteTool("tool1", ToolCategory.FILE);
        ClaudeLikeTool tool2 = createWriteTool("tool2", ToolCategory.SHELL);
        JsonNode input = objectMapper.createObjectNode();

        permissionManager.requiresPermission(tool1, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        permissionManager.requiresPermission(tool2, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));

        List<PermissionRequest> pending = permissionManager.getPendingRequests();

        assertEquals(2, pending.size());
    }

    @Test
    void testClearPendingRequest() throws Exception {
        ClaudeLikeTool tool = createWriteTool("tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        PermissionRequest request = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        assertNotNull(request);

        permissionManager.clearPendingRequest(request.id());

        assertTrue(permissionManager.getPendingRequests().isEmpty());
    }

    @Test
    void testRevokeAlwaysAllowed() throws Exception {
        ClaudeLikeTool tool = createWriteTool("revoke_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        // 先授权
        PermissionRequest request = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        PermissionResponse response = new PermissionResponse(
                request.id(),
                PermissionChoice.ALLOW_ALWAYS,
                null,
                null
        );
        permissionManager.handlePermissionResponse(response);

        assertTrue(permissionManager.getAlwaysAllowedTools().contains("revoke_tool"));

        // 撤销
        permissionManager.revokeAlwaysAllowed("revoke_tool");

        assertFalse(permissionManager.getAlwaysAllowedTools().contains("revoke_tool"));
    }

    @Test
    void testClearSessionGrants() throws Exception {
        ClaudeLikeTool tool = createWriteTool("session_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        PermissionRequest request = permissionManager.requiresPermission(tool, input,
                new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of()));
        PermissionResponse response = new PermissionResponse(
                request.id(),
                PermissionChoice.ALLOW_ONCE,
                null,
                null
        );
        permissionManager.handlePermissionResponse(response);

        assertFalse(permissionManager.getSessionGrants().isEmpty());

        permissionManager.clearSessionGrants();

        assertTrue(permissionManager.getSessionGrants().isEmpty());
    }

    @Test
    void testRequiresPermissionWithDeniedToolInContext() throws Exception {
        ClaudeLikeTool tool = createWriteTool("denied_tool", ToolCategory.FILE);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of("denied_tool")
        );

        // 被拒绝的工具仍会创建权限请求（由上层处理拒绝）
        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);
        assertNotNull(request);
    }

    @Test
    void testRequiresPermissionWithAgentTool() throws Exception {
        ClaudeLikeTool tool = createMockTool("agent_tool", ToolCategory.AGENT);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );

        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);

        assertNotNull(request);
        assertEquals(PermissionLevel.AGENT_SPAWN, request.level());
    }

    @Test
    void testRequiresPermissionWithExternalTool() throws Exception {
        ClaudeLikeTool tool = createMockTool("external_tool", ToolCategory.EXTERNAL);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );

        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);

        assertNotNull(request);
        assertEquals(PermissionLevel.NETWORK, request.level());
    }

    @Test
    void testRequiresPermissionWithPlanningTool() throws Exception {
        ClaudeLikeTool tool = createMockTool("planning_tool", ToolCategory.PLANNING);
        JsonNode input = objectMapper.createObjectNode();

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );

        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);

        assertNotNull(request);
        assertEquals(PermissionLevel.READ_ONLY, request.level());
    }

    // ===== 辅助方法 =====

    private ClaudeLikeTool createMockTool(String name, ToolCategory category) {
        return new ClaudeLikeTool() {
            @Override
            public String name() { return name; }
            @Override
            public ToolCategory category() { return category; }
            @Override
            public String description() { return "Test tool"; }
            @Override
            public String inputSchemaJson() { return "{}"; }
            @Override
            public boolean isReadOnly(JsonNode input) { return false; }
        };
    }

    private ClaudeLikeTool createReadOnlyTool(String name, ToolCategory category) {
        return new ClaudeLikeTool() {
            @Override
            public String name() { return name; }
            @Override
            public ToolCategory category() { return category; }
            @Override
            public String description() { return "Read-only tool"; }
            @Override
            public String inputSchemaJson() { return "{}"; }
            @Override
            public boolean defaultReadOnlyHint() { return true; }
        };
    }

    private ClaudeLikeTool createWriteTool(String name, ToolCategory category) {
        return new ClaudeLikeTool() {
            @Override
            public String name() { return name; }
            @Override
            public ToolCategory category() { return category; }
            @Override
            public String description() { return "Write tool"; }
            @Override
            public String inputSchemaJson() { return "{}"; }
            @Override
            public boolean defaultReadOnlyHint() { return false; }
            @Override
            public boolean isReadOnly(JsonNode input) { return false; }
        };
    }
}
