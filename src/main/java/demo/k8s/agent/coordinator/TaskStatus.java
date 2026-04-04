package demo.k8s.agent.coordinator;

/**
 * 任务状态枚举。
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
    STOPPED
}
