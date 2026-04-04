package demo.k8s.agent.query;

/**
 * 与 {@code query.ts} 中 {@code Terminal.reason} 对齐的退出原因（子集）。
 */
public enum LoopTerminalReason {

    COMPLETED,

    MAX_TURNS,

    MAX_OUTPUT_TOKENS_RECOVERY_EXHAUSTED,

    PROMPT_TOO_LONG,

    MODEL_ERROR,

    ABORTED,

    ERROR
}
