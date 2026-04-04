package demo.k8s.agent.query;

/**
 * 一次用户输入经 {@link AgenticQueryLoop} 跑完后的结果。
 */
public record AgenticTurnResult(LoopTerminalReason reason, String replyText, QueryLoopState state) {}
