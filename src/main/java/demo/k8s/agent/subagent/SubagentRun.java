package demo.k8s.agent.subagent;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 子 Agent 运行记录（v1 M5 持久化）。
 */
@Entity
@Table(name = "subagent_run")
public class SubagentRun {

    @Id
    @Column(length = 96)
    private String runId;

    @Column(name = "parent_run_id", length = 96)
    private String parentRunId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "app_id", length = 64)
    private String appId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RunStatus status = RunStatus.RUNNING;

    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    @Column(name = "spec_json", columnDefinition = "TEXT")
    private String specJson;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "depth", nullable = false)
    private int depth = 0;

    @Column(name = "token_budget", nullable = false)
    private int tokenBudget = 8000;

    @Column(name = "allowed_tools", length = 1024)
    private String allowedTools;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "batch_total", nullable = false)
    private int batchTotal = 1;

    @Column(name = "batch_index", nullable = false)
    private int batchIndex = 0;

    @Column(name = "main_run_id", length = 64)
    private String mainRunId;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getParentRunId() {
        return parentRunId;
    }

    public void setParentRunId(String parentRunId) {
        this.parentRunId = parentRunId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Instant deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getTokenBudget() {
        return tokenBudget;
    }

    public void setTokenBudget(int tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public String getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(String allowedTools) {
        this.allowedTools = allowedTools;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public int getBatchTotal() {
        return batchTotal;
    }

    public void setBatchTotal(int batchTotal) {
        this.batchTotal = batchTotal;
    }

    public int getBatchIndex() {
        return batchIndex;
    }

    public void setBatchIndex(int batchIndex) {
        this.batchIndex = batchIndex;
    }

    public String getMainRunId() {
        return mainRunId;
    }

    public void setMainRunId(String mainRunId) {
        this.mainRunId = mainRunId;
    }

    /**
     * 运行状态（对齐 SubRunEvent.EventType）
     */
    public enum RunStatus {
        PENDING,
        RUNNING,
        WAITING,
        SUSPENDED,
        COMPLETED,
        FAILED,
        TIMEOUT,
        REJECTED,
        CANCELLED
    }
}
