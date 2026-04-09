package demo.k8s.agent.subagent;

/**
 * 派生结果（v1 M4 契约冻结）。
 */
public class SpawnResult {

    private final boolean success;
    private final String runId;
    private final String message;
    private final MustDoNext mustDoNext;

    public SpawnResult(boolean success, String runId, String message, MustDoNext mustDoNext) {
        this.success = success;
        this.runId = runId;
        this.message = message;
        this.mustDoNext = mustDoNext;
    }

    /**
     * 成功派生
     */
    public static SpawnResult success(String runId) {
        return new SpawnResult(true, runId, "Subagent spawned successfully", null);
    }

    /**
     * 失败：门控拒绝
     */
    public static SpawnResult rejected(String reason, MustDoNext mustDoNext) {
        return new SpawnResult(false, null, reason, mustDoNext);
    }

    /**
     * 失败：系统错误
     */
    public static SpawnResult error(String message) {
        return new SpawnResult(false, null, message, MustDoNext.none());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getRunId() {
        return runId;
    }

    public String getMessage() {
        return message;
    }

    public MustDoNext getMustDoNext() {
        return mustDoNext;
    }

    /**
     * 下一步行动建议（结构化，防止模型循环重试）
     */
    public record MustDoNext(
            Action action,
            String reason,
            String suggestion
    ) {
        public enum Action {
            NONE,           // 不做任何事，直接回答
            RETRY,          // 重试（需附带建议）
            SIMPLIFY,       // 简化请求
            USE_LOCAL,      // 使用本地工具直接执行
            ASK_HUMAN       // 请求人类介入
        }

        public static MustDoNext none() {
            return new MustDoNext(Action.NONE, null, null);
        }

        public static MustDoNext retry(String suggestion) {
            return new MustDoNext(Action.RETRY, "retry", suggestion);
        }

        public static MustDoNext simplify(String suggestion) {
            return new MustDoNext(Action.SIMPLIFY, "simplify", suggestion);
        }

        public static MustDoNext useLocal(String suggestion) {
            return new MustDoNext(Action.USE_LOCAL, "use_local", suggestion);
        }

        public static MustDoNext askHuman(String suggestion) {
            return new MustDoNext(Action.ASK_HUMAN, "ask_human", suggestion);
        }
    }
}
