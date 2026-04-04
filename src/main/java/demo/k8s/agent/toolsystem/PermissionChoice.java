package demo.k8s.agent.toolsystem;

/**
 * 用户对权限请求的响应选择，与 Claude Code Permission Dialog 对齐。
 */
public enum PermissionChoice {

    /**
     * 本次允许：仅允许当前这次工具调用
     */
    ALLOW_ONCE("本次允许", null),

    /**
     * 会话允许：当前会话内允许同类工具调用
     */
    ALLOW_SESSION("会话允许", 30 * 60 * 1000L), // 30 分钟

    /**
     * 始终允许：持久化授权（直到用户手动撤销）
     */
    ALLOW_ALWAYS("始终允许", null),

    /**
     * 拒绝：不允许本次调用
     */
    DENY("拒绝", null);

    private final String label;
    private final Long sessionDurationMs; // null = 永不过期或单次有效

    PermissionChoice(String label, Long sessionDurationMs) {
        this.label = label;
        this.sessionDurationMs = sessionDurationMs;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 会话授权的持续时间（毫秒）
     * @return null 表示永不过期（ALLOW_ALWAYS）或单次有效（ALLOW_ONCE/DENY）
     */
    public Long getSessionDurationMs() {
        return sessionDurationMs;
    }

    /**
     * 是否为允许的选择
     */
    public boolean isAllowed() {
        return this == ALLOW_ONCE || this == ALLOW_SESSION || this == ALLOW_ALWAYS;
    }

    /**
     * 是否需要持久化授权
     */
    public boolean isPersistent() {
        return this == ALLOW_ALWAYS;
    }
}
