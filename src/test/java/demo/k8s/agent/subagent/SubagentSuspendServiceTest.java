package demo.k8s.agent.subagent;

import demo.k8s.agent.observability.tracing.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SubagentSuspendService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Human-in-the-Loop 挂起审批测试")
class SubagentSuspendServiceTest {

    @Mock
    private SubagentSuspendRepository repository;

    @Mock
    private SubagentRunService runService;

    @Mock
    private SubagentRun run;

    private SubagentSuspendService suspendService;

    @BeforeEach
    void setUp() {
        TraceContext.setSessionId("test-session");
        TraceContext.setTenantId("test-tenant");
        TraceContext.setAppId("test-app");
        suspendService = new SubagentSuspendService(repository, runService);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("挂起成功")
    void suspend_success() {
        when(runService.getRun("run-123", "test-session")).thenReturn(run);
        when(repository.save(any(SubagentSuspend.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runService.updateStatus(any(), any(), any())).thenReturn(run);

        SuspendRequest request = SuspendRequest.suspend("run-123", "test-session",
                "Need approval for sensitive operation", "Delete production files");

        SubagentSuspendService.SuspendResult result = suspendService.suspend(request);

        assertTrue(result.success());
        assertEquals("run-123", result.runId());

        // 验证运行记录状态更新为 SUSPENDED
        verify(runService).updateStatus(eq("run-123"), eq(SubagentRun.RunStatus.SUSPENDED), any());
    }

    @Test
    @DisplayName("恢复 - 批准")
    void resume_approved() {
        SubagentSuspend suspendRecord = new SubagentSuspend();
        suspendRecord.setRunId("run-123");
        suspendRecord.setSessionId("test-session");
        suspendRecord.setStatus(SubagentSuspend.SuspendStatus.PENDING);

        when(repository.findByRunId("run-123")).thenReturn(java.util.Optional.of(suspendRecord));
        when(repository.save(any(SubagentSuspend.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runService.updateStatus(any(), any(), any())).thenReturn(run);

        ResumeRequest request = ResumeRequest.approve("run-123", "test-session", "admin-user");

        SubagentSuspendService.ResumeResult result = suspendService.resume(request);

        assertTrue(result.success());
        assertEquals("APPROVED", result.approvalResult());
        assertEquals(SubagentSuspend.SuspendStatus.APPROVED, suspendRecord.getStatus());
        assertNotNull(suspendRecord.getApprovedAt());
    }

    @Test
    @DisplayName("恢复 - 拒绝")
    void resume_rejected() {
        SubagentSuspend suspendRecord = new SubagentSuspend();
        suspendRecord.setRunId("run-123");
        suspendRecord.setSessionId("test-session");
        suspendRecord.setStatus(SubagentSuspend.SuspendStatus.PENDING);

        when(repository.findByRunId("run-123")).thenReturn(java.util.Optional.of(suspendRecord));
        when(repository.save(any(SubagentSuspend.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runService.updateStatus(any(), any(), any())).thenReturn(run);

        ResumeRequest request = ResumeRequest.reject("run-123", "test-session", "admin-user", "Security concern");

        SubagentSuspendService.ResumeResult result = suspendService.resume(request);

        assertTrue(result.success());
        assertEquals("REJECTED", result.approvalResult());
        assertEquals(SubagentSuspend.SuspendStatus.REJECTED, suspendRecord.getStatus());
    }

    @Test
    @DisplayName("恢复非待审批记录返回错误")
    void resume_whenNotPending_returnsError() {
        SubagentSuspend suspendRecord = new SubagentSuspend();
        suspendRecord.setRunId("run-123");
        suspendRecord.setStatus(SubagentSuspend.SuspendStatus.APPROVED);

        when(repository.findByRunId("run-123")).thenReturn(java.util.Optional.of(suspendRecord));

        ResumeRequest request = ResumeRequest.approve("run-123", "test-session", "admin-user");

        SubagentSuspendService.ResumeResult result = suspendService.resume(request);

        assertFalse(result.success());
        assertEquals("Suspend record is not pending: APPROVED", result.message());
    }

    @Test
    @DisplayName("获取待审批挂起记录")
    void getPendingSuspensions_returnsList() {
        SubagentSuspend suspend1 = new SubagentSuspend();
        suspend1.setRunId("run-1");
        suspend1.setStatus(SubagentSuspend.SuspendStatus.PENDING);

        SubagentSuspend suspend2 = new SubagentSuspend();
        suspend2.setRunId("run-2");
        suspend2.setStatus(SubagentSuspend.SuspendStatus.PENDING);

        when(repository.findBySessionIdAndStatus("test-session", SubagentSuspend.SuspendStatus.PENDING))
                .thenReturn(List.of(suspend1, suspend2));

        List<SubagentSuspend> pending = suspendService.getPendingSuspensions("test-session");

        assertEquals(2, pending.size());
    }

    @Test
    @DisplayName("取消挂起")
    void cancelSuspension_cancelsPending() {
        SubagentSuspend suspendRecord = new SubagentSuspend();
        suspendRecord.setRunId("run-123");
        suspendRecord.setStatus(SubagentSuspend.SuspendStatus.PENDING);

        when(repository.findByRunId("run-123")).thenReturn(java.util.Optional.of(suspendRecord));
        when(repository.save(any(SubagentSuspend.class))).thenAnswer(invocation -> invocation.getArgument(0));

        suspendService.cancelSuspension("run-123");

        assertEquals(SubagentSuspend.SuspendStatus.CANCELLED, suspendRecord.getStatus());
        verify(repository).save(suspendRecord);
    }

    @Test
    @DisplayName("取消非待审批挂起不操作")
    void cancelSuspension_whenNotPending_doesNothing() {
        SubagentSuspend suspendRecord = new SubagentSuspend();
        suspendRecord.setRunId("run-123");
        suspendRecord.setStatus(SubagentSuspend.SuspendStatus.APPROVED);

        when(repository.findByRunId("run-123")).thenReturn(java.util.Optional.of(suspendRecord));

        suspendService.cancelSuspension("run-123");

        assertEquals(SubagentSuspend.SuspendStatus.APPROVED, suspendRecord.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("挂起记录不存在时抛出异常")
    void getSuspendRecord_whenNotFound_throwsException() {
        when(repository.findByRunId("run-123")).thenReturn(java.util.Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> suspendService.getSuspendRecord("run-123"));
    }

    @Test
    @DisplayName("恢复记录不存在时抛出异常")
    void resume_whenRecordNotFound_throwsException() {
        when(repository.findByRunId("run-123")).thenReturn(java.util.Optional.empty());

        ResumeRequest request = ResumeRequest.approve("run-123", "test-session", "admin-user");

        assertThrows(IllegalArgumentException.class, () -> suspendService.resume(request));
    }
}
