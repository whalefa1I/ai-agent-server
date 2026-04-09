package demo.k8s.agent.subagent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link SubagentRunService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("子 Agent 运行服务测试")
class SubagentRunServiceTest {

    @Mock
    private SubagentRunRepository repository;

    @Mock
    private Optional<SubagentRun> optionalRun;

    private SubagentRunService runService;

    @BeforeEach
    void setUp() {
        runService = new SubagentRunService(repository);
    }

    @Test
    @DisplayName("创建运行记录")
    void createRun_createsRecord() {
        SpawnRequest request = new SpawnRequest(
                "trace-1",
                "session-1",
                "tenant-1",
                "app-1",
                null,
                null,
                "test goal",
                new SpawnRequest.SpawnConstraints(
                        8000,
                        1,
                        Set.of("file_read"),
                        Instant.now().plusSeconds(180).toEpochMilli()
                )
        );

        when(repository.save(any(SubagentRun.class))).thenAnswer(invocation -> {
            SubagentRun run = invocation.getArgument(0);
            run.setRunId("run-test-123");
            return run;
        });

        SubagentRun result = runService.createRun(request);

        assertNotNull(result);
        assertEquals("run-test-123", result.getRunId());
        assertEquals("session-1", result.getSessionId());
        assertEquals("tenant-1", result.getTenantId());
        assertEquals(SubagentRun.RunStatus.PENDING, result.getStatus());
        verify(repository).save(any(SubagentRun.class));
    }

    @Test
    @DisplayName("更新状态为完成")
    void updateStatus_completed() {
        String runId = "run-123";
        String resultText = "task completed";

        SubagentRun run = new SubagentRun();
        run.setRunId(runId);
        run.setStatus(SubagentRun.RunStatus.RUNNING);

        when(repository.findById(runId)).thenReturn(Optional.of(run));
        when(repository.save(any(SubagentRun.class))).thenReturn(run);

        SubagentRun updated = runService.updateStatus(runId, SubagentRun.RunStatus.COMPLETED, resultText);

        assertEquals(SubagentRun.RunStatus.COMPLETED, updated.getStatus());
        assertEquals(resultText, updated.getResult());
        assertNotNull(updated.getEndedAt());
        verify(repository).save(run);
    }

    @Test
    @DisplayName("更新状态为失败")
    void updateStatus_failed() {
        String runId = "run-123";
        String error = "something went wrong";

        SubagentRun run = new SubagentRun();
        run.setRunId(runId);
        run.setStatus(SubagentRun.RunStatus.RUNNING);

        when(repository.findById(runId)).thenReturn(Optional.of(run));
        when(repository.save(any(SubagentRun.class))).thenReturn(run);

        SubagentRun updated = runService.updateStatus(runId, SubagentRun.RunStatus.FAILED, error);

        assertEquals(SubagentRun.RunStatus.FAILED, updated.getStatus());
        assertEquals(error, updated.getErrorMessage());
        assertNotNull(updated.getEndedAt());
    }

    @Test
    @DisplayName("启动运行")
    void startRun() {
        String runId = "run-123";

        SubagentRun run = new SubagentRun();
        run.setRunId(runId);
        run.setStatus(SubagentRun.RunStatus.PENDING);

        when(repository.findById(runId)).thenReturn(Optional.of(run));
        when(repository.save(any(SubagentRun.class))).thenReturn(run);

        SubagentRun updated = runService.startRun(runId);

        assertEquals(SubagentRun.RunStatus.RUNNING, updated.getStatus());
        assertNotNull(updated.getStartedAt());
    }

    @Test
    @DisplayName("运行不存在时抛出异常")
    void getRun_notFound() {
        when(repository.findByRunIdAndSessionId("run-123", "session-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            runService.getRun("run-123", "session-1");
        });
    }

    @Test
    @DisplayName("查找需要恢复的运行记录")
    void findRunsToReconcile() {
        SubagentRun run1 = new SubagentRun();
        run1.setRunId("run-1");
        run1.setStatus(SubagentRun.RunStatus.RUNNING);

        SubagentRun run2 = new SubagentRun();
        run2.setRunId("run-2");
        run2.setStatus(SubagentRun.RunStatus.PENDING);

        when(repository.findBySessionIdAndStatusIn(eq("session-1"), any()))
                .thenReturn(List.of(run1, run2));

        List<SubagentRun> runs = runService.findRunsToReconcile("session-1");

        assertEquals(2, runs.size());
    }

    @Test
    @DisplayName("Reconcile 超时任务")
    void reconcile_timeout() {
        String runId = "run-123";
        SubagentRun run = new SubagentRun();
        run.setRunId(runId);
        run.setStatus(SubagentRun.RunStatus.RUNNING);
        run.setDeadlineAt(Instant.now().minusSeconds(60)); // 已过期

        when(repository.findById(runId)).thenReturn(Optional.of(run));
        when(repository.save(any(SubagentRun.class))).thenReturn(run);

        SubagentRunService.ReconcileResult result = runService.reconcile(runId);

        assertEquals(SubagentRunService.ReconcileAction.TIMEOUT, result.action());
        assertEquals(SubagentRun.RunStatus.TIMEOUT, run.getStatus());
        assertNotNull(run.getEndedAt());
    }

    @Test
    @DisplayName("Reconcile 保留未过期任务")
    void reconcile_preserve() {
        String runId = "run-123";
        SubagentRun run = new SubagentRun();
        run.setRunId(runId);
        run.setStatus(SubagentRun.RunStatus.RUNNING);
        run.setDeadlineAt(Instant.now().plusSeconds(300)); // 未过期

        when(repository.findById(runId)).thenReturn(Optional.of(run));

        SubagentRunService.ReconcileResult result = runService.reconcile(runId);

        assertEquals(SubagentRunService.ReconcileAction.PRESERVE, result.action());
        assertEquals(SubagentRun.RunStatus.RUNNING, run.getStatus());
    }

    @Test
    @DisplayName("findAllActiveRuns 仅查询活跃状态，不扫全表")
    void findAllActiveRuns_usesStatusFilter() {
        when(repository.findByStatusIn(any())).thenReturn(List.of());

        runService.findAllActiveRuns();

        verify(repository).findByStatusIn(argThat(statuses ->
                statuses.contains(SubagentRun.RunStatus.RUNNING)
                        && statuses.contains(SubagentRun.RunStatus.PENDING)));
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("增加重试计数")
    void incrementRetry() {
        String runId = "run-123";
        SubagentRun run = new SubagentRun();
        run.setRunId(runId);
        run.setRetryCount(0);

        when(repository.findById(runId)).thenReturn(Optional.of(run));
        when(repository.save(any(SubagentRun.class))).thenReturn(run);

        SubagentRun updated = runService.incrementRetry(runId);

        assertEquals(1, updated.getRetryCount());
    }
}
