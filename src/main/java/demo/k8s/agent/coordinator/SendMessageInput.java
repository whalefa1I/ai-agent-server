package demo.k8s.agent.coordinator;

/** 与 SendMessage 工具入参对齐的最小字段（演示占位）。 */
public record SendMessageInput(String task_id, String message) {}
