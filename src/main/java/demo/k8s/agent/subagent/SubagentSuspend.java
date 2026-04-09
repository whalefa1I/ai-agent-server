package demo.k8s.agent.subagent;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 子 Agent 挂起审批记录（v1 Human-in-the-Loop）。
 */
@Entity
@Table(name = "subagent_suspend")
public class SubagentSuspend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 96)
    private String runId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SuspendStatus status = SuspendStatus.PENDING;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "approval_required", columnDefinition = "TEXT")
    private String approvalRequired;

    @Column(name = "approver_id", length = 64)
    private String approverId;

    @Column(name = "approval_result", length = 32)
    private String approvalResult;

    @Column(name = "approval_comment", columnDefinition = "TEXT")
    private String approvalComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public SuspendStatus getStatus() {
        return status;
    }

    public void setStatus(SuspendStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(String approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public String getApproverId() {
        return approverId;
    }

    public void setApproverId(String approverId) {
        this.approverId = approverId;
    }

    public String getApprovalResult() {
        return approvalResult;
    }

    public void setApprovalResult(String approvalResult) {
        this.approvalResult = approvalResult;
    }

    public String getApprovalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    /**
     * 挂起状态
     */
    public enum SuspendStatus {
        PENDING,    // 等待审批
        APPROVED,   // 已批准
        REJECTED,   // 已拒绝
        CANCELLED   // 已取消
    }
}
