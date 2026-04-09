package demo.k8s.agent.contextobject;

import demo.k8s.agent.config.DemoContextObjectWriteProperties;
import demo.k8s.agent.subagent.metrics.SubagentMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link ContextObjectWriteService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ContextObjectWriteServiceTest {

    @Mock
    private ContextObjectRepository repository;

    @Mock
    private SubagentMetrics metrics;

    private DemoContextObjectWriteProperties props;
    private ContextObjectWriteService writeService;

    @BeforeEach
    void setUp() {
        props = new DemoContextObjectWriteProperties();
        props.setWriteEnabled(true);
        props.setWriteThresholdChars(100);
        props.setFallbackHeadChars(50);
        props.setFallbackTailChars(50);
        props.setDefaultTenantId("test-tenant");
        props.setDefaultTtlHours(24);

        writeService = new ContextObjectWriteService(repository, props, metrics);
    }

    @Test
    @DisplayName("写入禁用时返回 fallback")
    void write_whenDisabled_returnsFallback() {
        props.setWriteEnabled(false);

        ContextObjectWriteService.WriteResult result = writeService.write(
                "test_tool", "some content", 10, ProducerKind.COMPACTION, null);

        assertFalse(result.success());
        assertNull(result.objectId());
        assertEquals("some content", result.stubText());
    }

    @Test
    @DisplayName("空内容直接返回成功")
    void write_whenEmptyContent_returnsSuccess() {
        ContextObjectWriteService.WriteResult result = writeService.write(
                "test_tool", "", 0, ProducerKind.COMPACTION, null);

        assertTrue(result.success());
        assertNull(result.objectId());
        assertEquals("", result.stubText());
    }

    @Test
    @DisplayName("内容未超阈值时不写入 DB")
    void write_whenContentBelowThreshold_doesNotWrite() {
        String content = "short content";

        ContextObjectWriteService.WriteResult result = writeService.write(
                "test_tool", content, 10, ProducerKind.COMPACTION, null);

        assertTrue(result.success());
        assertNull(result.objectId());
        assertEquals(content, result.stubText());
    }

    @Test
    @DisplayName("内容超阈值时写入 DB 并返回存根")
    void write_whenContentAboveThreshold_writesToDb() {
        String content = "x".repeat(200);

        ContextObjectWriteService.WriteResult result = writeService.write(
                "test_tool", content, 50, ProducerKind.COMPACTION, "run-123");

        assertTrue(result.success());
        assertNotNull(result.objectId());
        assertTrue(result.objectId().startsWith("ctx-obj-"));
        assertEquals("[" + result.objectId() + "]", result.stubText());

        // 验证 DB 保存
        ArgumentCaptor<ContextObject> captor = ArgumentCaptor.forClass(ContextObject.class);
        verify(repository).save(captor.capture());

        ContextObject saved = captor.getValue();
        assertEquals(result.objectId(), saved.getId());
        assertEquals("test-tenant", saved.getTenantId());
        assertEquals("test_tool", saved.getToolName());
        assertEquals(content, saved.getContent());
        assertEquals(50, saved.getTokenEstimate());
        assertEquals("run-123", saved.getRunId());
        assertEquals(ProducerKind.COMPACTION, saved.getProducerKind());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("写入失败时返回降级 fallback")
    void write_whenDbFails_returnsFallback() {
        String content = "x".repeat(200);
        doThrow(new RuntimeException("DB unavailable")).when(repository).save(any());

        ContextObjectWriteService.WriteResult result = writeService.write(
                "test_tool", content, 50, ProducerKind.COMPACTION, null);

        assertFalse(result.success());
        assertNull(result.objectId());
        assertNotNull(result.stubText());
        assertTrue(result.stubText().contains("[SYSTEM: Context object write failed"));
        assertTrue(result.stubText().contains("test_tool"));

        // 验证 fallback 包含头尾片段
        assertTrue(result.stubText().contains("xxxx"));
    }
}
