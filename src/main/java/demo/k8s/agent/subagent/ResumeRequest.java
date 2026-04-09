package demo.k8s.agent.subagent;

/**
 * 恢复请求（v1 Human-in-the-Loop）。
 * <p>
 * 当人工审批完成后，使用此请求恢复挂起的任务。
 */
public class ResumeRequest {
    private final String runId;
    private final String sessionId;
    private final String approvalResult;
    private final String approverId;

    public ResumeRequest(String runId, String sessionId, String approvalResult, String approverId) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.approvalResult = approvalResult;
        this.approverId = approverId;
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getApprovalResult() {
        return approvalResult;
    }

    public String getApproverId() {
        return approverId;
    }

    /**
     * 创建恢复请求。
     */
    public static ResumeRequest resume(String runId, String sessionId, String approvalResult, String approverId) {
        return new ResumeRequest(runId, sessionId, approvalResult, approverId);
    }

    /**
     * 创建批准的恢复请求。
     */
    public static ResumeRequest approve(String runId, String sessionId, String approverId) {
        return new ResumeRequest(runId, sessionId, "APPROVED", approverId);
    }

    /**
     * 创建拒绝的恢复请求。
     */
    public static ResumeRequest reject(String runId, String sessionId, String approverId, String reason) {
        return new ResumeRequest(runId, sessionId, "REJECTED: " + reason, approverId);
    }
}
