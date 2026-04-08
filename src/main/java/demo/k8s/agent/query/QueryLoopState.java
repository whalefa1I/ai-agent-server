package demo.k8s.agent.query;

/**
 * 跨迭代可变状态（与 {@code query.ts} 中 {@code State} 的精简投影）。
 */
public record QueryLoopState(
        int turnCount,
        int toolBatchCount,
        int compactionCount,
        int maxOutputTokensRecoveryCount,
        boolean hasAttemptedReactiveCompact,
        ContinuationReason lastContinuation) {

    public static QueryLoopState initial() {
        return new QueryLoopState(0, 0, 0, 0, false, ContinuationReason.INITIAL);
    }

    public QueryLoopState nextTurn(ContinuationReason reason) {
        return new QueryLoopState(
                turnCount + 1,
                toolBatchCount,
                compactionCount,
                maxOutputTokensRecoveryCount,
                hasAttemptedReactiveCompact,
                reason);
    }

    public QueryLoopState withToolBatchIncrement(ContinuationReason reason) {
        return new QueryLoopState(
                turnCount,
                toolBatchCount + 1,
                compactionCount,
                maxOutputTokensRecoveryCount,
                hasAttemptedReactiveCompact,
                reason);
    }

    public QueryLoopState withCompactionIncrement(ContinuationReason reason) {
        return new QueryLoopState(
                turnCount,
                toolBatchCount,
                compactionCount + 1,
                maxOutputTokensRecoveryCount,
                hasAttemptedReactiveCompact,
                reason);
    }

    public QueryLoopState withMaxOutputRecovery(int count) {
        return new QueryLoopState(
                turnCount, toolBatchCount, compactionCount, count, hasAttemptedReactiveCompact, lastContinuation);
    }

    public QueryLoopState withReactiveCompactAttempted() {
        return new QueryLoopState(
                turnCount, toolBatchCount, compactionCount, maxOutputTokensRecoveryCount, true, lastContinuation);
    }
}
