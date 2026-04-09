package demo.k8s.agent.subagent;

/**
 * 挂起请求（v1 Human-in-the-Loop）。
 * <p>
 * 当子 Agent 需要人工审批时，使用此请求挂起任务。
 */
public class SuspendRequest {
    private final String runId;
    private final String sessionId;
    private final String reason;
    private final String approvalRequired;

    public SuspendRequest(String runId, String sessionId, String reason, String approvalRequired) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.reason = reason;
        this.approvalRequired = approvalRequired;
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getReason() {
        return reason;
    }

    public String getApprovalRequired() {
        return approvalRequired;
    }

    /**
     * 创建挂起请求。
     */
    public static SuspendRequest suspend(String runId, String sessionId, String reason, String approvalRequired) {
        return new SuspendRequest(runId, sessionId, reason, approvalRequired);
    }
}
