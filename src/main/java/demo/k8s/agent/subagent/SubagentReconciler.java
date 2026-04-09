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
 * 子 Agent 恢复管理器（v1 M5 恢复）。
 * <p>
 * 应用启动时恢复未完成的运行记录，并定期将已过期的 RUNNING 任务收敛为 TIMEOUT。
 */
@Component
public class SubagentReconciler {

    private static final Logger log = LoggerFactory.getLogger(SubagentReconciler.class);

    private final SubagentRunService runService;
    private final SubagentMetrics metrics;
    private final ScheduledExecutorService scheduler;

    public SubagentReconciler(SubagentRunService runService, SubagentMetrics metrics) {
        this.runService = runService;
        this.metrics = metrics;
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

                    for (SubagentRun run : activeRuns) {
                        SubagentRunService.ReconcileResult result = runService.reconcile(run.getRunId());
                        if (result.action() == SubagentRunService.ReconcileAction.TIMEOUT) {
                            timeoutCount++;
                            metrics.recordReconcileTimeout();
                        } else if (result.action() == SubagentRunService.ReconcileAction.PRESERVE) {
                            preserveCount++;
                            metrics.recordReconcilePreserved();
                        }
                    }

                    log.info("[Reconciler] Initial reconcile complete: total={}, timeout={}, preserve={}",
                            activeRuns.size(), timeoutCount, preserveCount);
                    return null;
                });
            } catch (Exception e) {
                log.error("[Reconciler] Initial reconcile failed: {}", e.getMessage(), e);
            }
        }, 5, TimeUnit.SECONDS);
    }

    /**
     * 定期将 deadline 已过的 RUNNING 任务标记为 TIMEOUT（默认每 5 分钟）。
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanupTimeoutTasks() {
        try {
            Instant now = Instant.now();
            List<SubagentRun> overdue = runService.findOverdueRunningRuns(now);
            if (overdue.isEmpty()) {
                return;
            }
            log.debug("[Reconciler] Periodic cleanup: overdue count={}", overdue.size());
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
}
