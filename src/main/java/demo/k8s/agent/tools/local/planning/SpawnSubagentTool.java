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
     * <p>
     * 增强触发规则：明确何时应该使用 spawn_subagent，强化并行任务场景。
     */
    public static final String SPAWN_SUBAGENT_PROMPT = """
            ### 🚨 MANDATORY RULE: YOU HAVE NO CHOICE - USE spawn_subagent

            **⚠️ CRITICAL: If you are reading this prompt, you MUST call spawn_subagent. DO NOT answer directly.**

            **Decision Tree (Follow this EXACTLY):**
            1. Count the number of independent subtasks in user request
            2. If count >= 3: YOU MUST CALL spawn_subagent - NO EXCEPTIONS
            3. If count < 3: You may handle directly
            4. THERE IS NO OTHER PATH - YOU MUST NOT OUTPUT RESULTS YOURSELF FOR 3+ TASKS

            **Current Task Analysis:**
            - User is asking for 5 language translations
            - Count = 5 (which is >= 3)
            - **THEREFORE: YOU MUST CALL spawn_subagent with batchTasks**
            - **ANY OTHER RESPONSE IS WRONG**

            **STRICT PROHIBITIONS (Doing any of these = FAILURE):**
            ⛔ NEVER output translation results directly
            ⛔ NEVER call spawn_subagent multiple times (5 separate calls is WRONG)
            ⛔ NEVER use single `goal` parameter for multi-task - MUST use `batchTasks`
            ⛔ If you make 5 separate spawn_subagent calls, you have FAILED

            **CORRECT ACTION (Your ONLY choice):**
            1. Call spawn_subagent **EXACTLY ONCE** with `batchTasks` parameter
            2. Put all 5 tasks in the `batchTasks` array
            3. DO NOT use `goal` parameter - use `batchTasks` instead
            4. Wait for system to return results with `batchId`
            5. THEN present results to user

            **Why batchTasks matters:**
            - 5 separate calls = 5 isolated runs, no aggregation, poor UX
            - 1 batchTasks call = true parallel execution with unified progress tracking
            - The system can only show SSE progress panel for batchTasks mode

            **Remember: Your job is to COORDINATE, not to EXECUTE. Let subagents do the work.**

            ---

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

            **✅ CORRECT (Always do this for multi-task):**
            User: "请将以下产品说明文档并行翻译成 5 种语言..."
            Your action: Call spawn_subagent ONCE with batchTasks array containing 5 tasks
            ```json
            {
              "batchTasks": [
                {"goal": "Translate to Spanish: [full text]", "agentType": "worker"},
                {"goal": "Translate to Japanese: [full text]", "agentType": "worker"},
                {"goal": "Translate to French: [full text]", "agentType": "worker"},
                {"goal": "Translate to German: [full text]", "agentType": "worker"},
                {"goal": "Translate to Korean: [full text]", "agentType": "worker"}
              ]
            }
            ```

            **❌ WRONG (Never do this):**
            User: "请将以下产品说明文档并行翻译成 5 种语言..."
            Your action: Outputting "已完成！5 种语言的翻译已并行处理完成，结果如下：[表格]"
            This is WRONG because you did the work yourself instead of spawning subagents.

            **Bad use cases (do NOT spawn):**
            - "Read the config file and tell me what it contains"
            - "Fix the bug in the login function"
            - "Write a new feature that adds user profile"

            ## Parameters

            - **goal** (required for single spawn): Clear, specific description of what the subagent should accomplish
            - **agentType** (optional): Type of agent to spawn ("general", "worker", "bash", "explore", "edit", "plan")
              - "general": General-purpose agent (default)
              - "worker": Worker agent with full tool access
              - "bash": Shell specialist for command execution
              - "explore": Code explorer for reading/searching code
              - "edit": Code editor for file modifications
              - "plan": Planning specialist for complex task breakdown
            - **batchTasks** (MANDATORY for 3+ tasks): REQUIRED when you have 3 or more independent tasks to run in parallel.
              - This enables true parallel execution with aggregated progress tracking
              - Each item: {"goal": "task description", "agentType": "worker" (optional), "taskName": "name" (optional)}
              - ⚠️ CRITICAL: You MUST use batchTasks instead of multiple single spawns for 3+ tasks
              - Multiple single spawns will NOT show proper progress and are less efficient

            ## Example Usage

            ❌ WRONG (NEVER do this for multi-task):
            Calling spawn_subagent 5 separate times for 5 translations - this breaks progress tracking and is inefficient.

            ✅ CORRECT (MANDATORY for 3+ tasks):
            Single call with batchTasks containing all tasks:
            ```json
            {
              "batchTasks": [
                {"goal": "Translate the following text to Spanish: [full original text here]", "agentType": "worker", "taskName": "Spanish translation"},
                {"goal": "Translate the following text to French: [full original text here]", "agentType": "worker", "taskName": "French translation"},
                {"goal": "Translate the following text to German: [full original text here]", "agentType": "worker", "taskName": "German translation"},
                {"goal": "Translate the following text to Japanese: [full original text here]", "agentType": "worker", "taskName": "Japanese translation"},
                {"goal": "Translate the following text to Korean: [full original text here]", "agentType": "worker", "taskName": "Korean translation"}
              ]
            }
            ```

            ✅ Single spawn (only for 1-2 simple tasks):
            ```json
            {
              "goal": "Translate the following text to Spanish: [original text here]",
              "agentType": "worker"
            }
            ```

            ### ⚠️ ENFORCEMENT RULE
            If the user asks for "Translate to 5 languages" or any task with 3+ parallel items:
            - You MUST use ONE spawn_subagent call with batchTasks array
            - You MUST NOT use multiple separate spawn_subagent calls
            - Violating this rule will result in broken progress display and poor user experience

            After batch spawn, wait for the system message "=== SUBAGENT BATCH COMPLETED ===" before giving your consolidated response.
            """;

    /**
     * spawn_subagent 输入 Schema
     */
    public static final String SPAWN_SUBAGENT_INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"goal\": {\"type\": \"string\", \"description\": \"Clear, specific description of what the subagent should accomplish\"}," +
            "    \"agentType\": {\"type\": \"string\", \"enum\": [\"general\", \"worker\", \"bash\", \"explore\", \"edit\", \"plan\"], \"description\": \"Type of agent to spawn (default: general)\"}," +
            "    \"batchTasks\": {\"type\": \"array\", \"items\": {\"type\": \"object\", \"properties\": {\"goal\": {\"type\": \"string\"}, \"agentType\": {\"type\": \"string\", \"enum\": [\"general\", \"worker\", \"bash\", \"explore\", \"edit\", \"plan\"]}, \"taskName\": {\"type\": \"string\"}}, \"required\": [\"goal\"]}, \"description\": \"Array of batch tasks for parallel Map-Reduce spawning. When provided, spawns all tasks in one call and aggregates results automatically.\"}" +
            "  }," +
            "  \"required\": []" +
            "}";

    /**
     * 创建 spawn_subagent 工具（供 LocalToolRegistry 使用）
     */
    public static demo.k8s.agent.toolsystem.ClaudeLikeTool createTool() {
        return createSpawnSubagentToolSpec();
    }

    /**
     * 创建 spawn_subagent 工具
     */
    public static demo.k8s.agent.toolsystem.ClaudeLikeTool createSpawnSubagentTool() {
        return createSpawnSubagentToolSpec();
    }

    /**
     * 创建 spawn_subagent 工具规格（供配置类使用）
     */
    public static demo.k8s.agent.toolsystem.ClaudeLikeTool createSpawnSubagentToolSpec() {
        String detailedDescription = """
                Spawn a subagent to handle complex or parallelizable tasks.

                Required parameter:
                - goal: Clear description of what the subagent should accomplish

                Optional parameter:
                - agentType: Type of agent to spawn ("general", "worker", "bash", "explore", "edit", "plan")

                Batch spawning (optional):
                - batchTasks: Array of tasks to spawn in parallel. Each task has "goal" (required),
                  "agentType" (optional), and "taskName" (optional).
                  When batchTasks is provided, all tasks are spawned together and results are
                  aggregated automatically as a system message.

                Example (single):
                {
                  "goal": "Translate product description to Spanish",
                  "agentType": "worker"
                }

                Example (batch):
                {
                  "batchTasks": [
                    {"goal": "Translate to Spanish", "agentType": "worker"},
                    {"goal": "Translate to French", "agentType": "worker"}
                  ]
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
                    boolean hasGoal = input.has("goal") && !input.get("goal").asText("").isBlank();
                    boolean hasBatchTasks = input.has("batchTasks")
                            && input.get("batchTasks").isArray()
                            && input.get("batchTasks").size() > 0;
                    if (!hasGoal && !hasBatchTasks) {
                        return "either non-empty goal or non-empty batchTasks is required";
                    }
                    return null;
                });
    }

    /**
     * 执行 spawn_subagent 工具（支持单任务和批量派生）
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

            // 3. 获取会话上下文
            String sessionId = TraceContext.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                return spawnRejected("No active session for subagent spawn");
            }

            // 4. 检测是否为批量派生
            Object batchTasksObj = input.get("batchTasks");
            if (batchTasksObj instanceof java.util.List<?> batchList && !batchList.isEmpty()) {
                return executeBatchSpawn(sessionId, batchList);
            }

            // === 单任务派生路径 ===
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String goal = input.get("goal") != null ? mapper.convertValue(input.get("goal"), String.class) : "";
            String agentType = input.get("agentType") != null ? mapper.convertValue(input.get("agentType"), String.class) : "general";

            if (goal == null || goal.isBlank()) {
                return spawnRejected("goal is required when batchTasks is not provided");
            }

            log.info("[SpawnSubagent] Spawning single subagent: sessionId={}, goal={}, agentType={}",
                    sessionId, truncate(goal, 100), agentType);

            Set<String> allowed = spawnGatekeeper.globalSafeToolNames();
            int currentDepth = resolveCallerSubagentDepth(sessionId);

            // 使用 spawnSingle() 创建合成批次，确保完成后触发 SYSTEM 消息注入
            // mainRunId 通过 TraceContext.getRunId() 自动获取
            SpawnResult spawnResult = multiAgentFacade.getObject().spawnSingle(
                    "spawn_subagent_task",
                    goal,
                    agentType,
                    currentDepth,
                    allowed);

            if (!spawnResult.isSuccess()) {
                String msg = spawnResult.getMessage() != null ? spawnResult.getMessage() : "spawn failed";
                if (spawnResult.getMustDoNext() != null && spawnResult.getMustDoNext().suggestion() != null) {
                    msg = spawnResult.getMustDoNext().suggestion();
                }
                log.info("[SpawnSubagent] Spawn rejected: sessionId={}, goal={}, msg={}",
                        sessionId, truncate(goal, 50), msg);
                return spawnRejected(msg);
            }

            // 6. 获取子 Agent 运行状态
            String runId = spawnResult.getRunId();
            SubagentRun row = subagentRunService.getRun(runId, sessionId);
            String preview = row.getResult() != null ? row.getResult() : "";

            log.info("[SpawnSubagent] Spawn success: sessionId={}, runId={}, runStatus={}",
                    sessionId, runId, row.getStatus());

            return spawnSuccess(runId, goal, preview);

        } catch (Exception e) {
            log.error("[SpawnSubagent] Failed", e);
            return spawnRejected(e.getMessage() != null ? e.getMessage() : "spawn error");
        }
    }

    /**
     * 执行批量派生（当 input 中包含非空 batchTasks 数组时调用）
     */
    private LocalToolResult executeBatchSpawn(String sessionId, java.util.List<?> batchList) {
        log.info("[SpawnSubagent] Batch spawn requested: sessionId={}, totalTasks={}",
                sessionId, batchList.size());

        Set<String> allowed = spawnGatekeeper.globalSafeToolNames();
        int currentDepth = resolveCallerSubagentDepth(sessionId);

        // 解析 batchTasks
        java.util.List<MultiAgentFacade.BatchTask> tasks = new java.util.ArrayList<>();
        for (Object item : batchList) {
            MultiAgentFacade.BatchTask batchTask = parseBatchTaskItem(item);
            if (batchTask != null) {
                tasks.add(batchTask);
            }
        }

        if (tasks.isEmpty()) {
            return spawnRejected("batchTasks must contain at least one valid task with a non-empty goal");
        }

        // mainRunId 通过 TraceContext.getRunId() 自动获取
        String mainRunId = TraceContext.getRunId();

        MultiAgentFacade facade = multiAgentFacade.getObject();
        MultiAgentFacade.BatchSpawnResult batchResult = facade.spawnBatch(
                sessionId, mainRunId, tasks, currentDepth, allowed);

        // 构建响应
        java.util.List<String> runIds = batchResult.taskResults().stream()
                .filter(SpawnResult::isSuccess)
                .map(SpawnResult::getRunId)
                .collect(java.util.stream.Collectors.toList());
        int rejectedCount = batchResult.totalTasks() - runIds.size();
        java.util.List<String> rejectMessages = batchResult.taskResults().stream()
                .filter(r -> !r.isSuccess())
                .map(r -> r.getMessage() != null ? r.getMessage() : "spawn rejected")
                .collect(java.util.stream.Collectors.toList());

        StringBuilder content = new StringBuilder();
        content.append("Batch spawn result: batchId=").append(batchResult.batchId())
                .append(", totalTasks=").append(batchResult.totalTasks())
                .append(", started=").append(runIds.size())
                .append(", rejected=").append(rejectedCount);
        if (!runIds.isEmpty()) {
            content.append(", runIds=").append(String.join(", ", runIds));
        }
        if (!rejectMessages.isEmpty()) {
            content.append(", rejectedReasons=").append(String.join(" | ", rejectMessages));
        }
        content.append(".");
        if (!runIds.isEmpty()) {
            content.append(" Wait for system message '=== SUBAGENT BATCH COMPLETED ===' before consolidating results.");
        }

        // 构建 metadata
        java.util.Map<String, Object> output = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> batchInfo = new java.util.LinkedHashMap<>();
        batchInfo.put("batchId", batchResult.batchId());
        batchInfo.put("totalTasks", batchResult.totalTasks());
        batchInfo.put("startedTasks", runIds.size());
        batchInfo.put("rejectedTasks", rejectedCount);
        batchInfo.put("runIds", runIds);
        if (!rejectMessages.isEmpty()) {
            batchInfo.put("rejectedReasons", rejectMessages);
        }
        output.put("batch", batchInfo);

        boolean success = rejectedCount == 0;
        if (runIds.isEmpty()) {
            return LocalToolResult.builder()
                    .success(false)
                    .error("all batch tasks were rejected")
                    .content(content.toString())
                    .executionLocation("local")
                    .metadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(output))
                    .build();
        }

        return LocalToolResult.builder()
                .success(success)
                .error(success ? null : "some batch tasks were rejected")
                .content(content.toString())
                .executionLocation("local")
                .metadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(output))
                .build();
    }

    /**
     * 解析 batchTasks 数组中的单个任务项
     */
    private MultiAgentFacade.BatchTask parseBatchTaskItem(Object item) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<?, ?> map;
        try {
            if (item instanceof String str) {
                return new MultiAgentFacade.BatchTask(null, str, "general");
            }
            map = mapper.convertValue(item, java.util.Map.class);
        } catch (Exception e) {
            log.warn("[SpawnSubagent] Failed to parse batch task item: {}", item);
            return null;
        }

        String goal = map.get("goal") != null ? String.valueOf(map.get("goal")) : null;
        if (goal == null || goal.isBlank()) {
            log.warn("[SpawnSubagent] Batch task item missing goal, skipping: {}", item);
            return null;
        }

        String agentType = map.get("agentType") != null ? String.valueOf(map.get("agentType")) : "general";
        String taskName = map.get("taskName") != null ? String.valueOf(map.get("taskName")) : null;

        return new MultiAgentFacade.BatchTask(taskName, goal, agentType);
    }

    /**
     * 主会话调用时无对应 {@code subagent_run}，深度为 0；Worker 线程内 {@link TraceContext#getRunId()} 绑定当前子运行，
     * 用其 DB 中的 {@link SubagentRun#getDepth()} 作为门控 {@code currentDepth}，使嵌套 spawn 受 {@code max-spawn-depth} 约束。
     */
    /**
     * 供 {@link demo.k8s.agent.tools.local.LocalToolExecutor} 等在派生前计算门控深度。
     */
    public int resolveCallerSubagentDepth(String sessionId) {
        String callerRunId = TraceContext.getRunId();
        if (callerRunId == null || callerRunId.isBlank()) {
            return 0;
        }
        try {
            SubagentRun self = subagentRunService.getRun(callerRunId, sessionId);
            return self.getDepth();
        } catch (Exception e) {
            log.debug("[SpawnSubagent] No subagent_run for runId={}, sessionId={} — using depth 0: {}",
                    callerRunId, sessionId, e.getMessage());
            return 0;
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
