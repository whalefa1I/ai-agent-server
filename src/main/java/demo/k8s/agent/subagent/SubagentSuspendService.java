package demo.k8s.agent.subagent;

import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 子 Agent 挂起审批服务（v1 Human-in-the-Loop）。
 */
@Service
public class SubagentSuspendService {

    private static final Logger log = LoggerFactory.getLogger(SubagentSuspendService.class);

    private final SubagentSuspendRepository repository;
    private final SubagentRunService runService;

    public SubagentSuspendService(SubagentSuspendRepository repository, SubagentRunService runService) {
        this.repository = repository;
        this.runService = runService;
    }

    /**
     * 挂起运行记录。
     */
    @Transactional
    public SuspendResult suspend(SuspendRequest request) {
        String tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            tenantId = "default";
        }

        // 检查运行记录是否存在
        SubagentRun run = runService.getRun(request.getRunId(), request.getSessionId());

        // 创建挂起记录
        SubagentSuspend suspendRecord = new SubagentSuspend();
        suspendRecord.setRunId(request.getRunId());
        suspendRecord.setSessionId(request.getSessionId());
        suspendRecord.setTenantId(tenantId);
        suspendRecord.setStatus(SubagentSuspend.SuspendStatus.PENDING);
        suspendRecord.setReason(request.getReason());
        suspendRecord.setApprovalRequired(request.getApprovalRequired());
        suspendRecord.setCreatedAt(Instant.now());
        suspendRecord.setUpdatedAt(Instant.now());

        repository.save(suspendRecord);

        // 更新运行记录状态为 SUSPENDED
        runService.updateStatus(request.getRunId(), SubagentRun.RunStatus.SUSPENDED, "Suspended for approval: " + request.getReason());

        log.info("[Suspend] Suspended run: runId={}, sessionId={}, reason={}",
                request.getRunId(), request.getSessionId(), request.getReason());

        return SuspendResult.success(request.getRunId());
    }

    /**
     * 恢复挂起的运行记录。
     */
    @Transactional
    public ResumeResult resume(ResumeRequest request) {
        SubagentSuspend suspendRecord = repository.findByRunId(request.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("Suspend record not found: " + request.getRunId()));

        if (suspendRecord.getStatus() != SubagentSuspend.SuspendStatus.PENDING) {
            return ResumeResult.error("Suspend record is not pending: " + suspendRecord.getStatus());
        }

        // 更新挂起记录
        suspendRecord.setStatus("APPROVED".equals(request.getApprovalResult())
                ? SubagentSuspend.SuspendStatus.APPROVED
                : SubagentSuspend.SuspendStatus.REJECTED);
        suspendRecord.setApproverId(request.getApproverId());
        suspendRecord.setApprovalResult(request.getApprovalResult());
        suspendRecord.setApprovedAt(Instant.now());
        suspendRecord.setUpdatedAt(Instant.now());

        repository.save(suspendRecord);

        // 根据审批结果更新运行记录状态
        if ("APPROVED".equals(request.getApprovalResult())) {
            runService.updateStatus(request.getRunId(), SubagentRun.RunStatus.RUNNING, "Resumed after approval");
            log.info("[Resume] Resumed run: runId={}, approver={}", request.getRunId(), request.getApproverId());
            return ResumeResult.approved(request.getRunId());
        } else {
            runService.updateStatus(request.getRunId(), SubagentRun.RunStatus.FAILED, "Rejected: " + request.getApprovalResult());
            log.info("[Resume] Rejected run: runId={}, approver={}", request.getRunId(), request.getApproverId());
            return ResumeResult.rejected(request.getRunId());
        }
    }

    /**
     * 获取挂起记录。
     */
    public SubagentSuspend getSuspendRecord(String runId) {
        return repository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Suspend record not found: " + runId));
    }

    /**
     * 获取会话中所有待审批的挂起记录。
     */
    public List<SubagentSuspend> getPendingSuspensions(String sessionId) {
        return repository.findBySessionIdAndStatus(sessionId, SubagentSuspend.SuspendStatus.PENDING);
    }

    /**
     * 获取所有待审批的挂起记录。
     */
    public List<SubagentSuspend> getAllPendingSuspensions() {
        return repository.findByStatus(SubagentSuspend.SuspendStatus.PENDING);
    }

    /**
     * 取消挂起（由系统自动清理）。
     */
    @Transactional
    public void cancelSuspension(String runId) {
        SubagentSuspend suspendRecord = repository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Suspend record not found: " + runId));

        if (suspendRecord.getStatus() == SubagentSuspend.SuspendStatus.PENDING) {
            suspendRecord.setStatus(SubagentSuspend.SuspendStatus.CANCELLED);
            suspendRecord.setUpdatedAt(Instant.now());
            repository.save(suspendRecord);
        }
    }

    /**
     * 挂起结果。
     */
    public record SuspendResult(String runId, boolean success, String message) {
        public static SuspendResult success(String runId) {
            return new SuspendResult(runId, true, "Suspended successfully");
        }

        public static SuspendResult error(String message) {
            return new SuspendResult(null, false, message);
        }
    }

    /**
     * 恢复结果。
     */
    public record ResumeResult(String runId, boolean success, String message, String approvalResult) {
        public static ResumeResult approved(String runId) {
            return new ResumeResult(runId, true, "Approved", "APPROVED");
        }

        public static ResumeResult rejected(String runId) {
            return new ResumeResult(runId, true, "Rejected", "REJECTED");
        }

        public static ResumeResult error(String message) {
            return new ResumeResult(null, false, message, null);
        }
    }
}
