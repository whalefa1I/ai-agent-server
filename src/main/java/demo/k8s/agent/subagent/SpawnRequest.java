package demo.k8s.agent.subagent;

/**
 * 子 Agent 派生请求（v1 M4 契约冻结，v2 兼容）。
 * <p>
 * 对齐 v2 双栈架构的 SpawnRequest 协议，本地/远端运行时同签名。
 * <p>
 * version 字段用于未来协议演进：当前版本为 "v1"。
 */
public class SpawnRequest {

    /**
     * 协议版本号；当前为 "v1"。
     */
    private final String version;
    private final String traceId;
    private final String sessionId;
    private final String tenantId;
    private final String appId;
    /**
     * 任务短名（展示用）；可为 null，由运行时从 goal 推导。
     */
    private final String taskName;
    /**
     * Worker 类型（如 general / explore）；可为 null，默认为 general。
     */
    private final String agentType;
    private final String goal;
    /**
     * 父运行 ID；可为 null，表示根运行。
     */
    private final String parentRunId;
    private final SpawnConstraints constraints;

    public SpawnRequest(String version, String traceId, String sessionId, String tenantId, String appId,
                        String taskName, String agentType, String goal, String parentRunId,
                        SpawnConstraints constraints) {
        this.version = version != null ? version : "v1";
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.appId = appId;
        this.taskName = taskName;
        this.agentType = agentType;
        this.goal = goal;
        this.parentRunId = parentRunId;
        this.constraints = constraints;
    }

    /**
     * 向后兼容构造函数（默认 version="v1", parentRunId=null）
     */
    public SpawnRequest(String traceId, String sessionId, String tenantId, String appId,
                        String taskName, String agentType, String goal, SpawnConstraints constraints) {
        this("v1", traceId, sessionId, tenantId, appId, taskName, agentType, goal, null, constraints);
    }

    public String getVersion() {
        return version;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAppId() {
        return appId;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getAgentType() {
        return agentType;
    }

    public String getGoal() {
        return goal;
    }

    public String getParentRunId() {
        return parentRunId;
    }

    public SpawnConstraints getConstraints() {
        return constraints;
    }

    /**
     * 派生约束
     */
    public record SpawnConstraints(
            int maxBudgetTokens,
            int maxDepth,
            java.util.Set<String> allowedToolScopes,
            long deadlineEpochMs
    ) {}
}
