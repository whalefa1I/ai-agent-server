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
     *
     * ### 核心定位
     * - **目的**：仅用于**提升并行执行效率**，非隔离语义
     * - **启用条件**：3+ 个独立子任务
     * - **调用方式**：必须使用 `batchTasks` 参数（禁止多次单独调用，禁止单步 task 使用）
     */
    public static final String SPAWN_SUBAGENT_PROMPT = """
            ### 🎯 spawn_subagent 工具使用规则（v2 - 并行效率优先）

            **工具定位**：
            - 本工具**唯一目的**是提升并行执行效率，加快多任务处理速度
            - **不是**为了任务隔离或探索，**不要**为了"委派"而使用

            **启用条件（必须同时满足）**：
            1. 任务数量 >= 3 个
            2. 所有任务相互独立，无依赖关系
            3. 任务可并行执行（如：翻译到多种语言、处理多个独立文件）

            **调用方式（强制）**：
            - ✅ **必须**使用 `batchTasks` 参数，一次调用派生所有任务
            - ❌ **禁止**多次单独调用 spawn_subagent（如 5 个任务调用 5 次）
            - ❌ **禁止**对单步任务使用（<3 个任务时请直接用本地工具处理）

            **示例**：

            ✅ 正确（5 个翻译任务）：
            ```json
            {
              "batchTasks": [
                {"goal": "Translate to Spanish: [原文]", "agentType": "worker", "taskName": "Spanish"},
                {"goal": "Translate to French: [原文]", "agentType": "worker", "taskName": "French"},
                {"goal": "Translate to German: [原文]", "agentType": "worker", "taskName": "German"},
                {"goal": "Translate to Japanese: [原文]", "agentType": "worker", "taskName": "Japanese"},
                {"goal": "Translate to Korean: [原文]", "agentType": "worker", "taskName": "Korean"}
              ]
            }
            ```

            ❌ 错误 - 单步任务使用 spawn：
            ```json
            {"goal": "Read the config file", "agentType": "worker"}
            ```
            原因：单步任务直接使用 file_read 即可，spawn 会增加不必要的开销

            ❌ 错误 - 多次单独调用：
            调用 5 次 spawn_subagent，每次一个 goal
            原因：应使用 1 次 batchTasks 包含所有任务

            **等待完成**：
            调用 batchTasks 后，等待系统消息 "=== SUBAGENT BATCH COMPLETED ===" 再汇总结果
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
            Spawn subagents for **parallel execution efficiency only**.

            **When to use (must satisfy all):**
            - 3+ independent tasks that can run in parallel
            - Tasks have no dependencies on each other
            - Examples: translate to multiple languages, process multiple independent files

            **When NOT to use:**
            - Single task (<3 tasks): use local tools directly (avoid spawn overhead)
            - Sequential tasks with dependencies
            - Do NOT call multiple times - use batchTasks instead

            **Parameters:**
            - goal: Single task goal (NOT RECOMMENDED - use batchTasks for 3+ tasks)
            - agentType: Agent type ("general", "worker", "bash", "explore", "edit", "plan")
            - batchTasks: Array of tasks for parallel spawn (REQUIRED for 3+ tasks)
              - Each: {"goal": "...", "agentType": "worker" (optional), "taskName": "..." (optional)}

            **Example (correct for multi-task):**
            {"batchTasks": [
              {"goal": "Translate to Spanish: [text]", "agentType": "worker", "taskName": "Spanish"},
              {"goal": "Translate to French: [text]", "agentType": "worker", "taskName": "French"}
            ]}

            After batch spawn, wait for "=== SUBAGENT BATCH COMPLETED ===" system message.
            """;

        return demo.k8s.agent.toolsystem.ClaudeToolFactory.buildTool(
                new demo.k8s.agent.toolsystem.ToolDefPartial(
                        "spawn_subagent",
                        demo.k8s.agent.toolsystem.ToolCategory.PLANNING,
                        detailedDescription,
                        SPAWN_SUBAGENT_INPUT_SCHEMA,
                        null,
                        false,
                        false), // spawn_subagent 不是并发安全的，因为需要管理子 agent 生命周期
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

            // === 单任务派生路径（兼容模式，但记录告警）===
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String goal = input.get("goal") != null ? mapper.convertValue(input.get("goal"), String.class) : "";
            String agentType = input.get("agentType") != null ? mapper.convertValue(input.get("agentType"), String.class) : "general";

            if (goal == null || goal.isBlank()) {
                return spawnRejected("goal is required when batchTasks is not provided");
            }

            // 告警：单步 spawn 不推荐使用，增加不必要的开销
            log.warn("[SpawnSubagent] SINGLE SPAWN DETECTED (not recommended): sessionId={}, goal={}. " +
                    "spawn_subagent should only be used for 3+ parallel tasks via batchTasks. " +
                    "For single tasks, use local tools directly to avoid spawn overhead.",
                    sessionId, truncate(goal, 100));

            log.info("[SpawnSubagent] Spawning single subagent (compat mode): sessionId={}, goal={}, agentType={}",
                    sessionId, truncate(goal, 100), agentType);

            Set<String> allowed = spawnGatekeeper.globalSafeToolNames();
            int currentDepth = resolveCallerSubagentDepth(sessionId);

            // 使用 spawnSingle() 创建合成批次（batch-of-1），确保完成后触发 SYSTEM 消息注入
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
