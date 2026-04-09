package demo.k8s.agent.subagent;

/**
 * 子 Agent 派生请求（v1 M4 契约冻结）。
 * <p>
 * 对齐 v2 双栈架构的 SpawnRequest 协议，本地/远端运行时同签名。
 */
public class SpawnRequest {

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
    private final SpawnConstraints constraints;

    public SpawnRequest(String traceId, String sessionId, String tenantId, String appId,
                        String taskName, String agentType, String goal, SpawnConstraints constraints) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.appId = appId;
        this.taskName = taskName;
        this.agentType = agentType;
        this.goal = goal;
        this.constraints = constraints;
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
