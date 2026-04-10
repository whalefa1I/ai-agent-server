package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.web.SubagentSseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子 Agent 批次服务门面（单一业务入口）。
 * <p>
 * 产品路径 /api/v2/subagent/* 和运维路径 /api/ops/subagent/* 均委托于此。
 * 支持批次派生、级联取消、批次查询。
 */
@Service
public class SubagentBatchService {

    private static final Logger log = LoggerFactory.getLogger(SubagentBatchService.class);

    private final MultiAgentFacade multiAgentFacade;
    private final SubagentRunService runService;
    private final BatchCompletionListener batchCompletionListener;
    private final SubagentSseController sseController;
    private final DemoMultiAgentProperties properties;

    /**
     * 批次状态缓存（内存注册表）
     */
    private final Map<String, BatchStatus> batchStatusRegistry = new ConcurrentHashMap<>();

    public SubagentBatchService(MultiAgentFacade multiAgentFacade,
                                 SubagentRunService runService,
                                 BatchCompletionListener batchCompletionListener,
                                 SubagentSseController sseController,
                                 DemoMultiAgentProperties properties) {
        this.multiAgentFacade = multiAgentFacade;
        this.runService = runService;
        this.batchCompletionListener = batchCompletionListener;
        this.sseController = sseController;
        this.properties = properties;
        log.info("[SubagentBatchService] Initialized");
    }

    /**
     * 批量派生子 Agent（门面方法）。
     *
     * @param ctx          调用上下文（用于区分产品/运维路径）
     * @param sessionId    会话 ID
     * @param mainRunId    主 Agent 运行 ID
     * @param tasks        任务列表
     * @param currentDepth 当前深度
     * @param allowedTools 允许的工具
     * @return 批次派生结果
     */
    public BatchSpawnResponse spawnBatch(InvocationContext ctx, String sessionId, String mainRunId,
                                          List<BatchTaskRequest> tasks, int currentDepth,
                                          Set<String> allowedTools) {
        log.info("[SubagentBatchService] Spawning batch: sessionId={}, mainRunId={}, totalTasks={}",
                sessionId, mainRunId, tasks.size());

        // 检查并发限制
        int maxConcurrent = properties.getMaxConcurrentSpawns();
        if (tasks.size() > maxConcurrent) {
            return BatchSpawnResponse.rejected(
                    "Task count exceeds max concurrent spawns (" + maxConcurrent + ")",
                    "SPAWN_REJECTED_QUOTA");
        }

        try {
            // 设置 TraceContext（确保批次元数据与会话一致）
            String previousSession = TraceContext.getSessionId();
            TraceContext.setSessionId(sessionId);

            try {
                // 创建批次上下文
                BatchContext batchContext = batchCompletionListener.createBatch(sessionId, mainRunId, tasks.size());
                String batchId = batchContext.getBatchId();

                // 注册批次状态
                BatchStatus batchStatus = new BatchStatus(batchId, sessionId, tasks.size());
                batchStatusRegistry.put(batchId, batchStatus);

                // 派发所有子任务
                List<TaskSpawnResult> taskResults = new ArrayList<>();
                int rejectedCount = 0;
                int index = 0;
                for (BatchTaskRequest task : tasks) {
                    SpawnResult result = multiAgentFacade.spawnTask(
                            task.taskName,
                            task.goal,
                            task.agentType,
                            currentDepth,
                            allowedTools,
                            batchId,
                            tasks.size(),
                            index++,
                            mainRunId
                    );

                    TaskSpawnResult taskResult;
                    if (result.isSuccess()) {
                        taskResult = new TaskSpawnResult(
                                result.getRunId(),
                                "accepted",
                                null
                        );
                        batchStatus.addRunId(result.getRunId());
                        log.info("[SubagentBatchService] Task spawned: batchId={}, runId={}, goal={}",
                                batchId, result.getRunId(), task.goal);
                    } else {
                        rejectedCount++;
                        taskResult = new TaskSpawnResult(
                                null,
                                "rejected",
                                result.getMessage()
                        );
                        log.warn("[SubagentBatchService] Task spawn rejected: batchId={}, goal={}, reason={}",
                                batchId, task.goal, result.getMessage());
                    }
                    taskResults.add(taskResult);

                    // 发布 SSE 事件
                    if (result.isSuccess()) {
                        sseController.publishStatusEvent(result.getRunId(), sessionId, "accepted", null, null);
                    }
                }

                if (rejectedCount > 0) {
                    String errorCode = rejectedCount == tasks.size()
                            ? "SPAWN_ALL_REJECTED"
                            : "SPAWN_PARTIAL_REJECTED";
                    return BatchSpawnResponse.partialFailure(
                            batchId, sessionId, tasks.size(), taskResults, rejectedCount, errorCode);
                }

                return BatchSpawnResponse.success(batchId, sessionId, tasks.size(), taskResults);

            } finally {
                TraceContext.setSessionId(previousSession);
            }

        } catch (Exception e) {
            log.error("[SubagentBatchService] Batch spawn failed", e);
            return BatchSpawnResponse.error("Batch spawn failed: " + e.getMessage());
        }
    }

    /**
     * 级联取消批次（门面方法）。
     *
     * @param ctx       调用上下文
     * @param sessionId 会话 ID
     * @param batchId   批次 ID
     * @param reason    取消原因
     * @return 取消结果
     */
    @Transactional
    public BatchCancelResponse cancelBatch(InvocationContext ctx, String sessionId, String batchId, String reason) {
        log.info("[SubagentBatchService] Cancelling batch: sessionId={}, batchId={}, reason={}",
                sessionId, batchId, reason);

        // 从 DB 加载批次下的所有子运行
        List<SubagentRun> runs = runService.findByBatchId(batchId, sessionId);
        if (runs.isEmpty()) {
            return BatchCancelResponse.notFound(batchId, "Batch not found or sessionId mismatch");
        }

        // 检查批次是否已终态
        SubagentRun.RunStatus batchStatus = getBatchStatus(runs);
        if (isTerminalStatus(batchStatus)) {
            return BatchCancelResponse.alreadyTerminal(batchId, batchStatus.name());
        }

        // 级联取消所有子任务
        List<String> cancelledRunIds = new ArrayList<>();
        for (SubagentRun run : runs) {
            if (!isTerminalStatus(run.getStatus())) {
                boolean cancelled = multiAgentFacade.cancel(run.getRunId());
                if (cancelled) {
                    cancelledRunIds.add(run.getRunId());
                    // 发布 SSE 取消事件
                    sseController.publishStatusEvent(run.getRunId(), sessionId, "CANCELLED", null, reason);
                }
            }
        }

        // 更新批次状态注册表
        BatchStatus status = batchStatusRegistry.get(batchId);
        if (status != null) {
            status.markCancelled(reason);
        }

        log.info("[SubagentBatchService] Batch cancelled: batchId={}, cancelledRunIds={}",
                batchId, cancelledRunIds);

        return BatchCancelResponse.success(batchId, sessionId, "CANCEL_ACCEPTED", cancelledRunIds, reason);
    }

    /**
     * 查询批次状态（门面方法）。
     *
     * @param ctx       调用上下文
     * @param sessionId 会话 ID
     * @param batchId   批次 ID
     * @return 批次状态
     */
    public BatchQueryResponse queryBatch(InvocationContext ctx, String sessionId, String batchId) {
        // 从 DB 加载批次下的所有子运行
        List<SubagentRun> runs = runService.findByBatchId(batchId, sessionId);
        if (runs.isEmpty()) {
            return BatchQueryResponse.notFound(batchId, "Batch not found or sessionId mismatch");
        }

        // 计算批次状态
        int completed = 0;
        int pending = 0;
        int failed = 0;
        List<TaskStatus> taskStatuses = new ArrayList<>();

        for (SubagentRun run : runs) {
            TaskStatus taskStatus = new TaskStatus(
                    run.getRunId(),
                    run.getStatus().name(),
                    run.getResult(),
                    run.getErrorMessage()
            );
            taskStatuses.add(taskStatus);

            switch (run.getStatus()) {
                case COMPLETED:
                    completed++;
                    break;
                case FAILED:
                case TIMEOUT:
                case REJECTED:
                case CANCELLED:
                    failed++;
                    break;
                default:
                    pending++;
            }
        }

        String batchStatus = computeBatchStatus(runs);
        int total = runs.size();

        // 从注册表获取创建时间
        BatchStatus status = batchStatusRegistry.get(batchId);
        String createdAt = status != null ? status.getCreatedAt().toString() : null;

        return BatchQueryResponse.success(
                batchId, sessionId, total, completed, pending, failed,
                batchStatus, taskStatuses, createdAt);
    }

    /**
     * 计算批次整体状态
     */
    private String computeBatchStatus(List<SubagentRun> runs) {
        if (runs.isEmpty()) {
            return "UNKNOWN";
        }

        int completed = 0;
        int failed = 0;
        int running = 0;

        for (SubagentRun run : runs) {
            switch (run.getStatus()) {
                case COMPLETED:
                    completed++;
                    break;
                case FAILED:
                case TIMEOUT:
                case REJECTED:
                case CANCELLED:
                    failed++;
                    break;
                default:
                    running++;
            }
        }

        if (running > 0) {
            return "IN_PROGRESS";
        }
        if (failed == runs.size()) {
            return "FAILED";
        }
        if (completed == runs.size()) {
            return "COMPLETED";
        }
        return "PARTIAL";
    }

    /**
     * 批次是否已全部终态；若仍有 RUNNING/PENDING 等则返回 {@link SubagentRun.RunStatus#RUNNING}。
     */
    private SubagentRun.RunStatus getBatchStatus(List<SubagentRun> runs) {
        if (runs.isEmpty()) {
            return null;
        }
        boolean anyNonTerminal = runs.stream().anyMatch(r -> !isTerminalStatus(r.getStatus()));
        if (anyNonTerminal) {
            return SubagentRun.RunStatus.RUNNING;
        }
        boolean allCompleted = runs.stream().allMatch(r -> r.getStatus() == SubagentRun.RunStatus.COMPLETED);
        if (allCompleted) {
            return SubagentRun.RunStatus.COMPLETED;
        }
        return SubagentRun.RunStatus.FAILED;
    }

    /**
     * 检查状态是否为终态
     */
    private boolean isTerminalStatus(SubagentRun.RunStatus status) {
        return status == SubagentRun.RunStatus.COMPLETED ||
               status == SubagentRun.RunStatus.FAILED ||
               status == SubagentRun.RunStatus.TIMEOUT ||
               status == SubagentRun.RunStatus.CANCELLED ||
               status == SubagentRun.RunStatus.REJECTED;
    }

    // ============ DTO 定义 ============

    /**
     * 调用上下文（区分产品/运维路径）
     */
    public record InvocationContext(
            String principal,      // 调用主体（用户 ID 或 OPS）
            String sessionScope,   // 会话范围（产品路径必须校验归属）
            String auditReason     // 审计原因（运维路径建议填写）
    ) {}

    /**
     * 批次任务请求
     */
    public record BatchTaskRequest(
            String taskName,
            String goal,
            String agentType
    ) {}

    /**
     * 批次状态（内存注册表）
     */
    public static class BatchStatus {
        private final String batchId;
        private final String sessionId;
        private final int totalTasks;
        private final Instant createdAt;
        private final Set<String> runIds = ConcurrentHashMap.newKeySet();
        private volatile boolean cancelled = false;
        private String cancelReason;

        public BatchStatus(String batchId, String sessionId, int totalTasks) {
            this.batchId = batchId;
            this.sessionId = sessionId;
            this.totalTasks = totalTasks;
            this.createdAt = Instant.now();
        }

        public void addRunId(String runId) {
            runIds.add(runId);
        }

        public void markCancelled(String reason) {
            this.cancelled = true;
            this.cancelReason = reason;
        }

        public String getBatchId() {
            return batchId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public int getTotalTasks() {
            return totalTasks;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Set<String> getRunIds() {
            return runIds;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public String getCancelReason() {
            return cancelReason;
        }
    }

    /**
     * 批量派生响应
     */
    public record BatchSpawnResponse(
            boolean success,
            String batchId,
            String sessionId,
            int totalTasks,
            List<TaskSpawnResult> tasks,
            String message,
            String error,
            String errorCode
    ) {
        public static BatchSpawnResponse success(String batchId, String sessionId, int totalTasks,
                                                  List<TaskSpawnResult> tasks) {
            return new BatchSpawnResponse(
                    true, batchId, sessionId, totalTasks, tasks,
                    "Batch spawned successfully. Main agent will resume when all tasks complete.",
                    null, null);
        }

        public static BatchSpawnResponse rejected(String message, String errorCode) {
            return new BatchSpawnResponse(false, null, null, 0, Collections.emptyList(),
                    null, message, errorCode);
        }

        public static BatchSpawnResponse error(String message) {
            return new BatchSpawnResponse(false, null, null, 0, Collections.emptyList(),
                    null, message, "INTERNAL_ERROR");
        }

        public static BatchSpawnResponse partialFailure(String batchId, String sessionId, int totalTasks,
                                                        List<TaskSpawnResult> tasks,
                                                        int rejectedCount, String errorCode) {
            return new BatchSpawnResponse(
                    false,
                    batchId,
                    sessionId,
                    totalTasks,
                    tasks,
                    null,
                    "Some tasks were rejected during batch spawn (rejected="
                            + rejectedCount + "/" + totalTasks + ")",
                    errorCode
            );
        }
    }

    /**
     * 任务派生结果
     */
    public record TaskSpawnResult(
            String runId,
            String status,
            String error
    ) {}

    /**
     * 批次取消响应
     */
    public record BatchCancelResponse(
            boolean success,
            String batchId,
            String sessionId,
            String status,
            List<String> cancelledRunIds,
            String message,
            String error
    ) {
        public static BatchCancelResponse success(String batchId, String sessionId, String status,
                                                   List<String> cancelledRunIds, String reason) {
            return new BatchCancelResponse(
                    true, batchId, sessionId, status, cancelledRunIds,
                    "Cascade cancel accepted; workers will receive termination.",
                    null);
        }

        public static BatchCancelResponse notFound(String batchId, String message) {
            return new BatchCancelResponse(false, batchId, null, null, Collections.emptyList(),
                    null, message);
        }

        public static BatchCancelResponse alreadyTerminal(String batchId, String status) {
            return new BatchCancelResponse(false, batchId, null, status, Collections.emptyList(),
                    "Batch is already " + status,
                    "BATCH_ALREADY_TERMINAL");
        }
    }

    /**
     * 批次查询响应
     */
    public record BatchQueryResponse(
            boolean success,
            String batchId,
            String sessionId,
            int totalTasks,
            int completed,
            int pending,
            int failed,
            String status,
            List<TaskStatus> tasks,
            String createdAt,
            String error
    ) {
        public static BatchQueryResponse success(String batchId, String sessionId, int totalTasks,
                                                  int completed, int pending, int failed,
                                                  String status, List<TaskStatus> tasks,
                                                  String createdAt) {
            return new BatchQueryResponse(
                    true, batchId, sessionId, totalTasks, completed, pending, failed,
                    status, tasks, createdAt, null);
        }

        public static BatchQueryResponse notFound(String batchId, String message) {
            return new BatchQueryResponse(false, batchId, null, 0, 0, 0, 0,
                    null, Collections.emptyList(), null, message);
        }
    }

    /**
     * 任务状态
     */
    public record TaskStatus(
            String runId,
            String status,
            String result,
            String error
    ) {}
}
