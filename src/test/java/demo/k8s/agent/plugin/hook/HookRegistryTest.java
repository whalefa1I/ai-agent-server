package demo.k8s.agent.plugin.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HookRegistry 单元测试
 */
class HookRegistryTest {

    private HookRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HookRegistry();
    }

    @Test
    void testRegisterHook() {
        Hook hook = new TestHook("test-hook", "Test Hook", HookType.TOOL_CALL, HookPhase.BEFORE);

        registry.register(hook);

        var registered = registry.getHook("test-hook");
        assertNotNull(registered);
        assertEquals("test-hook", registered.getId());
    }

    @Test
    void testUnregisterHook() {
        Hook hook = new TestHook("test-hook", "Test Hook", HookType.TOOL_CALL, HookPhase.BEFORE);
        registry.register(hook);

        registry.unregister("test-hook");

        var registered = registry.getHook("test-hook");
        assertNull(registered);
    }

    @Test
    void testGetByType() {
        Hook hook1 = new TestHook("hook1", "Hook 1", HookType.TOOL_CALL, HookPhase.BEFORE);
        Hook hook2 = new TestHook("hook2", "Hook 2", HookType.TOOL_CALL, HookPhase.AFTER);
        Hook hook3 = new TestHook("hook3", "Hook 3", HookType.MODEL_CALL, HookPhase.BEFORE);

        registry.register(hook1);
        registry.register(hook2);
        registry.register(hook3);

        var toolCallHooks = registry.getHooks(HookType.TOOL_CALL);
        assertEquals(2, toolCallHooks.size());
    }

    @Test
    void testGetByTypeAndPhase() {
        Hook hook1 = new TestHook("hook1", "Hook 1", HookType.TOOL_CALL, HookPhase.BEFORE);
        Hook hook2 = new TestHook("hook2", "Hook 2", HookType.TOOL_CALL, HookPhase.AFTER);
        Hook hook3 = new TestHook("hook3", "Hook 3", HookType.TOOL_CALL, HookPhase.BEFORE);

        registry.register(hook1);
        registry.register(hook2);
        registry.register(hook3);

        var beforeHooks = registry.getHooks(HookType.TOOL_CALL, HookPhase.BEFORE);
        assertEquals(2, beforeHooks.size());
    }

    @Test
    void testGetByTypeAndPhaseSortedByPriority() {
        Hook hook1 = new TestHook("hook1", "Hook 1", HookType.TOOL_CALL, HookPhase.BEFORE, 100);
        Hook hook2 = new TestHook("hook2", "Hook 2", HookType.TOOL_CALL, HookPhase.BEFORE, 50);
        Hook hook3 = new TestHook("hook3", "Hook 3", HookType.TOOL_CALL, HookPhase.BEFORE, 200);

        registry.register(hook1);
        registry.register(hook2);
        registry.register(hook3);

        var sortedHooks = registry.getHooks(HookType.TOOL_CALL, HookPhase.BEFORE);
        // 应该按优先级排序（数字越小优先级越高）
        assertEquals("hook2", sortedHooks.get(0).getId());
        assertEquals("hook1", sortedHooks.get(1).getId());
        assertEquals("hook3", sortedHooks.get(2).getId());
    }

    @Test
    void testClearAll() {
        Hook hook1 = new TestHook("hook1", "Hook 1", HookType.TOOL_CALL, HookPhase.BEFORE);
        Hook hook2 = new TestHook("hook2", "Hook 2", HookType.MODEL_CALL, HookPhase.AFTER);

        registry.register(hook1);
        registry.register(hook2);

        registry.clear();

        assertEquals(0, registry.getAllHooks().size());
    }

    @Test
    void testGetAll() {
        Hook hook1 = new TestHook("hook1", "Hook 1", HookType.TOOL_CALL, HookPhase.BEFORE);
        Hook hook2 = new TestHook("hook2", "Hook 2", HookType.MODEL_CALL, HookPhase.AFTER);

        registry.register(hook1);
        registry.register(hook2);

        var allHooks = registry.getAllHooks();
        assertEquals(2, allHooks.size());
    }

    /**
     * 测试用 Hook 实现
     */
    private static class TestHook implements Hook {
        private final String id;
        private final String name;
        private final HookType type;
        private final HookPhase phase;
        private final int priority;

        TestHook(String id, String name, HookType type, HookPhase phase) {
            this(id, name, type, phase, 100);
        }

        TestHook(String id, String name, HookType type, HookPhase phase, int priority) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.phase = phase;
            this.priority = priority;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Test hook";
        }

        @Override
        public HookType getType() {
            return type;
        }

        @Override
        public HookPhase getPhase() {
            return phase;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean execute(HookContext context) {
            return true;
        }
    }
}
