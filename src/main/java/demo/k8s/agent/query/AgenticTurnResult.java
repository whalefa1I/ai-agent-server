package demo.k8s.agent.query;

import demo.k8s.agent.observability.TokenCounts;

/**
 * 一次用户输入经 {@link AgenticQueryLoop} 跑完后的结果。
 */
public record AgenticTurnResult(
        LoopTerminalReason reason,
        String replyText,
        QueryLoopState state,
        TokenCounts tokenCounts
) {
    public AgenticTurnResult(LoopTerminalReason reason, String replyText, QueryLoopState state) {
        this(reason, replyText, state, TokenCounts.ZERO);
    }
}
