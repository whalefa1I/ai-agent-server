package demo.k8s.agent.tools.local.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.SubagentRun;
import demo.k8s.agent.subagent.SubagentRunService;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * query_subagent_result 工具 - 查询子 Agent 运行结果。
 * <p>
 * 用于模型在派生子 Agent 后，主动查询某个子任务的执行结果。
 */
@Component
public class QuerySubagentResultTool {

    private static final Logger log = LoggerFactory.getLogger(QuerySubagentResultTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectProvider<SubagentRunService> subagentRunServiceProvider;

    public QuerySubagentResultTool(ObjectProvider<SubagentRunService> subagentRunServiceProvider) {
        this.subagentRunServiceProvider = subagentRunServiceProvider;
    }

    public static final String QUERY_SUBAGENT_RESULT_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"runId\": {\"type\": \"string\", \"description\": \"The subagent run ID to query (e.g., run-1234567890-abcd1234)\"}" +
            "  }," +
            "  \"required\": [\"runId\"]" +
            "}";

    /**
     * 创建 query_subagent_result 工具
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "query_subagent_result",
                        ToolCategory.PLANNING,
                        """
                        Query the result of a previously spawned subagent.

                        Use this tool when you need to retrieve the output of a subagent
                        that was spawned earlier using spawn_subagent.

                        Parameters:
                        - runId (required): The run ID returned when the subagent was spawned.

                        Example:
                        {
                          "runId": "run-1234567890-abcd1234"
                        }
                        """,
                        QUERY_SUBAGENT_RESULT_INPUT_SCHEMA,
                        null,
                        true),
                null,
                (input) -> {
                    if (!input.has("runId") || input.get("runId").asText("").isBlank()) {
                        return "runId is required and cannot be empty";
                    }
                    return null;
                });
    }

    /**
     * 执行查询
     */
    public LocalToolResult executeQuery(Map<String, Object> input) {
        try {
            String runId = input.get("runId") != null
                    ? MAPPER.convertValue(input.get("runId"), String.class)
                    : "";

            if (runId.isBlank()) {
                return queryError("runId is required");
            }

            String sessionId = TraceContext.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                return queryError("No active session");
            }

            SubagentRunService runService = subagentRunServiceProvider.getObject();
            if (runService == null) {
                return queryError("Subagent run service not available");
            }

            SubagentRun run = runService.getRun(runId, sessionId);

            // 构建响应
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("runId", run.getRunId());
            output.put("status", run.getStatus().name().toLowerCase());
            output.put("goal", run.getGoal());
            if (run.getResult() != null && !run.getResult().isBlank()) {
                output.put("result", run.getResult());
            }
            if (run.getErrorMessage() != null && !run.getErrorMessage().isBlank()) {
                output.put("errorMessage", run.getErrorMessage());
            }
            if (run.getBatchId() != null) {
                output.put("batchId", run.getBatchId());
            }

            StringBuilder content = new StringBuilder();
            content.append("Subagent result: runId=").append(runId)
                    .append(", status=").append(run.getStatus().name().toLowerCase());
            if (run.getGoal() != null) {
                content.append(", goal=").append(truncate(run.getGoal(), 100));
            }
            if (run.getResult() != null && !run.getResult().isBlank()) {
                content.append("\n\nResult:\n").append(run.getResult());
            } else if (run.getErrorMessage() != null && !run.getErrorMessage().isBlank()) {
                content.append("\n\nError:\n").append(run.getErrorMessage());
            } else {
                content.append("\n\nNo result available yet (status: ").append(run.getStatus()).append(")");
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(content.toString())
                    .executionLocation("local")
                    .metadata(MAPPER.valueToTree(output))
                    .build();

        } catch (IllegalArgumentException e) {
            return queryError("Run not found: " + e.getMessage());
        } catch (Exception e) {
            log.error("[QuerySubagentResult] Failed", e);
            return queryError(e.getMessage() != null ? e.getMessage() : "query error");
        }
    }

    private static LocalToolResult queryError(String message) {
        return LocalToolResult.builder()
                .success(false)
                .error(message)
                .executionLocation("local")
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
