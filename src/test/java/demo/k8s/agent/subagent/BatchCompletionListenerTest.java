package demo.k8s.agent.subagent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BatchCompletionListener 单元测试
 */
@DisplayName("BatchCompletionListener 单元测试")
class BatchCompletionListenerTest {

    @Mock
    private SubagentRunService runService;

    @Mock
    private demo.k8s.agent.observability.events.EventBus eventBus;

    @Mock
    private demo.k8s.agent.config.DemoMultiAgentProperties agentProperties;

    @Mock
    private SubagentResultStorage resultStorage;

    @Mock
    private SubagentRun subagentRun;

    private BatchCompletionListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(agentProperties.getWallclockTtlSeconds()).thenReturn(300); // 5 minutes TTL
        when(resultStorage.writeResult(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "results/" + inv.getArgument(0) + "/run-" + inv.getArgument(1) + ".txt");
        when(resultStorage.getResultPath(anyString(), anyString()))
                .thenAnswer(inv -> "results/" + inv.getArgument(0) + "/run-" + inv.getArgument(1) + ".txt");
        when(resultStorage.summarize(anyString(), anyInt()))
                .thenAnswer(inv -> {
                    String text = inv.getArgument(0);
                    int max = inv.getArgument(1);
                    if (text == null || text.isBlank()) {
                        return "(no output)";
                    }
                    return text.length() <= max ? text : text.substring(0, max) + "... [truncated]";
                });
        listener = new BatchCompletionListener(runService, eventBus, agentProperties, resultStorage);
    }

    @Test
    @DisplayName("创建批次上下文")
    void testCreateBatch() {
        // When
        BatchContext ctx = listener.createBatch("session-1", "run-main-123", 3);

        // Then
        assertNotNull(ctx);
        assertEquals("session-1", ctx.getSessionId());
        assertEquals("run-main-123", ctx.getMainRunId());
        assertEquals(3, ctx.getTotalTasks());
        assertEquals(0, ctx.getCompletedCount());
        assertFalse(ctx.isAllCompleted());
    }

    @Test
    @DisplayName("子任务完成回调 - 全部完成")
    void testOnSubagentCompleted_allComplete() throws Exception {
        // Given
        BatchContext ctx = listener.createBatch("session-1", "run-main-123", 3);
        String batchId = ctx.getBatchId();

        when(runService.getRun(anyString(), anyString())).thenReturn(subagentRun);
        when(subagentRun.getBatchId()).thenReturn(batchId);
        when(subagentRun.getMainRunId()).thenReturn("run-main-123");

        // When - 所有 3 个子任务完成
        listener.onSubagentCompleted("run-001", "session-1", "result-1");
        listener.onSubagentCompleted("run-002", "session-1", "result-2");
        listener.onSubagentCompleted("run-003", "session-1", "result-3");

        // Then - 应该发布 BatchCompletedEvent
        verify(eventBus, times(1)).publish(any(demo.k8s.agent.subagent.BatchCompletionListener.BatchCompletedEvent.class));
    }

    @Test
    @DisplayName("子任务完成回调 - 部分完成不触发唤醒")
    void testOnSubagentCompleted_partialComplete_doesNotResume() {
        // Given
        BatchContext ctx = listener.createBatch("session-1", "run-main-123", 3);
        String batchId = ctx.getBatchId();

        when(runService.getRun(anyString(), anyString())).thenReturn(subagentRun);
        when(subagentRun.getBatchId()).thenReturn(batchId);

        // When - 只有 2 个子任务完成
        listener.onSubagentCompleted("run-001", "session-1", "result-1");
        listener.onSubagentCompleted("run-002", "session-1", "result-2");

        // Then - 不应发布事件
        verify(eventBus, never()).publish(any());
        assertFalse(ctx.isAllCompleted());
        assertEquals(2, ctx.getCompletedCount());
    }

    @Test
    @DisplayName("非批次任务跳过处理")
    void testOnSubagentCompleted_nonBatchTask_skips() {
        // Given
        when(runService.getRun(anyString(), anyString())).thenReturn(subagentRun);
        when(subagentRun.getBatchId()).thenReturn(null); // 非批次任务

        // When
        listener.onSubagentCompleted("run-001", "session-1", "result-1");

        // Then - 不应发布事件
        verify(eventBus, never()).publish(any());
    }

    @Test
    @DisplayName("批次上下文超时检测")
    void testBatchContext_timeout() throws Exception {
        // Given - 创建一个 TTL 很短的批次
        BatchContext ctx = new BatchContext("batch-test", "session-1", "run-main", 2, 1); // 1 second TTL

        // When - 等待超时
        Thread.sleep(1500);

        // Then
        assertTrue(ctx.isTimedOut());
    }

    @Test
    @DisplayName("结果汇总 - 截断长结果")
    void testCollectResultsAsSummary_truncatesLongResults() {
        // Given
        BatchContext ctx = listener.createBatch("session-1", "run-main-123", 1);
        String batchId = ctx.getBatchId();

        when(runService.getRun("run-001", "session-1")).thenReturn(subagentRun);
        when(subagentRun.getBatchId()).thenReturn(batchId);

        String longResult = "x".repeat(5000); // 5000 字符

        // When
        listener.onSubagentCompleted("run-001", "session-1", longResult);
        String summary = ctx.collectResultsAsSummary();

        // Then - 结果应该被截断
        assertTrue(summary.contains("truncated"));
        assertTrue(summary.length() < 5000);
    }

    @Test
    @DisplayName("批次状态查询")
    void testGetBatchStatus() {
        // Given
        BatchContext ctx = listener.createBatch("session-1", "run-main-123", 5);
        String batchId = ctx.getBatchId();

        // When
        BatchContext retrieved = listener.getBatchStatus(batchId);

        // Then
        assertNotNull(retrieved);
        assertEquals(ctx.getBatchId(), retrieved.getBatchId());
        assertEquals(5, retrieved.getTotalTasks());
    }

    @Test
    @DisplayName("同一 runId 重复完成回调不重复计数")
    void testOnSubagentCompleted_duplicateRunId_ignored() {
        BatchContext ctx = listener.createBatch("session-1", "run-main-123", 2);
        String batchId = ctx.getBatchId();

        when(runService.getRun(anyString(), anyString())).thenReturn(subagentRun);
        when(subagentRun.getBatchId()).thenReturn(batchId);
        when(subagentRun.getMainRunId()).thenReturn("run-main-123");

        listener.onSubagentCompleted("run-001", "session-1", "result-1");
        listener.onSubagentCompleted("run-001", "session-1", "result-1-again");

        assertEquals(1, ctx.getCompletedCount());
        verify(eventBus, never()).publish(any());

        listener.onSubagentCompleted("run-002", "session-1", "result-2");
        verify(eventBus, times(1)).publish(any(demo.k8s.agent.subagent.BatchCompletionListener.BatchCompletedEvent.class));
        assertEquals(2, ctx.getCompletedCount());
    }

    @Test
    @DisplayName("活跃批次列表")
    void testGetAllActiveBatches() {
        // Given
        listener.createBatch("session-1", "run-main-1", 3);
        listener.createBatch("session-2", "run-main-2", 2);

        // When
        var batches = listener.getAllActiveBatches();

        // Then
        assertEquals(2, batches.size());
    }
}
