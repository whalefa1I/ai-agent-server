package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批次完成监听器：监听子任务完成事件，检测批次是否全部完成。
 * <p>
 * 当批次中所有子任务完成后，自动唤醒主 Agent 进行结果汇总。
 * 结果全文外置存储到文件系统，主 Agent 上下文仅注入路径和摘要。
 */
@Component
public class BatchCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(BatchCompletionListener.class);

    /**
     * 批次上下文注册表（内存缓存）
     */
    private final Map<String, BatchContext> batchRegistry = new ConcurrentHashMap<>();

    private final SubagentRunService runService;
    private final EventBus eventBus;
    private final DemoMultiAgentProperties agentProperties;
    private final SubagentResultStorage resultStorage;

    public BatchCompletionListener(
            SubagentRunService runService,
            EventBus eventBus,
            DemoMultiAgentProperties agentProperties,
            SubagentResultStorage resultStorage) {
        this.runService = runService;
        this.eventBus = eventBus;
        this.agentProperties = agentProperties;
        this.resultStorage = resultStorage;
        log.info("[BatchCompletionListener] Initialized with max concurrent spawns: {}",
                agentProperties.getMaxConcurrentSpawns());
    }

    /**
     * 注册新批次。
     *
     * @param sessionId   会话 ID
     * @param mainRunId   主 Agent 运行 ID（用于唤醒）
     * @param totalTasks  批次总任务数
     * @return 批次上下文
     */
    public BatchContext createBatch(String sessionId, String mainRunId, int totalTasks) {
        String batchId = generateBatchId();
        long ttlSeconds = agentProperties.getWallclockTtlSeconds();
        BatchContext ctx = new BatchContext(batchId, sessionId, mainRunId, totalTasks, ttlSeconds);
        batchRegistry.put(batchId, ctx);
        log.info("[BatchCompletionListener] Batch created: batchId={}, sessionId={}, mainRunId={}, totalTasks={}",
                batchId, sessionId, mainRunId, totalTasks);
        return ctx;
    }

    /**
     * 子任务完成回调（由 SubagentRunService 或其他组件调用）。
     * <p>
     * 结果全文外置存储到文件系统，BatchContext 中仅存储路径和摘要。
     *
     * @param runId     子任务运行 ID
     * @param sessionId 会话 ID
     * @param result    执行结果
     */
    public void onSubagentCompleted(String runId, String sessionId, String result) {
        // 1. 查询运行记录获取 batchId
        SubagentRun run;
        try {
            run = runService.getRun(runId, sessionId);
        } catch (IllegalArgumentException e) {
            log.warn("[BatchCompletionListener] Run not found: runId={}, sessionId={}", runId, sessionId);
            return;
        }

        String batchId = run.getBatchId();
        if (batchId == null || batchId.isBlank()) {
            log.debug("[BatchCompletionListener] Non-batch task completed: runId={}", runId);
            return; // 非批次任务，跳过
        }

        // 2. 外置存储结果
        String status = run.getStatus() != null ? run.getStatus().name() : "COMPLETED";
        String resultPath = resultStorage.writeResult(batchId, runId, result, status);
        String summary = resultStorage.summarize(result, 100);

        // 3. 更新批次进度（存储路径和摘要，而非全文）
        BatchContext ctx = batchRegistry.get(batchId);
        if (ctx == null) {
            // 批次上下文不存在，可能是重启后，尝试从 DB 重建
            log.warn("[BatchCompletionListener] Batch context not found, rebuilding from DB: batchId={}", batchId);
            ctx = rebuildBatchContextFromDb(batchId, sessionId);
            if (ctx == null) {
                log.error("[BatchCompletionListener] Failed to rebuild batch context: batchId={}", batchId);
                return;
            }
        }

        ctx.markCompleted(runId, resultPath, summary, status);

        // 4. 检测是否全部完成
        if (ctx.isAllCompleted()) {
            log.info("[BatchCompletionListener] Batch completed: batchId={}, total={}", batchId, ctx.getTotalTasks());

            // 5. 唤醒主 Agent
            resumeMainThread(ctx);

            // 6. 清理注册表
            batchRegistry.remove(batchId);
        } else {
            log.info("[BatchCompletionListener] Batch progress: batchId={}/{}, {}%",
                    ctx.getCompletedCount(), ctx.getTotalTasks(),
                    (ctx.getCompletedCount() * 100) / ctx.getTotalTasks());
        }
    }

    /**
     * 从 DB 重建批次上下文（重启恢复场景）。
     */
    private BatchContext rebuildBatchContextFromDb(String batchId, String sessionId) {
        try {
            var runs = runService.findByBatchId(batchId, sessionId);
            if (runs.isEmpty()) {
                return null;
            }

            String mainRunId = runs.stream()
                    .map(SubagentRun::getMainRunId)
                    .filter(id -> id != null && !id.isBlank())
                    .findFirst()
                    .orElse(null);

            if (mainRunId == null) {
                log.warn("[BatchCompletionListener] mainRunId not found for batch: batchId={}", batchId);
                return null;
            }

            int fromMeta = runs.stream().mapToInt(SubagentRun::getBatchTotal).filter(t -> t > 0).findFirst().orElse(0);
            int totalTasks = fromMeta > 0 ? fromMeta : runs.size();
            if (totalTasks < runs.size()) {
                totalTasks = runs.size();
            }

            BatchContext ctx = new BatchContext(batchId, sessionId, mainRunId, totalTasks,
                    agentProperties.getWallclockTtlSeconds());

            for (SubagentRun run : runs) {
                if (isTerminalRunStatus(run.getStatus())) {
                    // 为已存在的运行生成结果路径
                    String resultPath = resultStorage.getResultPath(batchId, run.getRunId());
                    String summary = resultStorage.summarize(
                            run.getResult() != null ? run.getResult() : run.getErrorMessage(), 100);
                    ctx.markCompleted(run.getRunId(), resultPath, summary, run.getStatus().name());
                }
            }

            batchRegistry.put(batchId, ctx);
            return ctx;
        } catch (Exception e) {
            log.error("[BatchCompletionListener] Failed to rebuild batch context: batchId={}", batchId, e);
            return null;
        }
    }

    private static boolean isTerminalRunStatus(SubagentRun.RunStatus status) {
        return status == SubagentRun.RunStatus.COMPLETED
                || status == SubagentRun.RunStatus.FAILED
                || status == SubagentRun.RunStatus.TIMEOUT
                || status == SubagentRun.RunStatus.CANCELLED;
    }

    /**
     * 唤醒主 Agent：通过系统消息注入。
     */
    private void resumeMainThread(BatchContext ctx) {
        String summary = ctx.collectResultsAsSummary();

        // 发布批次完成事件
        eventBus.publish(new BatchCompletedEvent(
                ctx.getSessionId(),
                ctx.getBatchId(),
                ctx.getMainRunId(),
                ctx.getTotalTasks(),
                summary
        ));

        log.info("[BatchCompletionListener] Published BatchCompletedEvent: batchId={}, mainRunId={}",
                ctx.getBatchId(), ctx.getMainRunId());

    }

    /**
     * 超时清理：定期检查超时批次。
     * <p>
     * 对于超时批次，为未完成的任务生成占位结果文件，然后唤醒主 Agent。
     */
    @Scheduled(fixedRateString = "${demo.multi-agent.batch-cleanup-interval-ms:60000}")
    public void cleanupTimeoutBatches() {
        int timeoutCount = 0;

        for (Map.Entry<String, BatchContext> entry : batchRegistry.entrySet()) {
            BatchContext ctx = entry.getValue();
            if (ctx.isTimedOut()) {
                log.warn("[BatchCompletionListener] Batch timeout: batchId={}, completed={}/{}",
                        ctx.getBatchId(), ctx.getCompletedCount(), ctx.getTotalTasks());

                // 为未完成的任务生成占位结果
                List<SubagentRun> runs;
                try {
                    runs = runService.findByBatchId(ctx.getBatchId(), ctx.getSessionId());
                    for (SubagentRun run : runs) {
                        if (!isTerminalRunStatus(run.getStatus())) {
                            // 任务尚未完成，生成超时占位结果
                            String placeholderResult = String.format(
                                    "Task timed out. Batch: %s, RunId: %s, Goal: %s",
                                    ctx.getBatchId(), run.getRunId(), run.getGoal());
                            String resultPath = resultStorage.writeResult(
                                    ctx.getBatchId(), run.getRunId(), placeholderResult, "TIMEOUT");
                            String summary = resultStorage.summarize(placeholderResult, 100);
                            ctx.markCompleted(run.getRunId(), resultPath, summary, "TIMEOUT");

                            // 更新 DB 状态
                            try {
                                runService.updateStatus(run.getRunId(), SubagentRun.RunStatus.TIMEOUT,
                                        "Task timed out during batch cleanup");
                            } catch (Exception e) {
                                log.warn("[BatchCompletionListener] Failed to update run status: runId={}",
                                        run.getRunId(), e);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[BatchCompletionListener] Failed to fetch runs for timeout handling: batchId={}",
                            ctx.getBatchId(), e);
                }

                // 超时处理：强制标记为完成并唤醒主 Agent（如果有 mainRunId）
                if (ctx.getMainRunId() != null) {
                    resumeMainThread(ctx);
                }

                batchRegistry.remove(entry.getKey());
                timeoutCount++;
            }
        }

        if (timeoutCount > 0) {
            log.info("[BatchCompletionListener] Cleanup summary: {} batches timed out", timeoutCount);
        }
    }

    /**
     * 获取批次状态（用于运维 API）。
     */
    public BatchContext getBatchStatus(String batchId) {
        return batchRegistry.get(batchId);
    }

    /**
     * 获取所有活跃批次（用于运维 API）。
     */
    public Map<String, BatchContext> getAllActiveBatches() {
        return new ConcurrentHashMap<>(batchRegistry);
    }

    private static String generateBatchId() {
        return "batch-" + System.currentTimeMillis() + "-" +
                java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 批次完成事件
     */
    public static class BatchCompletedEvent extends Event {
        private final String batchId;
        private final String mainRunId;
        private final int totalTasks;
        private final String resultsSummary;

        public BatchCompletedEvent(String sessionId, String batchId, String mainRunId,
                                    int totalTasks, String resultsSummary) {
            super(sessionId, "system", buildPayload(batchId, mainRunId, totalTasks, resultsSummary));
            this.batchId = batchId;
            this.mainRunId = mainRunId;
            this.totalTasks = totalTasks;
            this.resultsSummary = resultsSummary;
        }

        private static Map<String, Object> buildPayload(String batchId, String mainRunId,
                                                         int totalTasks, String resultsSummary) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("batchId", batchId);
            payload.put("mainRunId", mainRunId);
            payload.put("totalTasks", totalTasks);
            payload.put("resultsSummary", resultsSummary);
            return payload;
        }

        @Override
        public String getEventType() {
            return "BATCH_COMPLETED";
        }

        public String getBatchId() {
            return batchId;
        }

        public String getMainRunId() {
            return mainRunId;
        }

        public int getTotalTasks() {
            return totalTasks;
        }

        public String getResultsSummary() {
            return resultsSummary;
        }
    }
}
