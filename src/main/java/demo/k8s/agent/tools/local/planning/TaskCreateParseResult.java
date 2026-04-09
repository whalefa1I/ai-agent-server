package demo.k8s.agent.tools.local.planning;

import demo.k8s.agent.tools.local.LocalToolResult;

import java.util.Map;

/**
 * TaskCreate 参数解析结果（内存路径与 MultiAgent 路由共用）。
 */
public sealed interface TaskCreateParseResult permits TaskCreateParseResult.ParseError, TaskCreateParseResult.Parsed {

    record ParseError(LocalToolResult error) implements TaskCreateParseResult {}

    record Parsed(String subject, String description, String activeForm, Map<String, Object> metadata)
            implements TaskCreateParseResult {}
}
