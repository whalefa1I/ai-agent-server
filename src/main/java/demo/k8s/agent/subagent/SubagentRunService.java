package demo.k8s.agent.subagent;

import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 子 Agent 运行服务（v1 M5 持久化与恢复）。
 */
@Service
public class SubagentRunService {

    private static final Logger log = LoggerFactory.getLogger(SubagentRunService.class);

    private final SubagentRunRepository repository;

    public SubagentRunService(SubagentRunRepository repository) {
        this.repository = repository;
    }

    /**
     * 创建新的运行记录。
     */
    @Transactional
    public SubagentRun createRun(SpawnRequest request) {
        String runId = generateRunId();
        Instant now = Instant.now();

        SubagentRun run = new SubagentRun();
        run.setRunId(runId);
        run.setParentRunId(null); // v1 暂不实现父子关系
        run.setTenantId(request.getTenantId());
        run.setSessionId(request.getSessionId());
        run.setAppId(request.getAppId());
        run.setStatus(SubagentRun.RunStatus.PENDING);
        run.setGoal(request.getGoal());
        run.setDepth(request.getConstraints().maxDepth());
        run.setTokenBudget(request.getConstraints().maxBudgetTokens());
        run.setAllowedTools(String.join(",", request.getConstraints().allowedToolScopes()));
        run.setDeadlineAt(Instant.ofEpochMilli(request.getConstraints().deadlineEpochMs()));
        run.setCreatedAt(now);
        run.setStartedAt(now);
        run.setUpdatedAt(now);

        repository.save(run);
        log.info("[SubagentRun] Created run: runId={}, sessionId={}, goal={}",
                runId, request.getSessionId(), truncate(request.getGoal(), 50));

        return run;
    }

    /**
     * 更新运行状态。
     */
    @Transactional
    public SubagentRun updateStatus(String runId, SubagentRun.RunStatus newStatus, String resultOrError) {
        return repository.findById(runId).map(run -> {
            run.setStatus(newStatus);
            run.setUpdatedAt(Instant.now());

            if (newStatus == SubagentRun.RunStatus.COMPLETED) {
                run.setResult(resultOrError);
                run.setEndedAt(Instant.now());
            } else if (newStatus == SubagentRun.RunStatus.FAILED ||
                       newStatus == SubagentRun.RunStatus.TIMEOUT ||
                       newStatus == SubagentRun.RunStatus.CANCELLED) {
                run.setErrorMessage(resultOrError);
                run.setEndedAt(Instant.now());
            }

            return repository.save(run);
        }).orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    /**
     * 开始运行（PENDING -> RUNNING）。
     */
    @Transactional
    public SubagentRun startRun(String runId) {
        return repository.findById(runId).map(run -> {
            run.setStatus(SubagentRun.RunStatus.RUNNING);
            run.setStartedAt(Instant.now());
            run.setUpdatedAt(Instant.now());
            return repository.save(run);
        }).orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    /**
     * 获取运行记录。
     */
    public SubagentRun getRun(String runId, String sessionId) {
        return repository.findByRunIdAndSessionId(runId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found or access denied: " + runId));
    }

    /**
     * 查找需要恢复的运行记录（重启后调用）。
     */
    public List<SubagentRun> findRunsToReconcile(String sessionId) {
        List<SubagentRun.RunStatus> activeStatuses = List.of(
                SubagentRun.RunStatus.PENDING,
                SubagentRun.RunStatus.RUNNING,
                SubagentRun.RunStatus.WAITING,
                SubagentRun.RunStatus.SUSPENDED
        );
        return repository.findBySessionIdAndStatusIn(sessionId, activeStatuses);
    }

    /**
     * 恢复运行记录（reconcile）。
     * <p>
     * 对于已过期的任务，标记为 TIMEOUT；对于其他任务，保持原状态等待后续处理。
     */
    @Transactional
    public ReconcileResult reconcile(String runId) {
        return repository.findById(runId).map(run -> {
            Instant now = Instant.now();
            if (run.getDeadlineAt() != null && now.isAfter(run.getDeadlineAt())) {
                // 已过期，标记为超时
                run.setStatus(SubagentRun.RunStatus.TIMEOUT);
                run.setErrorMessage("Task exceeded Wall-Clock TTL during recovery");
                run.setEndedAt(now);
                run.setUpdatedAt(now);
                repository.save(run);
                log.info("[Reconcile] Run timeout: runId={}, deadline={}", runId, run.getDeadlineAt());
                return new ReconcileResult(runId, ReconcileAction.TIMEOUT, "Exceeded TTL");
            }
            // 未过期，保持原状态，等待后续处理
            log.info("[Reconcile] Run preserved: runId={}, status={}", runId, run.getStatus());
            return new ReconcileResult(runId, ReconcileAction.PRESERVE, null);
        }).orElse(new ReconcileResult(runId, ReconcileAction.NOT_FOUND, "Run not found"));
    }

    /**
     * 批量恢复会话中的所有运行记录。
     */
    @Transactional
    public ReconcileSummary reconcileSession(String sessionId) {
        List<SubagentRun> runs = findRunsToReconcile(sessionId);
        int timeoutCount = 0;
        int preserveCount = 0;

        for (SubagentRun run : runs) {
            ReconcileResult result = reconcile(run.getRunId());
            if (result.action() == ReconcileAction.TIMEOUT) {
                timeoutCount++;
            } else if (result.action() == ReconcileAction.PRESERVE) {
                preserveCount++;
            }
        }

        log.info("[Reconcile] Session summary: sessionId={}, total={}, timeout={}, preserve={}",
                sessionId, runs.size(), timeoutCount, preserveCount);

        return new ReconcileSummary(sessionId, runs.size(), timeoutCount, preserveCount);
    }

    /**
     * 增加重试计数。
     */
    @Transactional
    public SubagentRun incrementRetry(String runId) {
        return repository.findById(runId).map(run -> {
            run.setRetryCount(run.getRetryCount() + 1);
            run.setUpdatedAt(Instant.now());
            return repository.save(run);
        }).orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    /**
     * 查找所有活跃的运行记录（用于启动恢复）。
     */
    public List<SubagentRun> findAllActiveRuns() {
        List<SubagentRun.RunStatus> activeStatuses = List.of(
                SubagentRun.RunStatus.PENDING,
                SubagentRun.RunStatus.RUNNING,
                SubagentRun.RunStatus.WAITING,
                SubagentRun.RunStatus.SUSPENDED
        );
        return repository.findByStatusIn(activeStatuses);
    }

    /**
     * 查找已超过 deadline 且仍为 RUNNING 的记录（周期 reconcile 用）。
     */
    public List<SubagentRun> findOverdueRunningRuns(Instant now) {
        return repository.findOverdueRuns(now);
    }

    /**
     * 统计活跃任务数。
     */
    public int countActiveRuns(String sessionId) {
        List<SubagentRun.RunStatus> activeStatuses = List.of(
                SubagentRun.RunStatus.PENDING,
                SubagentRun.RunStatus.RUNNING,
                SubagentRun.RunStatus.WAITING
        );
        return repository.countBySessionIdAndStatusIn(sessionId, activeStatuses);
    }

    private static String generateRunId() {
        return "run-" + System.currentTimeMillis() + "-" +
               UUID.randomUUID().toString().substring(0, 8);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public record ReconcileResult(String runId, ReconcileAction action, String reason) {}
    public record ReconcileSummary(String sessionId, int total, int timeoutCount, int preserveCount) {}

    public enum ReconcileAction {
        TIMEOUT,
        PRESERVE,
        NOT_FOUND
    }
}
