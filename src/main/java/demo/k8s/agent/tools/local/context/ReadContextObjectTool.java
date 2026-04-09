package demo.k8s.agent.tools.local.context;

import demo.k8s.agent.contextobject.ContextObjectReadService;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;

import java.util.Map;

/**
 * 按 id 读取外置超长工具结果（会话与租户由服务端隐式绑定，模型不可传 conversationId）。
 */
public final class ReadContextObjectTool {

    private static final String INPUT_SCHEMA =
            """
                    {
                      "type": "object",
                      "properties": {
                        "id": { "type": "string", "description": "Context object id (e.g. ctx-obj-...)" },
                        "offset": { "type": "integer", "description": "Character offset into stored text (optional)" },
                        "limit": { "type": "integer", "description": "Max characters to return (optional; capped by server)" }
                      },
                      "required": ["id"]
                    }
                    """;

    private ReadContextObjectTool() {}

    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "read_context_object",
                        ToolCategory.FILE_SYSTEM,
                        "Retrieve full or partial text for a truncated tool result using its context object id.",
                        INPUT_SCHEMA,
                        null,
                        true));
    }

    public static LocalToolResult execute(Map<String, Object> input, ContextObjectReadService service) {
        if (service == null) {
            return LocalToolResult.error("read_context_object service unavailable");
        }
        String id = stringOrNull(input.get("id"));
        Integer offset = intOrNull(input.get("offset"));
        Integer limit = intOrNull(input.get("limit"));
        String text = service.read(id, offset, limit);
        return LocalToolResult.success(text);
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer intOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
