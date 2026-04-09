package demo.k8s.agent.contextobject;

/**
 * 产生外置上下文对象的来源（主会话 compaction、子 Agent、其它）。
 */
public enum ProducerKind {
    MAIN,
    SUBAGENT,
    COMPACTION,
    OTHER
}
