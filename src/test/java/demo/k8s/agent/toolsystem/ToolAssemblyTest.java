package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolAssembly 测试
 */
class ToolAssemblyTest {

    @Test
    void testToolAssemblyUtilityClass() {
        // ToolAssembly 是工具类，不能实例化
        // 验证其静态方法存在且可用
        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                List.of(),
                ToolPermissionContext.defaultContext(),
                new ToolFeatureFlags(true, true, true, true, Set.of()),
                List.of()
        );
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAssembleFilteredToolPool() {
        // 测试工具组装和过滤
        List<ToolModule> localTools = List.of(
                createModule("local1", ToolCategory.FILE),
                createModule("local2", ToolCategory.SHELL)
        );

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );
        // 关闭 simpleMode 以避免过滤
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                localTools, ctx, flags, List.of()
        );

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testAssembleFilteredToolPoolWithDeniedTools() {
        List<ToolModule> localTools = List.of(
                createModule("allowed", ToolCategory.FILE),
                createModule("denied", ToolCategory.SHELL)
        );

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of("denied")
        );
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                localTools, ctx, flags, List.of()
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("allowed", result.get(0).spec().name());
    }

    @Test
    void testAssembleFilteredToolPoolWithMcpTools() {
        List<ToolModule> localTools = List.of(
                createModule("local", ToolCategory.FILE)
        );

        ToolModule mcpTool = createModule("mcp", ToolCategory.EXTERNAL);

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                localTools, ctx, flags, List.of(mcpTool)
        );

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testAssembleFilteredToolPoolEmptyInput() {
        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                List.of(), ctx, flags, List.of()
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testToolCategoryFiltering() {
        List<ToolModule> localTools = List.of(
                createModule("file_tool", ToolCategory.FILE),
                createModule("shell_tool", ToolCategory.SHELL),
                createModule("agent_tool", ToolCategory.AGENT)
        );

        // 只允许 FILE 类别 - 使用 allowedCategories 来限制
        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(ToolCategory.FILE),
                Set.of()
        );
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                localTools, ctx, flags, List.of()
        );

        // 注意：assembleFilteredToolPool 只做特性门控、简单模式、deny 规则过滤
        // 类别过滤是在 ToolRegistry.callbacksAfterCategoryFilter 中做的
        // 所以这里返回 3 个工具是正常的
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void testToolCategoryFilteringWithDeniedTools() {
        List<ToolModule> localTools = List.of(
                createModule("file_tool", ToolCategory.FILE),
                createModule("shell_tool", ToolCategory.SHELL)
        );

        // 使用 deniedToolNames 来拒绝工具
        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of("shell_tool")
        );
        ToolFeatureFlags flags = new ToolFeatureFlags(false, true, true, true, Set.of());

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                localTools, ctx, flags, List.of()
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("file_tool", result.get(0).spec().name());
    }

    @Test
    void testAssembleFilteredToolPoolWithSimpleMode() {
        List<ToolModule> localTools = List.of(
                createModule("tool1", ToolCategory.FILE),
                createModule("tool2", ToolCategory.SHELL)
        );

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );
        // 启用 simpleMode 并只允许 tool1
        ToolFeatureFlags flags = new ToolFeatureFlags(true, true, true, true, Set.of("tool1"));

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                localTools, ctx, flags, List.of()
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("tool1", result.get(0).spec().name());
    }

    @Test
    void testAssembleFilteredToolPoolAgentSwarmsDisabled() {
        List<ToolModule> localTools = List.of(
                createModule("agent_tool", ToolCategory.AGENT),
                createModule("file_tool", ToolCategory.FILE)
        );

        ToolPermissionContext ctx = new ToolPermissionContext(
                ToolPermissionMode.DEFAULT,
                Set.of(),
                Set.of()
        );
        // 禁用 agentSwarms
        ToolFeatureFlags flags = new ToolFeatureFlags(false, false, true, true, Set.of());

        List<ToolModule> result = ToolAssembly.assembleFilteredToolPool(
                localTools, ctx, flags, List.of()
        );

        assertNotNull(result);
        assertEquals(1, result.size()); // 只有 file_tool
        assertEquals("file_tool", result.get(0).spec().name());
    }

    private ToolModule createModule(String name, ToolCategory category) {
        ClaudeLikeTool tool = new ClaudeLikeTool() {
            @Override
            public String name() { return name; }
            @Override
            public ToolCategory category() { return category; }
            @Override
            public String description() { return "Test"; }
            @Override
            public String inputSchemaJson() { return "{}"; }
        };

        org.springframework.ai.tool.ToolCallback callback =
                org.springframework.ai.tool.function.FunctionToolCallback.builder(name,
                        (java.util.function.Function<java.util.Map<String, Object>, String>) input -> "result")
                        .description("Test")
                        .inputType(java.util.Map.class)
                        .build();

        return new ToolModule(tool, callback);
    }
}
