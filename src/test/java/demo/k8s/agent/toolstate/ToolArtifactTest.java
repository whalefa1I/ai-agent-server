package demo.k8s.agent.toolstate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolArtifact 实体测试
 */
@DisplayName("ToolArtifact 实体测试")
class ToolArtifactTest {

    private ToolArtifact artifact;

    @BeforeEach
    void setUp() {
        artifact = new ToolArtifact();
    }

    @Test
    @DisplayName("测试 ID 字段")
    void testId() {
        artifact.setId("test-id-123");
        assertEquals("test-id-123", artifact.getId());
    }

    @Test
    @DisplayName("测试 SessionId 字段")
    void testSessionId() {
        artifact.setSessionId("session-456");
        assertEquals("session-456", artifact.getSessionId());
    }

    @Test
    @DisplayName("测试 AccountId 字段")
    void testAccountId() {
        artifact.setAccountId("user-789");
        assertEquals("user-789", artifact.getAccountId());
    }

    @Test
    @DisplayName("测试 Header 字段")
    void testHeader() {
        String headerJson = "{\"name\":\"BashTool\",\"type\":\"tool\",\"status\":\"todo\"}";
        artifact.setHeader(headerJson);
        assertEquals(headerJson, artifact.getHeader());
    }

    @Test
    @DisplayName("测试 HeaderVersion 字段")
    void testHeaderVersion() {
        artifact.setHeaderVersion(5);
        assertEquals(5, artifact.getHeaderVersion());
    }

    @Test
    @DisplayName("测试 Body 字段")
    void testBody() {
        String bodyJson = "{\"todo\":\"Execute command\"}";
        artifact.setBody(bodyJson);
        assertEquals(bodyJson, artifact.getBody());
    }

    @Test
    @DisplayName("测试 BodyVersion 字段")
    void testBodyVersion() {
        artifact.setBodyVersion(3);
        assertEquals(3, artifact.getBodyVersion());
    }

    @Test
    @DisplayName("测试 Seq 字段")
    void testSeq() {
        artifact.setSeq(100L);
        assertEquals(100L, artifact.getSeq());
    }

    @Test
    @DisplayName("测试 CreatedAt 字段")
    void testCreatedAt() {
        Instant now = Instant.now();
        artifact.setCreatedAt(now);
        assertEquals(now, artifact.getCreatedAt());
    }

    @Test
    @DisplayName("测试 UpdatedAt 字段")
    void testUpdatedAt() {
        Instant now = Instant.now();
        artifact.setUpdatedAt(now);
        assertEquals(now, artifact.getUpdatedAt());
    }

    @Test
    @DisplayName("测试 PrePersist 回调")
    void testPrePersist() {
        artifact.setId("test-id");
        artifact.setSessionId("session-1");
        artifact.setAccountId("user-1");
        artifact.setHeader("{}");
        artifact.setBody("{}");

        Instant before = Instant.now();
        artifact.prePersist();
        Instant after = Instant.now();

        assertTrue(artifact.getCreatedAt().isAfter(before) || artifact.getCreatedAt().equals(before));
        assertTrue(artifact.getCreatedAt().isBefore(after) || artifact.getCreatedAt().equals(after));
        assertEquals(artifact.getCreatedAt(), artifact.getUpdatedAt());
    }

    @Test
    @DisplayName("测试 PreUpdate 回调")
    void testPreUpdate() {
        Instant before = Instant.now();
        artifact.preUpdate();
        Instant after = Instant.now();

        assertTrue(artifact.getUpdatedAt().isAfter(before) || artifact.getUpdatedAt().equals(before));
        assertTrue(artifact.getUpdatedAt().isBefore(after) || artifact.getUpdatedAt().equals(after));
    }

    @Test
    @DisplayName("测试完整对象创建")
    void testFullObject() {
        String headerJson = "{\"name\":\"BashTool\",\"type\":\"tool\",\"status\":\"todo\",\"version\":1}";
        String bodyJson = "{\"todo\":\"Execute ls -la\",\"version\":1}";
        Instant now = Instant.now();

        artifact.setId("artifact-123");
        artifact.setSessionId("session-456");
        artifact.setAccountId("user-789");
        artifact.setHeader(headerJson);
        artifact.setHeaderVersion(1);
        artifact.setBody(bodyJson);
        artifact.setBodyVersion(1);
        artifact.setSeq(0L);
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);

        assertEquals("artifact-123", artifact.getId());
        assertEquals("session-456", artifact.getSessionId());
        assertEquals("user-789", artifact.getAccountId());
        assertEquals(headerJson, artifact.getHeader());
        assertEquals(1, artifact.getHeaderVersion());
        assertEquals(bodyJson, artifact.getBody());
        assertEquals(1, artifact.getBodyVersion());
        assertEquals(0L, artifact.getSeq());
    }
}
