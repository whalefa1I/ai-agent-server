package demo.k8s.agent.toolstate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolArtifactHeader 测试
 */
@DisplayName("ToolArtifactHeader 测试")
class ToolArtifactHeaderTest {

    private ToolArtifactHeader header;

    @BeforeEach
    void setUp() {
        header = new ToolArtifactHeader();
    }

    @Test
    @DisplayName("测试 Name 字段")
    void testName() {
        header.setName("BashTool");
        assertEquals("BashTool", header.getName());
    }

    @Test
    @DisplayName("测试 Type 字段")
    void testType() {
        header.setType("tool");
        assertEquals("tool", header.getType());
    }

    @Test
    @DisplayName("测试 Status 字段")
    void testStatus() {
        header.setStatus("executing");
        assertEquals("executing", header.getStatus());
    }

    @Test
    @DisplayName("测试 Version 字段")
    void testVersion() {
        header.setVersion(3);
        assertEquals(3, header.getVersion());
    }

    @Test
    @DisplayName("测试完整对象创建")
    void testFullObject() {
        header.setName("BashTool");
        header.setType("tool");
        header.setStatus("completed");
        header.setVersion(5);

        assertEquals("BashTool", header.getName());
        assertEquals("tool", header.getType());
        assertEquals("completed", header.getStatus());
        assertEquals(5, header.getVersion());
    }
}
