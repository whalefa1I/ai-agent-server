package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.metrics.SubagentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link MultiAgentFacade} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("多 Agent 门面测试")
class MultiAgentFacadeTest {

    @Mock
    private DemoMultiAgentProperties props;

    @Mock
    private SpawnGatekeeper gatekeeper;

    @Mock
    private SubAgentRuntime runtime;

    private SubagentMetrics metrics;

    private MultiAgentFacade facade;

    @BeforeEach
    void setUp() {
        TraceContext.setSessionId("test-session");
        TraceContext.setTenantId("test-tenant");
        TraceContext.setAppId("test-app");
        metrics = new SubagentMetrics(new SimpleMeterRegistry());
        facade = new MultiAgentFacade(props, gatekeeper, runtime, metrics);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("禁用时返回拒绝")
    void spawn_whenDisabled_rejects() {
        lenient().when(props.isEnabled()).thenReturn(false);

        SpawnResult result = facade.spawn("test goal", 0, Set.of("file_read"));

        assertFalse(result.isSuccess());
        assertNotNull(result.getMustDoNext());
        assertEquals(SpawnResult.MustDoNext.Action.USE_LOCAL, result.getMustDoNext().action());
    }

    @Test
    @DisplayName("Mode=off 时返回拒绝")
    void spawn_whenModeOff_rejects() {
        lenient().when(props.isEnabled()).thenReturn(true);
        lenient().when(props.getMode()).thenReturn(DemoMultiAgentProperties.Mode.off);

        SpawnResult result = facade.spawn("test goal", 0, Set.of("file_read"));

        assertFalse(result.isSuccess());
        assertEquals(SpawnResult.MustDoNext.Action.USE_LOCAL, result.getMustDoNext().action());
    }

    @Test
    @DisplayName("Shadow 模式不注入成功结果")
    void spawn_whenShadowMode_doesNotInjectSuccess() {
        lenient().when(props.isEnabled()).thenReturn(true);
        lenient().when(props.getMode()).thenReturn(DemoMultiAgentProperties.Mode.shadow);

        SpawnResult result = facade.spawn("test goal", 0, Set.of("file_read"));

        assertFalse(result.isSuccess());
        assertEquals("Shadow mode evaluation only", result.getMessage());
        assertEquals(SpawnResult.MustDoNext.Action.NONE, result.getMustDoNext().action());
    }

    @Test
    @DisplayName("门控拒绝时返回结构化建议")
    void spawn_whenGatekeeperRejects_returnsStructuredAdvice() {
        lenient().when(props.isEnabled()).thenReturn(true);
        lenient().when(props.getMode()).thenReturn(DemoMultiAgentProperties.Mode.on);
        lenient().when(gatekeeper.checkAndAcquire(anyString(), anyInt(), any()))
                .thenReturn(SpawnResult.MustDoNext.simplify("Simplify your request"));

        SpawnResult result = facade.spawn("test goal", 0, Set.of("file_read"));

        assertFalse(result.isSuccess());
        assertNotNull(result.getMustDoNext());
        assertEquals(SpawnResult.MustDoNext.Action.SIMPLIFY, result.getMustDoNext().action());
    }

    @Test
    @DisplayName("成功派生")
    void spawn_whenAllChecksPass_succeeds() {
        lenient().when(props.isEnabled()).thenReturn(true);
        lenient().when(props.getMode()).thenReturn(DemoMultiAgentProperties.Mode.on);
        lenient().when(props.getDefaultTenantId()).thenReturn("default");
        lenient().when(gatekeeper.checkAndAcquire(anyString(), anyInt(), any())).thenReturn(null);
        lenient().when(gatekeeper.calculateDeadline()).thenReturn(java.time.Instant.now().plusSeconds(180));
        lenient().when(runtime.spawn(any())).thenReturn(CompletableFuture.completedFuture(SpawnResult.success("run-123")));

        SpawnResult result = facade.spawn("test goal", 0, Set.of("file_read"));

        assertTrue(result.isSuccess());
        assertEquals("run-123", result.getRunId());
        verify(gatekeeper, never()).onSpawnStart(anyString(), anyString());
        verify(gatekeeper, never()).onSpawnEnd(anyString(), anyString());
    }

    @Test
    @DisplayName("运行时异常时返回错误")
    void spawn_whenRuntimeThrows_returnsError() {
        lenient().when(props.isEnabled()).thenReturn(true);
        lenient().when(props.getMode()).thenReturn(DemoMultiAgentProperties.Mode.on);
        lenient().when(props.getDefaultTenantId()).thenReturn("default");
        lenient().when(gatekeeper.checkAndAcquire(anyString(), anyInt(), any())).thenReturn(null);
        lenient().when(gatekeeper.calculateDeadline()).thenReturn(java.time.Instant.now().plusSeconds(180));
        lenient().when(runtime.spawn(any())).thenThrow(new RuntimeException("Runtime error"));

        SpawnResult result = facade.spawn("test goal", 0, Set.of("file_read"));

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Runtime error"));
        verify(gatekeeper).releaseConcurrentSlot("test-session");
    }

    @Test
    @DisplayName("cancel 取消任务")
    void cancel_cancelsTask() {
        lenient().when(runtime.cancel("run-123")).thenReturn(true);

        boolean result = facade.cancel("run-123");

        assertTrue(result);
    }

    @Test
    @DisplayName("getStatus 获取状态")
    void getStatus_returnsStatus() {
        SubRunEvent event = SubRunEvent.started("run-123", "test-session");
        lenient().when(runtime.getStatus("run-123")).thenReturn(event);

        SubRunEvent result = facade.getStatus("run-123");

        assertEquals(event, result);
        assertEquals(SubRunEvent.EventType.STARTED, result.getType());
    }
}
