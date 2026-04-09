package demo.k8s.agent.query;

/**
 * 对应 TS {@code State.transition}：本轮为何继续外循环（便于日志 / 测试断言，不解析消息正文）。
 */
public enum ContinuationReason {

    INITIAL,

    TOOL_FOLLOW_UP,

    NEXT_TURN,

    /** 与 {@code reactive_compact_retry} / {@code collapse_drain_retry} 等类比 */
    POST_COMPACTION_RETRY,

    RETRY_AFTER_ERROR,

    TERMINAL_NO_TOOLS
}
