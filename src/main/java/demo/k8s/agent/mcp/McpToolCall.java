package demo.k8s.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * MCP 工具调用封装。
 */
public class McpToolCall implements Function<String, String> {

    private static final Logger log = LoggerFactory.getLogger(McpToolCall.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String serverName;
    private final String toolName;
    private final McpServerConnection connection;
    private final ToolDefinition toolDefinition;

    public McpToolCall(String serverName, String toolName, McpServerConnection connection, ToolDefinition toolDefinition) {
        this.serverName = serverName;
        this.toolName = toolName;
        this.connection = connection;
        this.toolDefinition = toolDefinition;
    }

    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String apply(String inputJson) {
        return call(inputJson);
    }

    public String call(String inputJson) {
        try {
            Map<String, Object> arguments = OBJECT_MAPPER.readValue(inputJson, Map.class);
            JsonNode result = connection.callTool(toolName, arguments);

            if (result == null) {
                return "{\"error\": \"Tool call failed: no response from MCP server\"}";
            }

            // 处理 MCP 工具调用响应格式
            // MCP 返回格式：{ "content": [...], "isError": false }
            if (result.has("content")) {
                JsonNode content = result.get("content");
                if (content.isArray() && content.size() > 0) {
                    JsonNode firstContent = content.get(0);
                    if (firstContent.has("text")) {
                        return firstContent.get("text").asText();
                    }
                }
            }

            if (result.has("isError") && result.get("isError").asBoolean()) {
                String errorText = result.has("text") ? result.get("text").asText() : "Unknown error";
                return "{\"error\": \"" + errorText + "\"}";
            }

            return OBJECT_MAPPER.writeValueAsString(result);

        } catch (Exception e) {
            log.error("调用 MCP 工具失败：{}.{}", serverName, toolName, e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public CompletableFuture<String> callAsync(String inputJson) {
        return CompletableFuture.completedFuture(call(inputJson));
    }
}
