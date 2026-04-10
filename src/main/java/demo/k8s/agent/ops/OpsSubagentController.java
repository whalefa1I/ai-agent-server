package demo.k8s.agent.ops;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.MultiAgentFacade;
import demo.k8s.agent.subagent.SpawnGatekeeper;
import demo.k8s.agent.subagent.SpawnResult;
import demo.k8s.agent.subagent.SubRunEvent;
import demo.k8s.agent.subagent.SubagentBatchService;
import demo.k8s.agent.subagent.SubagentResultStorage;
import demo.k8s.agent.subagent.SubagentRun;
import demo.k8s.agent.subagent.SubagentRunService;
import demo.k8s.agent.tools.local.planning.SpawnSubagentTool;
import demo.k8s.agent.toolsystem.ToolRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 子 Agent 运维验收 API：配置快照、门控只读探测、DB 运行列表、可选直连 {@link MultiAgentFacade} 探活。
 * <p>
 * 鉴权：{@code demo.ops.enabled=true} 且配置 {@code demo.ops.secret}；请求头 {@code X-Ops-Secret}。
 */
@RestController
@RequestMapping("/api/ops/subagent")
@CrossOrigin(origins = "*")
public class OpsSubagentController {

    private static final Logger log = LoggerFactory.getLogger(OpsSubagentController.class);

    private static final String DEFAULT_PROBE_GOAL =
            "Ops probe: respond with the single word OK only. Do not call any tools.";

    private final OpsApiAuthorizer authorizer;
    private final DemoMultiAgentProperties multiAgentProperties;
    private final ObjectProvider<MultiAgentFacade> facadeProvider;
    private final ObjectProvider<SpawnSubagentTool> spawnSubagentToolProvider;
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;
    private final SpawnGatekeeper gatekeeper;
    private final SubagentRunService subagentRunService;
    private final SubagentBatchService subagentBatchService;
    private final SubagentResultStorage resultStorage;

    public OpsSubagentController(
            OpsApiAuthorizer authorizer,
            DemoMultiAgentProperties multiAgentProperties,
            ObjectProvider<MultiAgentFacade> facadeProvider,
            ObjectProvider<SpawnSubagentTool> spawnSubagentToolProvider,
            ObjectProvider<ToolRegistry> toolRegistryProvider,
            SpawnGatekeeper gatekeeper,
            SubagentRunService subagentRunService,
            SubagentBatchService subagentBatchService,
            SubagentResultStorage resultStorage) {
        this.authorizer = authorizer;
        this.multiAgentProperties = multiAgentProperties;
        this.facadeProvider = facadeProvider;
        this.spawnSubagentToolProvider = spawnSubagentToolProvider;
        this.toolRegistryProvider = toolRegistryProvider;
        this.gatekeeper = gatekeeper;
        this.subagentRunService = subagentRunService;
        this.subagentBatchService = subagentBatchService;
        this.resultStorage = resultStorage;
    }

    /**
     * 聚合摘要：有效配置、Bean 是否就绪、注册表中是否包含 spawn_subagent。
     */
    @GetMapping("/summary")
    public ResponseEntity<?> summary(HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }
        MultiAgentFacade facade = facadeProvider.getIfAvailable();
        ToolRegistry registry = toolRegistryProvider.getIfAvailable();
        Set<String> names = registry != null ? registry.getAllToolNames() : Set.of();
        boolean hasSpawnTool = names.contains("spawn_subagent");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("multiAgent", Map.of(
                "enabled", multiAgentProperties.isEnabled(),
                "mode", multiAgentProperties.getMode().name(),
                "maxSpawnDepth", multiAgentProperties.getMaxSpawnDepth(),
                "maxConcurrentSpawns", multiAgentProperties.getMaxConcurrentSpawns(),
                "wallclockTtlSeconds", multiAgentProperties.getWallclockTtlSeconds(),
                "workerExposeTaskTools", multiAgentProperties.isWorkerExposeTaskTools(),
                "workerExposeSpawnSubagent", multiAgentProperties.isWorkerExposeSpawnSubagent(),
                "workerMaxTurns", multiAgentProperties.getWorkerMaxTurns(),
                "defaultTenantId", multiAgentProperties.getDefaultTenantId()
        ));
        body.put("beans", Map.of(
                "multiAgentFacadePresent", facade != null,
                "spawnSubagentToolPresent", spawnSubagentToolProvider.getIfAvailable() != null,
                "toolRegistryPresent", registry != null,
                "registeredToolCount", names.size(),
                "spawnSubagentInRegistry", hasSpawnTool
        ));
        body.put("facadeOutcomeHint", predictFacadeBranch());
        return ResponseEntity.ok(body);
    }

    /**
     * 门控只读探测 + 内存计数（不占并发槽）。
     */
    @GetMapping("/gatekeeper")
    public ResponseEntity<?> gatekeeper(
            HttpServletRequest request,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "0") int currentDepth) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }
        Set<String> allowed = gatekeeper.globalSafeToolNames();
        SpawnResult.MustDoNext peek = gatekeeper.peekSpawnAllowed(sessionId, currentDepth, allowed);
        SpawnGatekeeper.SessionStats mem = gatekeeper.getSessionStats(sessionId);
        int dbActive = subagentRunService.countActiveRuns(sessionId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("currentDepth", currentDepth);
        body.put("memoryGatekeeper", Map.of("depth", mem.depth(), "concurrent", mem.concurrent()));
        body.put("dbActiveRunsApprox", dbActive);
        body.put("allowedToolsSampleSize", allowed.size());
        body.put("peekWouldPass", peek == null);
        if (peek != null) {
            body.put("peekRejection", Map.of(
                    "action", peek.action().name(),
                    "reason", peek.reason(),
                    "suggestion", peek.suggestion()
            ));
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 最近子运行记录（当前会话），便于与日志 / 前端对照。
     */
    @GetMapping("/runs")
    public ResponseEntity<?> runs(HttpServletRequest request, @RequestParam String sessionId) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }
        List<Map<String, Object>> rows = subagentRunService.listRecentBySession(sessionId).stream()
                .map(this::toRunSummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "runs", rows, "count", rows.size()));
    }

    /**
     * 单条运行详情（需 sessionId 防止跨会话枚举）。
     */
    @GetMapping("/run/{runId}")
    public ResponseEntity<?> runDetail(
            HttpServletRequest request,
            @PathVariable String runId,
            @RequestParam String sessionId) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }
        try {
            SubagentRun row = subagentRunService.getRun(runId, sessionId);
            return ResponseEntity.ok(toRunDetail(row));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    /**
     * 运行时状态（若 {@link MultiAgentFacade#getStatus} 可用）。
     * 需传 {@code sessionId}：本地运行时按会话校验 run 归属。
     */
    @GetMapping("/runtime-status/{runId}")
    public ResponseEntity<?> runtimeStatus(
            HttpServletRequest request,
            @PathVariable String runId,
            @RequestParam String sessionId) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }
        MultiAgentFacade facade = facadeProvider.getIfAvailable();
        if (facade == null) {
            return ResponseEntity.status(503).body(Map.of("error", true, "message", "MultiAgentFacade not available"));
        }
        TraceContext.clear();
        try {
            TraceContext.init(TraceContext.generateTraceId(), TraceContext.generateSpanId(),
                    multiAgentProperties.getDefaultTenantId(), "ops-status", sessionId, "ops-probe");
            SubRunEvent ev = facade.getStatus(runId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("runId", runId);
            body.put("sessionId", sessionId);
            body.put("event", ev == null ? null : eventToMap(ev));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("runId", runId, "sessionId", sessionId, "error", e.getMessage()));
        } finally {
            TraceContext.clear();
        }
    }

    private static Map<String, Object> eventToMap(SubRunEvent ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", ev.getType() != null ? ev.getType().name() : null);
        m.put("timestamp", ev.getTimestamp() != null ? ev.getTimestamp().toString() : null);
        m.put("content", ev.getContent());
        m.put("toolName", ev.getToolName());
        m.put("toolArgs", ev.getToolArgs());
        return m;
    }

    /**
     * 对会话做 reconcile 摘要（可能将超时运行标记为 TIMEOUT）。
     */
    @PostMapping("/reconcile")
    public ResponseEntity<?> reconcile(HttpServletRequest request, @RequestParam String sessionId) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }
        SubagentRunService.ReconcileSummary s = subagentRunService.reconcileSession(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", s.sessionId(),
                "total", s.total(),
                "timeoutCount", s.timeoutCount(),
                "preserveCount", s.preserveCount()
        ));
    }

    /**
     * 直连门面探测：{@code execute=false} 时仅返回分支预测与门控只读结果；{@code execute=true} 时真实 spawn（消耗模型配额，shadow 仍会占用门控槽位直至运行时结束，与线上一致）。
     */
    @PostMapping("/probe/spawn")
    public ResponseEntity<?> probeSpawn(HttpServletRequest request, @RequestBody(required = false) ProbeSpawnBody body) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }
        ProbeSpawnBody b = body != null ? body : new ProbeSpawnBody();
        log.info("[OpsSubagentController] probe-spawn request: sessionId={}, execute={}, agentType={}",
                b.sessionId, b.execute, b.agentType);
        String sessionId = b.sessionId != null && !b.sessionId.isBlank() ? b.sessionId : "ops-probe-session";
        String tenantId = b.tenantId != null && !b.tenantId.isBlank() ? b.tenantId : multiAgentProperties.getDefaultTenantId();
        String appId = b.appId != null && !b.appId.isBlank() ? b.appId : "ops-probe";
        String goal = b.goal != null && !b.goal.isBlank() ? b.goal : DEFAULT_PROBE_GOAL;
        String agentType = b.agentType != null && !b.agentType.isBlank() ? b.agentType : "worker";
        boolean execute = Boolean.TRUE.equals(b.execute);

        Set<String> allowed = gatekeeper.globalSafeToolNames();
        SpawnResult.MustDoNext peek = gatekeeper.peekSpawnAllowed(sessionId, 0, allowed);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId);
        out.put("execute", execute);
        out.put("facadeBranch", predictFacadeBranch());
        out.put("peekWouldPass", peek == null);
        if (peek != null) {
            out.put("peekRejection", Map.of(
                    "action", peek.action().name(),
                    "reason", peek.reason(),
                    "suggestion", peek.suggestion()
            ));
        }

        if (!execute) {
            out.put("hint", "Set execute=true to run MultiAgentFacade.spawnTask (costs tokens if mode=on).");
            return ResponseEntity.ok(out);
        }

        MultiAgentFacade facade = facadeProvider.getIfAvailable();
        if (facade == null) {
            out.put("spawn", Map.of("success", false, "message", "MultiAgentFacade not available"));
            return ResponseEntity.ok(out);
        }

        TraceContext.clear();
        try {
            TraceContext.init(TraceContext.generateTraceId(), TraceContext.generateSpanId(), tenantId, appId, sessionId, "ops-probe");
            TraceContext.setRequestId("ops-probe-" + System.currentTimeMillis());

            SpawnResult result = facade.spawnTask("ops_probe", goal, agentType, 0, allowed);
            out.put("spawn", spawnResultToMap(result));

            if (result.isSuccess() && result.getRunId() != null) {
                try {
                    SubagentRun row = subagentRunService.getRun(result.getRunId(), sessionId);
                    out.put("dbRecord", toRunSummary(row));
                } catch (Exception ex) {
                    out.put("dbRecordHint", "Run created but not yet readable: " + ex.getMessage());
                }
            }
            log.info("[OpsProbe] spawn probe finished: success={}, runId={}", result.isSuccess(), result.getRunId());
            return ResponseEntity.ok(out);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 批量派生子 Agent（运维路径）。
     * <p>
     * 运维路径：跳过用户级归属校验，但记录审计日志。
     */
    @PostMapping("/batch-spawn")
    public ResponseEntity<?> batchSpawn(HttpServletRequest request,
                                         @RequestBody(required = false) OpsBatchSpawnBody body) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }

        OpsBatchSpawnBody b = body != null ? body : new OpsBatchSpawnBody();
        log.info("[OpsSubagentController] batch-spawn request: sessionId={}, taskCount={}, mainRunId={}",
                b.sessionId, b.tasks != null ? b.tasks.size() : 0, b.mainRunId);
        String sessionId = b.sessionId != null && !b.sessionId.isBlank() ? b.sessionId : "ops-batch-session";
        String mainRunId = b.mainRunId;
        if (b.tasks == null || b.tasks.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "tasks is required and must not be empty",
                    "errorCode", "BAD_REQUEST"
            ));
        }

        // 构建调用上下文（运维路径）
        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS", // principal
                sessionId,
                "ops_batch_spawn" // auditReason
        );

        // 获取允许的工具列表
        Set<String> allowed = gatekeeper.globalSafeToolNames();

        // 转换任务请求
        List<SubagentBatchService.BatchTaskRequest> tasks = b.tasks.stream()
                .map(t -> new SubagentBatchService.BatchTaskRequest(t.taskName, t.goal, t.agentType))
                .toList();

        SubagentBatchService.BatchSpawnResponse response = subagentBatchService.spawnBatch(
                ctx, sessionId, mainRunId, tasks, 0, allowed
        );

        if (response.success()) {
            return ResponseEntity.ok(response);
        } else {
            log.warn("[OpsSubagentController] batch-spawn rejected: sessionId={}, errorCode={}, error={}",
                    sessionId, response.errorCode(), response.error());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", response.error(),
                    "errorCode", response.errorCode()
            ));
        }
    }

    /**
     * 批次取消（运维路径）。
     */
    @PostMapping("/batch/cancel")
    public ResponseEntity<?> batchCancel(HttpServletRequest request,
                                          @RequestBody(required = false) OpsBatchCancelBody body) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }

        OpsBatchCancelBody b = body != null ? body : new OpsBatchCancelBody();
        log.info("[OpsSubagentController] batch-cancel request: sessionId={}, batchId={}", b.sessionId, b.batchId);
        String sessionId = b.sessionId != null && !b.sessionId.isBlank() ? b.sessionId : "ops-batch-session";
        String batchId = b.batchId;

        if (batchId == null || batchId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "batchId is required"));
        }

        // 构建调用上下文（运维路径）
        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS",
                sessionId,
                b.reason != null ? b.reason : "ops_cancel"
        );

        SubagentBatchService.BatchCancelResponse response = subagentBatchService.cancelBatch(
                ctx, sessionId, batchId, b.reason != null ? b.reason : "ops_cancel"
        );

        if (response.success()) {
            return ResponseEntity.ok(response);
        } else if ("BATCH_ALREADY_TERMINAL".equals(response.error())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", response.error(),
                    "batchId", batchId,
                    "status", response.status()
            ));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "error", response.error(),
                    "batchId", batchId
            ));
        }
    }

    /**
     * 批次状态查询（运维路径）。
     */
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<?> batchQuery(HttpServletRequest request,
                                         @PathVariable String batchId,
                                         @RequestParam String sessionId) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }

        // 构建调用上下文（运维路径）
        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                "OPS",
                sessionId,
                "ops_batch_query"
        );

        SubagentBatchService.BatchQueryResponse response = subagentBatchService.queryBatch(ctx, sessionId, batchId);

        if (response.success()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "error", response.error(),
                    "batchId", batchId
            ));
        }
    }

    /**
     * 获取批次结果详情（运维路径）。
     * <p>
     * 返回所有任务的结果，包含 runId, status, summary, resultPath, 和完整 result 内容。
     */
    @GetMapping("/batch/{batchId}/results")
    public ResponseEntity<?> batchResults(HttpServletRequest request,
                                          @PathVariable String batchId,
                                          @RequestParam String sessionId) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }

        // 从 DB 加载批次下的所有子运行
        List<SubagentRun> runs = subagentRunService.findByBatchId(batchId, sessionId);
        if (runs.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Batch not found or sessionId mismatch",
                    "batchId", batchId
            ));
        }

        // 构建结果列表
        List<Map<String, Object>> results = new ArrayList<>();
        for (SubagentRun run : runs) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runId", run.getRunId());
            result.put("status", run.getStatus() != null ? run.getStatus().name() : null);
            result.put("goal", truncate(run.getGoal(), 200));

            // 从 SubagentResultStorage 读取结果路径和内容
            String resultPath = run.getBatchId() != null
                    ? resultStorage.getResultPath(run.getBatchId(), run.getRunId())
                    : null;
            result.put("resultPath", resultPath);

            // 读取完整结果内容
            String fullResult = run.getResult() != null ? run.getResult() : run.getErrorMessage();
            result.put("result", fullResult);

            // 生成摘要
            result.put("summary", resultStorage.summarize(fullResult, 100));

            result.put("createdAt", run.getCreatedAt() != null ? run.getCreatedAt().toString() : null);
            result.put("endedAt", run.getEndedAt() != null ? run.getEndedAt().toString() : null);

            results.add(result);
        }

        // 计算批次统计
        int total = runs.size();
        int completed = (int) runs.stream().filter(r -> r.getStatus() == SubagentRun.RunStatus.COMPLETED).count();
        int failed = (int) runs.stream().filter(r ->
                r.getStatus() == SubagentRun.RunStatus.FAILED ||
                r.getStatus() == SubagentRun.RunStatus.TIMEOUT ||
                r.getStatus() == SubagentRun.RunStatus.CANCELLED).count();
        int pending = total - completed - failed;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("batchId", batchId);
        response.put("sessionId", sessionId);
        response.put("totalTasks", total);
        response.put("completed", completed);
        response.put("failed", failed);
        response.put("pending", pending);
        response.put("results", results);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有活跃批次（运维路径）。
     */
    @GetMapping("/batches")
    public ResponseEntity<?> batches(HttpServletRequest request,
                                      @RequestParam(required = false) String sessionId) {
        ResponseEntity<Map<String, Object>> deny = authorizer.preflight(request);
        if (deny != null) {
            return deny;
        }

        java.util.Set<String> batchIds;
        if (sessionId != null && !sessionId.isBlank()) {
            batchIds = subagentRunService.findActiveBatchIdsBySession(sessionId);
        } else {
            // 获取所有会话的活跃批次（简化实现：返回空列表，实际可从内存注册表获取）
            batchIds = java.util.Collections.emptySet();
        }

        List<Map<String, Object>> batches = batchIds.stream()
                .map(batchId -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("batchId", batchId);
                    b.put("sessionId", sessionId);
                    return b;
                })
                .toList();

        return ResponseEntity.ok(Map.of("batches", batches, "count", batches.size()));
    }

    private String predictFacadeBranch() {
        if (!multiAgentProperties.isEnabled() || multiAgentProperties.getMode() == DemoMultiAgentProperties.Mode.off) {
            return "disabled";
        }
        if (multiAgentProperties.getMode() == DemoMultiAgentProperties.Mode.shadow) {
            return "shadow_eval_only";
        }
        return "on_will_execute_worker";
    }

    private static Map<String, Object> spawnResultToMap(SpawnResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", r.isSuccess());
        m.put("runId", r.getRunId());
        m.put("message", r.getMessage());
        SpawnResult.MustDoNext n = r.getMustDoNext();
        if (n != null) {
            m.put("mustDoNext", Map.of(
                    "action", n.action().name(),
                    "reason", n.reason(),
                    "suggestion", n.suggestion()
            ));
        }
        return m;
    }

    private Map<String, Object> toRunSummary(SubagentRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.getRunId());
        m.put("parentRunId", r.getParentRunId());
        m.put("sessionId", r.getSessionId());
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        m.put("goal", truncate(r.getGoal(), 200));
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("updatedAt", r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
        m.put("endedAt", r.getEndedAt() != null ? r.getEndedAt().toString() : null);
        m.put("deadlineAt", r.getDeadlineAt() != null ? r.getDeadlineAt().toString() : null);
        m.put("batchId", r.getBatchId());
        return m;
    }

    private Map<String, Object> toRunDetail(SubagentRun r) {
        Map<String, Object> m = new LinkedHashMap<>(toRunSummary(r));
        m.put("goalFull", r.getGoal());
        m.put("result", r.getResult() != null ? truncate(r.getResult(), 4000) : null);
        m.put("errorMessage", r.getErrorMessage());
        m.put("allowedTools", r.getAllowedTools());
        m.put("depth", r.getDepth());
        m.put("tokenBudget", r.getTokenBudget());
        m.put("retryCount", r.getRetryCount());
        m.put("batchId", r.getBatchId());
        m.put("batchTotal", r.getBatchTotal());
        m.put("batchIndex", r.getBatchIndex());
        m.put("mainRunId", r.getMainRunId());
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @SuppressWarnings("unused")
    public static class ProbeSpawnBody {
        public String sessionId;
        public String tenantId;
        public String appId;
        public String goal;
        public String agentType;
        public Boolean execute;
    }

    /**
     * 运维批量派生请求
     */
    @SuppressWarnings("unused")
    public static class OpsBatchSpawnBody {
        public String sessionId;
        public String mainRunId;
        public List<OpsBatchTask> tasks;
    }

    /**
     * 运维批次任务请求
     */
    @SuppressWarnings("unused")
    public static class OpsBatchTask {
        public String taskName;
        public String goal;
        public String agentType = "worker";
    }

    /**
     * 运维批次取消请求
     */
    @SuppressWarnings("unused")
    public static class OpsBatchCancelBody {
        public String sessionId;
        public String batchId;
        public String reason;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleBadJson(HttpMessageNotReadableException ex) {
        log.error("[OpsSubagentController] JSON parse failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", true,
                "message", "Invalid JSON request body",
                "detail", ex.getMostSpecificCause() != null
                        ? ex.getMostSpecificCause().getMessage()
                        : ex.getMessage()
        ));
    }
}
