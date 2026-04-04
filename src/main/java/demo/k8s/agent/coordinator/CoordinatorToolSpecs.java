package demo.k8s.agent.coordinator;

import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;

/**
 * Coordinator 独占工具的 {@link ClaudeLikeTool} 元数据（用于 {@link demo.k8s.agent.toolsystem.ToolRegistry} 过滤与文档）。
 */
public final class CoordinatorToolSpecs {

    private CoordinatorToolSpecs() {}

    public static ClaudeLikeTool sendMessage() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "SendMessage",
                        ToolCategory.AGENT,
                        "向 worker 发送跟进消息（协调者专用）。",
                        "{"
                                + "\"type\":\"object\","
                                + "\"properties\":{"
                                + "\"task_id\":{\"type\":\"string\",\"description\":\"Task 返回的 task_id 或 agent 标识\"},"
                                + "\"message\":{\"type\":\"string\",\"description\":\"跟进指令\"}"
                                + "},"
                                + "\"required\":[\"task_id\",\"message\"]"
                                + "}",
                        null,
                        false));
    }

    public static ClaudeLikeTool taskStop() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "TaskStop",
                        ToolCategory.AGENT,
                        "终止正在运行的 worker（协调者专用）。",
                        "{"
                                + "\"type\":\"object\","
                                + "\"properties\":{\"task_id\":{\"type\":\"string\",\"description\":\"要停止的任务 ID\"}},"
                                + "\"required\":[\"task_id\"]"
                                + "}",
                        null,
                        false));
    }
}
