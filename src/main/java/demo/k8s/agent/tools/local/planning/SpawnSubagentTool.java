package demo.k8s.agent.tools.local.planning;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.MultiAgentFacade;
import demo.k8s.agent.subagent.SpawnGatekeeper;
import demo.k8s.agent.subagent.SpawnResult;
import demo.k8s.agent.subagent.SubagentRun;
import demo.k8s.agent.subagent.SubagentRunService;
import demo.k8s.agent.tools.local.LocalToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * spawn_subagent 工具 - 直接派生子 Agent 执行任务。
 * <p>
 * 与 TaskCreate + useSubagent 的区别：
 * - TaskCreate + useSubagent：需要先创建 Task，再由路由决定走子 Agent
 * - spawn_subagent：直接派生子 Agent，不经过 Task 系统
 * <p>
 * 适用场景：
 * - 需要并行处理多个独立子任务（如"翻译到 5 种语言"、"处理 10 个文件"）
 * - 复杂任务可以拆解为多个并行分支
 * <p>
 * 与 Claude Code 的 delegate / spawn 工具对齐。
 */
@Component
public class SpawnSubagentTool {

    private static final Logger log = LoggerFactory.getLogger(SpawnSubagentTool.class);

    private final DemoMultiAgentProperties multiAgentProperties;
    private final ObjectProvider<MultiAgentFacade> multiAgentFacade;
    private final SpawnGatekeeper spawnGatekeeper;
    private final SubagentRunService subagentRunService;

    public SpawnSubagentTool(
            DemoMultiAgentProperties multiAgentProperties,
            ObjectProvider<MultiAgentFacade> multiAgentFacade,
            SpawnGatekeeper spawnGatekeeper,
            SubagentRunService subagentRunService) {
        this.multiAgentProperties = multiAgentProperties;
        this.multiAgentFacade = multiAgentFacade;
        this.spawnGatekeeper = spawnGatekeeper;
        this.subagentRunService = subagentRunService;
    }

    /**
     * spawn_subagent 工具提示词（与 Claude Code 的 delegate / spawn 对齐）
     */
    public static final String SPAWN_SUBAGENT_PROMPT = """
            Use this tool to spawn a subagent that handles complex or parallelizable tasks.

            ## When to Use spawn_subagent

            Spawn a subagent when:
            - You need to process multiple independent items in parallel (e.g., "Translate to 5 languages", "Process 10 files")
            - The task can be split into parallel branches that do not depend on each other
            - You want to leverage parallel execution for speed
            - The task is complex enough to benefit from independent execution

            ## When NOT to Use spawn_subagent

            Do NOT spawn a subagent for:
            - Simple sequential tasks (e.g., "Read this file, then edit it, then save")
            - Tasks that must be done one after another due to dependencies
            - When you want to handle the task directly with the main agent

            ## Examples

            **Good use cases (spawn subagent):**
            - "Translate this document to Spanish, French, German, Japanese, and Korean"
            - "Generate monthly reports for each of the 12 sales regions"
            - "Analyze these 5 datasets and produce summaries"
            - "Create 10 unit tests for the authentication module"

            **Bad use cases (do NOT spawn):**
            - "Read the config file and tell me what it contains"
            - "Fix the bug in the login function"
            - "Write a new feature that adds user profile"

            ## Parameters

            - **goal** (required): Clear, specific description of what the subagent should accomplish
            - **agentType** (optional): Type of agent to spawn ("general", "worker", "bash", "explore", "edit", "plan")
              - "general": General-purpose agent (default)
              - "worker": Worker agent with full tool access
              - "bash": Shell specialist for command execution
              - "explore": Code explorer for reading/searching code
              - "edit": Code editor for file modifications
              - "plan": Planning specialist for complex task breakdown

            ## Example Usage

            ```json
            {
              "goal": "Translate the following text to Spanish, Japanese, French, German, and Korean. Each translation should be saved to a separate file: [original text here]",
              "agentType": "worker"
            }
            ```

            ```json
            {
              "goal": "Analyze the project structure and identify all TODO comments in TypeScript files",
              "agentType": "explore"
            }
            ```
            """;

    /**
     * spawn_subagent 输入 Schema
     */
    public static final String SPAWN_SUBAGENT_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"goal\": {\"type\": \"string\", \"description\": \"Clear, specific description of what the subagent should accomplish\"}," +
            "    \"agentType\": {\"type\": \"string\", \"enum\": [\"general\", \"worker\", \"bash\", \"explore\", \"edit\", \"plan\"], \"description\": \"Type of agent to spawn (default: general)\"}" +
            "  }," +
            "  \"required\": [\"goal\"]" +
            "}";

    /**
     * 创建 spawn_subagent 工具
     */
    public demo.k8s.agent.toolsystem.ClaudeLikeTool createSpawnSubagentTool() {
        return createSpawnSubagentToolSpec();
    }

    /**
     * 创建 spawn_subagent 工具规格（供配置类使用）
     */
    public demo.k8s.agent.toolsystem.ClaudeLikeTool createSpawnSubagentToolSpec() {
        String detailedDescription = """
                Spawn a subagent to handle complex or parallelizable tasks.

                Required parameter:
                - goal: Clear description of what the subagent should accomplish

                Optional parameter:
                - agentType: Type of agent to spawn ("general", "worker", "bash", "explore", "edit", "plan")

                Example (parallel translation task):
                {
                  "goal": "Translate product description to Spanish, Japanese, French, German, and Korean. Each translation saved to a separate file.",
                  "agentType": "worker"
                }

                Example (code exploration):
                {
                  "goal": "Find all TODO comments in TypeScript files and summarize by category",
                  "agentType": "explore"
                }

                When to use spawn_subagent:
                - Parallel processing of multiple independent items (e.g., "Translate to 5 languages", "Process 10 files")
                - Complex tasks that can be split into parallel branches
                - Tasks that benefit from independent execution

                When NOT to use spawn_subagent:
                - Simple sequential tasks
                - Tasks with dependencies that require order
                - When you want to handle the task directly
                """;

        return demo.k8s.agent.toolsystem.ClaudeToolFactory.buildTool(
                new demo.k8s.agent.toolsystem.ToolDefPartial(
                        "spawn_subagent",
                        demo.k8s.agent.toolsystem.ToolCategory.PLANNING,
                        detailedDescription,
                        SPAWN_SUBAGENT_INPUT_SCHEMA,
                        null,
                        false),
                (json, ctx) -> null, // 由 PermissionManager 检查
                (input) -> {
                    if (!input.has("goal") || input.get("goal").asText("").isBlank()) {
                        return "goal is required and cannot be empty";
                    }
                    return null;
                });
    }

    /**
     * 执行 spawn_subagent 工具
     */
    public LocalToolResult executeSpawnSubagent(Map<String, Object> input) {
        try {
            // 1. 检查子 Agent 系统是否启用
            if (!multiAgentProperties.isEnabled()
                    || multiAgentProperties.getMode() == DemoMultiAgentProperties.Mode.off) {
                log.debug("[SpawnSubagent] Multi-agent disabled, rejecting spawn");
                return spawnRejected("Multi-agent system is disabled. Use local tools instead.");
            }

            // 2. Shadow 模式：只记录不执行
            if (multiAgentProperties.getMode() == DemoMultiAgentProperties.Mode.shadow) {
                log.debug("[SpawnSubagent] Shadow mode, blocking spawn");
                return spawnRejected("Shadow mode: spawn evaluation only");
            }

            // 3. 解析输入参数
            String goal = input.get("goal") != null ? input.get("goal").asText() : "";
            String agentType = input.get("agentType") != null ? input.get("agentType").asText() : "general";

            if (goal == null || goal.isBlank()) {
                return spawnRejected("goal is required and cannot be empty");
            }

            // 4. 获取会话上下文
            String sessionId = TraceContext.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                return spawnRejected("No active session for subagent spawn");
            }

            // 5. 检查门控并派生子 Agent
            log.info("[SpawnSubagent] Spawning subagent: sessionId={}, goal={}, agentType={}",
                    sessionId, truncate(goal, 100), agentType);

            Set<String> allowed = spawnGatekeeper.globalSafeToolNames();
            SpawnResult spawnResult = multiAgentFacade.getObject().spawnTask(
                    "spawn_subagent_task", // taskName
                    goal,
                    agentType,
                    0, // currentDepth
                    allowed);

            if (!spawnResult.isSuccess()) {
                String msg = spawnResult.getMessage() != null ? spawnResult.getMessage() : "spawn failed";
                if (spawnResult.getMustDoNext() != null && spawnResult.getMustDoNext().suggestion() != null) {
                    msg = spawnResult.getMustDoNext().suggestion();
                }
                log.info("[SpawnSubagent] Spawn rejected or failed: sessionId={}, goal={}, msg={}",
                        sessionId, truncate(goal, 50), msg);
                return spawnRejected(msg);
            }

            // 6. 获取子 Agent 运行状态
            String runId = spawnResult.getRunId();
            SubagentRun row = subagentRunService.getRun(runId, sessionId);
            String preview = row.getResult() != null ? row.getResult() : "";

            log.info("[SpawnSubagent] Spawn success: sessionId={}, runId={}, runStatus={}",
                    sessionId, runId, row.getStatus());

            // 7. 返回结果（与 TaskCreate 成功响应格式对齐）
            return spawnSuccess(runId, goal, preview);

        } catch (Exception e) {
            log.error("[SpawnSubagent] Failed", e);
            return spawnRejected(e.getMessage() != null ? e.getMessage() : "spawn error");
        }
    }

    private LocalToolResult spawnRejected(String message) {
        return LocalToolResult.builder()
                .success(false)
                .error(message)
                .executionLocation("local")
                .build();
    }

    private LocalToolResult spawnSuccess(String runId, String goal, String resultPreview) {
        Map<String, Object> output = new java.util.LinkedHashMap<>();
        Map<String, Object> subagentInfo = new java.util.LinkedHashMap<>();
        subagentInfo.put("runId", runId);
        subagentInfo.put("goal", goal);
        output.put("subagent", subagentInfo);

        String content = "Subagent spawned successfully: runId=" + runId + ", goal=" + truncate(goal, 100);
        if (resultPreview != null && !resultPreview.isBlank()) {
            content += ", result=" + truncate(resultPreview, 200);
        }

        return LocalToolResult.builder()
                .success(true)
                .content(content)
                .executionLocation("local")
                .metadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(output))
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
