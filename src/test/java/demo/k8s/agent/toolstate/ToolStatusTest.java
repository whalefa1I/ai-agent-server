package demo.k8s.agent.toolstate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolStatus 枚举测试
 */
@DisplayName("ToolStatus 枚举测试")
class ToolStatusTest {

    @Test
    @DisplayName("测试 getValue 方法返回正确的字符串值")
    void testGetValue() {
        assertEquals("todo", ToolStatus.TODO.getValue());
        assertEquals("plan", ToolStatus.PLAN.getValue());
        assertEquals("pending_confirmation", ToolStatus.PENDING_CONFIRMATION.getValue());
        assertEquals("executing", ToolStatus.EXECUTING.getValue());
        assertEquals("completed", ToolStatus.COMPLETED.getValue());
        assertEquals("failed", ToolStatus.FAILED.getValue());
    }

    @Test
    @DisplayName("测试 valueOf 方法正确解析枚举")
    void testValueOf() {
        assertEquals(ToolStatus.TODO, ToolStatus.valueOf("TODO"));
        assertEquals(ToolStatus.PLAN, ToolStatus.valueOf("PLAN"));
        assertEquals(ToolStatus.COMPLETED, ToolStatus.valueOf("COMPLETED"));
    }

    @Test
    @DisplayName("测试枚举数量")
    void testEnumCount() {
        assertEquals(6, ToolStatus.values().length);
    }
}
