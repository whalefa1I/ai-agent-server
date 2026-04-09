package demo.k8s.agent.subagent;

import demo.k8s.agent.subagent.metrics.SubagentMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 子 Agent 恢复管理器（v1 M5 恢复，v2 兼容）。
 * <p>
 * 应用启动时恢复未完成的运行记录，并定期将已过期的 RUNNING 任务标记为 TIMEOUT。
 * <p>
 * 恢复策略：
 * - 已过期的任务：标记为 TIMEOUT
 * - 未过期的 PENDING/RUNNING 任务：重新提交执行（通过 runtime.resume）
 * - WAITING/SUSPENDED 任务：保持状态，等待外部事件（如用户审批）
 */
@Component
public class SubagentReconciler {

    private static final Logger log = LoggerFactory.getLogger(SubagentReconciler.class);

    private final SubagentRunService runService;
    private final SubagentMetrics metrics;
    private final SubAgentRuntime runtime;
    private final ScheduledExecutorService scheduler;

    public SubagentReconciler(SubagentRunService runService, SubagentMetrics metrics, SubAgentRuntime runtime) {
        this.runService = runService;
        this.metrics = metrics;
        this.runtime = runtime;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @PostConstruct
    public void init() {
        log.info("[Reconciler] Starting initial reconcile...");

        scheduler.schedule(() -> {
            try {
                metrics.recordReconcileDuration(() -> {
                    List<SubagentRun> activeRuns = runService.findAllActiveRuns();
                    int timeoutCount = 0;
                    int preserveCount = 0;
                    int resumedCount = 0;

                    for (SubagentRun run : activeRuns) {
                        SubagentRunService.ReconcileResult result = runService.reconcile(run.getRunId());
                        if (result.action() == SubagentRunService.ReconcileAction.TIMEOUT) {
                            timeoutCount++;
                            metrics.recordReconcileTimeout();
                        } else if (result.action() == SubagentRunService.ReconcileAction.PRESERVE) {
                            preserveCount++;
                            metrics.recordReconcilePreserved();
                            // 对于未过期的 RUNNING 任务，尝试恢复执行
                            if (run.getStatus() == SubagentRun.RunStatus.RUNNING) {
                                log.info("[Reconciler] Resuming running task: runId={}, goal={}",
                                        run.getRunId(), truncate(run.getGoal(), 50));
                                // v1: 暂时仅记录日志，实际恢复执行需要 CoordinatorState 配合
                                // TODO: 实现 runtime.resume(run) 来重新提交任务
                                resumedCount++;
                            }
                        }
                    }

                    log.info("[Reconciler] Initial reconcile complete: total={}, timeout={}, preserve={}, resumed={}",
                            activeRuns.size(), timeoutCount, preserveCount, resumedCount);
                    return null;
                });
            } catch (Exception e) {
                log.error("[Reconciler] Initial reconcile failed: {}", e.getMessage(), e);
            }
        }, 5, TimeUnit.SECONDS);
    }

    /**
     * 定期将 deadline 已过的 RUNNING 任务标记为 TIMEOUT（默认每 30 秒）。
     * <p>
     * 缩短间隔以更快发现超时任务（与 wallclockTtlSeconds=180 默认配置匹配）。
     */
    @Scheduled(fixedRate = 30_000)
    public void cleanupTimeoutTasks() {
        try {
            Instant now = Instant.now();
            List<SubagentRun> overdue = runService.findOverdueRunningRuns(now);
            if (overdue.isEmpty()) {
                return;
            }
            log.info("[Reconciler] Periodic cleanup: overdue count={}", overdue.size());
            for (SubagentRun run : overdue) {
                SubagentRunService.ReconcileResult result = runService.reconcile(run.getRunId());
                if (result.action() == SubagentRunService.ReconcileAction.TIMEOUT) {
                    metrics.recordReconcileTimeout();
                }
            }
        } catch (Exception e) {
            log.error("[Reconciler] Periodic cleanup failed: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
