package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.web.SubagentSseController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SubagentBatchService} 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SubagentBatchServiceTest {

    @Mock
    private MultiAgentFacade multiAgentFacade;

    @Mock
    private SubagentRunService runService;

    @Mock
    private BatchCompletionListener batchCompletionListener;

    @Mock
    private SubagentSseController sseController;

    @Mock
    private DemoMultiAgentProperties properties;

    @Mock
    private SubagentRun subagentRun;

    private SubagentBatchService batchService;

    @BeforeEach
    void setUp() {
        batchService = new SubagentBatchService(
                multiAgentFacade,
                runService,
                batchCompletionListener,
                sseController,
                properties
        );
    }

    @Test
    void testSpawnBatch_Success() {
        // Given
        String sessionId = "test-session";
        String mainRunId = "run-main-123";
        String batchId = "batch-test-456";

        List<SubagentBatchService.BatchTaskRequest> tasks = List.of(
                new SubagentBatchService.BatchTaskRequest("task1", "Goal 1", "worker"),
                new SubagentBatchService.BatchTaskRequest("task2", "Goal 2", "worker")
        );

        BatchContext mockBatchContext = mock(BatchContext.class);
        when(mockBatchContext.getBatchId()).thenReturn(batchId);
        when(batchCompletionListener.createBatch(sessionId, mainRunId, 2)).thenReturn(mockBatchContext);

        when(multiAgentFacade.spawnTask(any(), any(), any(), anyInt(), anySet(), any(), anyInt(), anyInt(), any()))
                .thenReturn(SpawnResult.success("run-001"))
                .thenReturn(SpawnResult.success("run-002"));

        when(properties.getMaxConcurrentSpawns()).thenReturn(5);

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "test-user", sessionId, null
        );

        // When
        SubagentBatchService.BatchSpawnResponse response = batchService.spawnBatch(
                ctx, sessionId, mainRunId, tasks, 0, Set.of("test-tool")
        );

        // Then
        assertTrue(response.success());
        assertEquals(batchId, response.batchId());
        assertEquals(2, response.totalTasks());
        assertEquals(2, response.tasks().size());

        // 验证 SSE 事件发布
        verify(sseController, times(2)).publishStatusEvent(
                anyString(), eq(sessionId), eq("accepted"), isNull(), isNull()
        );
    }

    @Test
    void testSpawnBatch_ExceedsMaxConcurrent() {
        // Given
        when(properties.getMaxConcurrentSpawns()).thenReturn(3);

        List<SubagentBatchService.BatchTaskRequest> tasks = List.of(
                new SubagentBatchService.BatchTaskRequest("task1", "Goal 1", "worker"),
                new SubagentBatchService.BatchTaskRequest("task2", "Goal 2", "worker"),
                new SubagentBatchService.BatchTaskRequest("task3", "Goal 3", "worker"),
                new SubagentBatchService.BatchTaskRequest("task4", "Goal 4", "worker")
        );

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "test-user", "session-1", null
        );

        // When
        SubagentBatchService.BatchSpawnResponse response = batchService.spawnBatch(
                ctx, "session-1", "run-main", tasks, 0, Set.of("test-tool")
        );

        // Then
        assertFalse(response.success());
        assertEquals("SPAWN_REJECTED_QUOTA", response.errorCode());
        assertTrue(response.error().contains("max concurrent spawns"));
    }

    @Test
    void testSpawnBatch_TaskRejected() {
        // Given
        String sessionId = "test-session";
        String mainRunId = "run-main-123";
        String batchId = "batch-test-456";

        List<SubagentBatchService.BatchTaskRequest> tasks = List.of(
                new SubagentBatchService.BatchTaskRequest("task1", "Goal 1", "worker")
        );

        BatchContext mockBatchContext = mock(BatchContext.class);
        when(mockBatchContext.getBatchId()).thenReturn(batchId);
        when(batchCompletionListener.createBatch(sessionId, mainRunId, 1)).thenReturn(mockBatchContext);

        when(multiAgentFacade.spawnTask(any(), any(), any(), anyInt(), anySet(), any(), anyInt(), anyInt(), any()))
                .thenReturn(SpawnResult.rejected("Quota exceeded", SpawnResult.MustDoNext.simplify("Reduce concurrent tasks")));

        when(properties.getMaxConcurrentSpawns()).thenReturn(5);

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "test-user", sessionId, null
        );

        // When
        SubagentBatchService.BatchSpawnResponse response = batchService.spawnBatch(
                ctx, sessionId, mainRunId, tasks, 0, Set.of("test-tool")
        );

        // Then
        assertFalse(response.success());
        assertEquals(1, response.totalTasks());
        assertEquals(1, response.tasks().size());
        assertEquals("rejected", response.tasks().get(0).status());
        assertEquals("SPAWN_ALL_REJECTED", response.errorCode());
    }

    @Test
    void testCancelBatch_Success() {
        // Given
        String sessionId = "test-session";
        String batchId = "batch-test-456";

        when(runService.findByBatchId(batchId, sessionId)).thenReturn(List.of(subagentRun));
        when(subagentRun.getRunId()).thenReturn("run-001");
        when(subagentRun.getStatus()).thenReturn(SubagentRun.RunStatus.RUNNING);

        when(multiAgentFacade.cancel("run-001")).thenReturn(true);

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS", sessionId, "ops_cancel"
        );

        // When
        SubagentBatchService.BatchCancelResponse response = batchService.cancelBatch(
                ctx, sessionId, batchId, "ops_cancel"
        );

        // Then
        assertTrue(response.success());
        assertEquals("CANCEL_ACCEPTED", response.status());
        assertEquals(1, response.cancelledRunIds().size());
        assertEquals("run-001", response.cancelledRunIds().get(0));

        verify(sseController).publishStatusEvent(
                eq("run-001"), eq(sessionId), eq("CANCELLED"), isNull(), eq("ops_cancel")
        );
    }

    @Test
    void testCancelBatch_AlreadyTerminal() {
        // Given
        String sessionId = "test-session";
        String batchId = "batch-test-456";

        when(subagentRun.getStatus()).thenReturn(SubagentRun.RunStatus.COMPLETED);
        when(runService.findByBatchId(batchId, sessionId)).thenReturn(List.of(subagentRun));

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS", sessionId, "ops_cancel"
        );

        // When
        SubagentBatchService.BatchCancelResponse response = batchService.cancelBatch(
                ctx, sessionId, batchId, "ops_cancel"
        );

        // Then
        assertFalse(response.success());
        assertEquals("BATCH_ALREADY_TERMINAL", response.error());
    }

    @Test
    void testQueryBatch_Success() {
        // Given
        String sessionId = "test-session";
        String batchId = "batch-test-456";

        when(subagentRun.getRunId()).thenReturn("run-001");
        when(subagentRun.getStatus()).thenReturn(SubagentRun.RunStatus.COMPLETED);
        when(subagentRun.getResult()).thenReturn("Task completed successfully");
        when(subagentRun.getErrorMessage()).thenReturn(null);

        when(runService.findByBatchId(batchId, sessionId)).thenReturn(List.of(subagentRun));

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS", sessionId, null
        );

        // When
        SubagentBatchService.BatchQueryResponse response = batchService.queryBatch(ctx, sessionId, batchId);

        // Then
        assertTrue(response.success());
        assertEquals(1, response.totalTasks());
        assertEquals(1, response.completed());
        assertEquals("COMPLETED", response.status());
        assertEquals(1, response.tasks().size());
        assertEquals("run-001", response.tasks().get(0).runId());
    }

    @Test
    void testQueryBatch_CancelledCountsAsFailedNotPending() {
        // Given
        String sessionId = "test-session";
        String batchId = "batch-test-cancelled";

        when(subagentRun.getRunId()).thenReturn("run-cancel-1");
        when(subagentRun.getStatus()).thenReturn(SubagentRun.RunStatus.CANCELLED);
        when(subagentRun.getResult()).thenReturn(null);
        when(subagentRun.getErrorMessage()).thenReturn("Cancelled by user");
        when(runService.findByBatchId(batchId, sessionId)).thenReturn(List.of(subagentRun));

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS", sessionId, null
        );

        // When
        SubagentBatchService.BatchQueryResponse response = batchService.queryBatch(ctx, sessionId, batchId);

        // Then
        assertTrue(response.success());
        assertEquals(0, response.pending());
        assertEquals(1, response.failed());
        assertEquals("FAILED", response.status());
    }

    @Test
    void testQueryBatch_NotFound() {
        // Given
        when(runService.findByBatchId("non-existent", "session-1")).thenReturn(List.of());

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS", "session-1", null
        );

        // When
        SubagentBatchService.BatchQueryResponse response = batchService.queryBatch(ctx, "session-1", "non-existent");

        // Then
        assertFalse(response.success());
        assertTrue(response.error().contains("not found"));
    }
}
