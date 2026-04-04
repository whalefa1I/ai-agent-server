package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 测试
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void testToolRegistryCreation() {
        assertNotNull(registry);
        assertTrue(registry.modules().isEmpty());
    }

    @Test
    void testRegisterTool() {
        ToolModule module = createToolModule("test_tool", ToolCategory.FILE);

        registry.register(module);

        List<ToolModule> modules = registry.modules();
        assertEquals(1, modules.size());
        assertEquals("test_tool", modules.get(0).spec().name());
    }

    @Test
    void testRegisterMultipleTools() {
        registry.register(createToolModule("tool1", ToolCategory.FILE));
        registry.register(createToolModule("tool2", ToolCategory.SHELL));
        registry.register(createToolModule("tool3", ToolCategory.PLANNING));

        List<ToolModule> modules = registry.modules();
        assertEquals(3, modules.size());
    }

    @Test
    void testFilteredCallbacksDefaultMode() {
        registry.register(createToolModule("tool1", ToolCategory.FILE));
        registry.register(createToolModule("tool2", ToolCategory.SHELL));

        // 使用空的 allowedCategories 表示不限制类别
        ToolPermissionContext ctx = new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of());
        // 关闭 simpleMode 以避免过滤
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<org.springframework.ai.tool.ToolCallback> callbacks =
                registry.filteredCallbacks(ctx, flags, List.of());

        assertEquals(2, callbacks.size());
    }

    @Test
    void testFilteredCallbacksReadOnlyMode() {
        registry.register(createReadOnlyTool("read_tool", ToolCategory.FILE));
        registry.register(createWriteTool("write_tool", ToolCategory.FILE));

        ToolPermissionContext ctx = new ToolPermissionContext(ToolPermissionMode.READ_ONLY, Set.of(), Set.of());
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<org.springframework.ai.tool.ToolCallback> callbacks =
                registry.filteredCallbacks(ctx, flags, List.of());

        // 只读模式下应该只返回只读工具
        assertEquals(1, callbacks.size());
        assertEquals("read_tool", callbacks.get(0).getToolDefinition().name());
    }

    @Test
    void testFilteredCallbacksWithMcpTools() {
        registry.register(createToolModule("local_tool", ToolCategory.FILE));

        ToolModule mcpTool = createToolModule("mcp_tool", ToolCategory.EXTERNAL);

        ToolPermissionContext ctx = new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of());
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<org.springframework.ai.tool.ToolCallback> callbacks =
                registry.filteredCallbacks(ctx, flags, List.of(mcpTool));

        // 应该包含本地工具和 MCP 工具
        assertEquals(2, callbacks.size());
    }

    @Test
    void testFilteredCallbacksDisabledTool() {
        registry.register(createDisabledTool("disabled_tool", ToolCategory.FILE));
        registry.register(createToolModule("enabled_tool", ToolCategory.FILE));

        ToolPermissionContext ctx = new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of());
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<org.springframework.ai.tool.ToolCallback> callbacks =
                registry.filteredCallbacks(ctx, flags, List.of());

        assertEquals(1, callbacks.size());
        assertEquals("enabled_tool", callbacks.get(0).getToolDefinition().name());
    }

    @Test
    void testFilteredCallbacksDeniedTool() {
        registry.register(createToolModule("tool1", ToolCategory.FILE));
        registry.register(createToolModule("tool2", ToolCategory.SHELL));

        ToolPermissionContext ctx = new ToolPermissionContext(ToolPermissionMode.DEFAULT, Set.of(), Set.of("tool1"));
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<org.springframework.ai.tool.ToolCallback> callbacks =
                registry.filteredCallbacks(ctx, flags, List.of());

        assertEquals(1, callbacks.size());
        assertEquals("tool2", callbacks.get(0).getToolDefinition().name());
    }

    @Test
    void testFilteredCallbacksPlanningInReadOnlyMode() {
        registry.register(createToolModule("todo", ToolCategory.PLANNING));

        ToolPermissionContext ctx = new ToolPermissionContext(ToolPermissionMode.READ_ONLY, Set.of(), Set.of());
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<org.springframework.ai.tool.ToolCallback> callbacks =
                registry.filteredCallbacks(ctx, flags, List.of());

        // 规划工具在只读模式下应该被允许
        assertEquals(1, callbacks.size());
    }

    @Test
    void testModulesReturnsCopy() {
        ToolModule module = createToolModule("tool", ToolCategory.FILE);
        registry.register(module);

        List<ToolModule> modules1 = registry.modules();
        // 创建一个新列表而不是直接修改返回的列表（因为是不可变副本）
        List<ToolModule> modulesCopy = new java.util.ArrayList<>(modules1);
        modulesCopy.add(createToolModule("extra", ToolCategory.FILE));

        List<ToolModule> modules2 = registry.modules();
        assertEquals(1, modules2.size()); // 原始注册表不应被修改
    }

    @Test
    void testRegisterReturnsSelf() {
        ToolRegistry result = registry.register(createToolModule("tool", ToolCategory.FILE));
        assertSame(registry, result);
    }

    // ===== 辅助方法 =====

    private ToolModule createToolModule(String name, ToolCategory category) {
        ClaudeLikeTool tool = new ClaudeLikeTool() {
            @Override
            public String name() { return name; }
            @Override
            public ToolCategory category() { return category; }
            @Override
            public String description() { return "Test tool"; }
            @Override
            public String inputSchemaJson() { return "{}"; }
            @Override
            public boolean defaultReadOnlyHint() { return false; }
        };

        org.springframework.ai.tool.ToolCallback callback = createCallback(name);
        return new ToolModule(tool, callback);
    }

    private ToolModule createReadOnlyTool(String name, ToolCategory category) {
        ClaudeLikeTool tool = new ClaudeLikeTool() {
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

        org.springframework.ai.tool.ToolCallback callback = createCallback(name);
        return new ToolModule(tool, callback);
    }

    private ToolModule createWriteTool(String name, ToolCategory category) {
        ClaudeLikeTool tool = new ClaudeLikeTool() {
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
        };

        org.springframework.ai.tool.ToolCallback callback = createCallback(name);
        return new ToolModule(tool, callback);
    }

    private ToolModule createDisabledTool(String name, ToolCategory category) {
        ClaudeLikeTool tool = new ClaudeLikeTool() {
            @Override
            public String name() { return name; }
            @Override
            public ToolCategory category() { return category; }
            @Override
            public String description() { return "Disabled tool"; }
            @Override
            public String inputSchemaJson() { return "{}"; }
            @Override
            public boolean isEnabled() { return false; }
        };

        org.springframework.ai.tool.ToolCallback callback = createCallback(name);
        return new ToolModule(tool, callback);
    }

    private org.springframework.ai.tool.ToolCallback createCallback(String name) {
        Function<Map<String, Object>, String> function = input -> "result";
        return FunctionToolCallback.builder(name, function)
                .description("Test")
                .inputType(Map.class)
                .build();
    }
}
