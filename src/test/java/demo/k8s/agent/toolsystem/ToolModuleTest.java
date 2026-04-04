package demo.k8s.agent.toolsystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolModule 测试
 */
class ToolModuleTest {

    @Test
    void testToolModuleCreation() {
        ClaudeLikeTool tool = createMockTool("test_tool", ToolCategory.FILE);
        org.springframework.ai.tool.ToolCallback callback = createCallback("test_tool");

        ToolModule module = new ToolModule(tool, callback);

        assertNotNull(module);
        assertEquals("test_tool", module.spec().name());
        assertEquals("test_tool", module.callback().getToolDefinition().name());
    }

    @Test
    void testToolModuleEquality() {
        ClaudeLikeTool tool = createMockTool("test", ToolCategory.FILE);
        org.springframework.ai.tool.ToolCallback callback = createCallback("test");

        ToolModule module1 = new ToolModule(tool, callback);
        ToolModule module2 = new ToolModule(tool, callback);

        assertEquals(module1, module2);
        assertEquals(module1.hashCode(), module2.hashCode());
    }

    @Test
    void testToolModuleWithDifferentTools() {
        ClaudeLikeTool tool1 = createMockTool("tool1", ToolCategory.FILE);
        ClaudeLikeTool tool2 = createMockTool("tool2", ToolCategory.FILE);
        org.springframework.ai.tool.ToolCallback callback1 = createCallback("tool1");
        org.springframework.ai.tool.ToolCallback callback2 = createCallback("tool2");

        ToolModule module1 = new ToolModule(tool1, callback1);
        ToolModule module2 = new ToolModule(tool2, callback2);

        assertNotEquals(module1, module2);
    }

    @Test
    void testToolModuleAccessorMethods() {
        ClaudeLikeTool tool = createMockTool("test", ToolCategory.SHELL);
        org.springframework.ai.tool.ToolCallback callback = createCallback("test");

        ToolModule module = new ToolModule(tool, callback);

        assertNotNull(module.spec());
        assertNotNull(module.callback());
        assertEquals(ToolCategory.SHELL, module.spec().category());
    }

    @Test
    void testToolModuleToString() {
        ClaudeLikeTool tool = createMockTool("test_tool", ToolCategory.FILE);
        org.springframework.ai.tool.ToolCallback callback = createCallback("test_tool");

        ToolModule module = new ToolModule(tool, callback);

        String str = module.toString();
        assertNotNull(str);
        assertTrue(str.contains("test_tool"));
    }

    private ClaudeLikeTool createMockTool(String name, ToolCategory category) {
        return new ClaudeLikeTool() {
            @Override
            public String name() { return name; }
            @Override
            public ToolCategory category() { return category; }
            @Override
            public String description() { return "Test"; }
            @Override
            public String inputSchemaJson() { return "{}"; }
        };
    }

    private org.springframework.ai.tool.ToolCallback createCallback(String name) {
        return org.springframework.ai.tool.function.FunctionToolCallback.builder(name,
                (java.util.function.Function<java.util.Map<String, Object>, String>) input -> "result")
                .description("Test")
                .inputType(java.util.Map.class)
                .build();
    }
}
