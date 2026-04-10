package demo.k8s.agent.subagent;

import demo.k8s.agent.config.SubagentAsyncConfiguration;
import demo.k8s.agent.coordinator.AsyncSubagentExecutor;
import demo.k8s.agent.coordinator.CoordinatorState;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.web.SubagentSseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 本地子 Agent 运行时实现（v1）。
 * <p>
 * 在独立执行器上调用 {@link AsyncSubagentExecutor} 执行真实 Worker 循环，并维护
 * {@link SubagentRun} 与 {@link SpawnGatekeeper} 生命周期（与 {@link MultiAgentFacade} 的并发槽位对齐）。
 */
@Component
public class LocalSubAgentRuntime implements SubAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(LocalSubAgentRuntime.class);

    private final SubagentRunService runService;
    private final SpawnGatekeeper gatekeeper;
    private final AsyncSubagentExecutor asyncSubagentExecutor;
    private final Executor subagentExecutor;
    private final BatchCompletionListener batchCompletionListener;
    private final SubagentSseController sseController;

    public LocalSubAgentRuntime(
            SubagentRunService runService,
            SpawnGatekeeper gatekeeper,
            AsyncSubagentExecutor asyncSubagentExecutor,
            @Qualifier(SubagentAsyncConfiguration.SUBAGENT_TASK_EXECUTOR) Executor subagentExecutor,
            BatchCompletionListener batchCompletionListener,
            SubagentSseController sseController) {
        this.runService = runService;
        this.gatekeeper = gatekeeper;
        this.asyncSubagentExecutor = asyncSubagentExecutor;
        this.subagentExecutor = subagentExecutor;
        this.batchCompletionListener = batchCompletionListener;
        this.sseController = sseController;
    }

    @Override
    public CompletableFuture<SpawnResult> spawn(SpawnRequest request) {
        CompletableFuture<SpawnResult> done = new CompletableFuture<>();
        try {
            // 使用 parentRunId 创建运行记录（支持父子关系追溯）
            SubagentRun run = runService.createRun(request, request.getParentRunId());
            String runId = run.getRunId();
            String sessionId = request.getSessionId();
            runService.startRun(runId);

            String taskName = Optional.ofNullable(request.getTaskName())
                    .filter(s -> !s.isBlank())
                    .orElseGet(() -> truncate(request.getGoal(), 80));
            String agentType = Optional.ofNullable(request.getAgentType())
                    .filter(s -> !s.isBlank())
                    .orElse("general");
            Duration timeout = resolveTimeout(request);

            subagentExecutor.execute(() -> {
                try {
                    bindWorkerThreadTraceContext(request, runId, sessionId);
                    gatekeeper.onSpawnStart(sessionId, runId);

                    // 发布 SSE 启动事件
                    sseController.publishStatusEvent(runId, sessionId, "running", null, null);

                    CoordinatorState.TaskResult tr = asyncSubagentExecutor.runSynchronousAgent(
                            taskName, request.getGoal(), agentType, timeout);
                    if (tr.error() != null && !tr.error().isBlank() && !"stopped".equalsIgnoreCase(tr.error())) {
                        String errorResult = tr.error();
                        runService.updateStatus(runId, SubagentRun.RunStatus.FAILED, errorResult);
                        // 发布 SSE 失败事件
                        sseController.publishStatusEvent(runId, sessionId, "FAILED", null, errorResult);
                        // 通知批次监听器（即使是失败也要通知，让批次知道这个任务结束了）
                        batchCompletionListener.onSubagentCompleted(runId, sessionId, errorResult);
                        done.complete(SpawnResult.error(errorResult));
                    } else {
                        String out = tr.output() != null ? tr.output() : "";
                        runService.updateStatus(runId, SubagentRun.RunStatus.COMPLETED, out);
                        // 发布 SSE 完成事件
                        sseController.publishStatusEvent(runId, sessionId, "COMPLETED", out, null);
                        // 通知批次监听器
                        batchCompletionListener.onSubagentCompleted(runId, sessionId, out);
                        done.complete(SpawnResult.success(runId));
                    }
                } catch (Exception e) {
                    log.error("[LocalRuntime] Subagent run failed: runId={}", runId, e);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "subagent failed";
                    try {
                        runService.updateStatus(runId, SubagentRun.RunStatus.FAILED, errorMsg);
                        // 发布 SSE 异常事件
                        sseController.publishStatusEvent(runId, sessionId, "FAILED", null, "ERROR: " + errorMsg);
                        // 通知批次监听器（异常情况）
                        batchCompletionListener.onSubagentCompleted(runId, sessionId, "ERROR: " + errorMsg);
                    } catch (Exception suppress) {
                        log.warn("[LocalRuntime] Failed to persist FAILED status: runId={}", runId, suppress);
                    }
                    done.complete(SpawnResult.error(errorMsg));
                } finally {
                    try {
                        gatekeeper.onSpawnEnd(sessionId, runId);
                    } finally {
                        TraceContext.clear();
                    }
                }
            });
        } catch (Exception e) {
            log.error("[LocalRuntime] Failed to schedule subagent: {}", e.getMessage(), e);
            gatekeeper.releaseConcurrentSlot(request.getSessionId());
            done.complete(SpawnResult.error(
                    e.getMessage() != null ? e.getMessage() : "Failed to start subagent"));
        }
        return done;
    }

    private static Duration resolveTimeout(SpawnRequest request) {
        Instant deadline = Instant.ofEpochMilli(request.getConstraints().deadlineEpochMs());
        Duration until = Duration.between(Instant.now(), deadline);
        if (until.isNegative() || until.isZero()) {
            until = Duration.ofSeconds(30);
        }
        Duration cap = Duration.ofMinutes(30);
        if (until.compareTo(cap) > 0) {
            until = cap;
        }
        return until;
    }

    @Override
    public boolean cancel(String runId) {
        try {
            runService.updateStatus(runId, SubagentRun.RunStatus.CANCELLED, "Cancelled by user");
            // 发布 SSE 取消事件
            String sessionId = TraceContext.getSessionId();
            if (sessionId != null) {
                sseController.publishStatusEvent(runId, sessionId, "CANCELLED", null, "Cancelled by user");
            }
            return true;
        } catch (Exception e) {
            log.warn("[LocalRuntime] Cancel failed for runId={}: {}", runId, e.getMessage());
            return false;
        }
    }

    @Override
    public SubRunEvent getStatus(String runId) {
        String sessionId = TraceContext.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try {
            SubagentRun run = runService.getRun(runId, sessionId);
            SubagentRun.RunStatus st = run.getStatus();
            return switch (st) {
                case PENDING, RUNNING, WAITING -> SubRunEvent.started(runId, sessionId);
                case COMPLETED -> SubRunEvent.completed(runId, sessionId,
                        run.getResult() != null ? run.getResult() : "");
                case FAILED, REJECTED -> SubRunEvent.failed(runId, sessionId,
                        run.getErrorMessage() != null ? run.getErrorMessage() : st.name());
                case TIMEOUT -> SubRunEvent.timeout(runId, sessionId);
                case SUSPENDED -> new SubRunEvent(runId, sessionId, SubRunEvent.EventType.SUSPEND,
                        Instant.now(), "SUSPENDED", null, null);
                case CANCELLED -> SubRunEvent.rejected(runId, sessionId, "CANCELLED");
            };
        } catch (Exception e) {
            log.debug("[LocalRuntime] getStatus: runId={}, err={}", runId, e.getMessage());
            return null;
        }
    }

    @Override
    public String getRuntimeType() {
        return "local";
    }

    /**
     * 在子 Agent Worker 线程绑定与 {@link SpawnRequest} 一致的追踪上下文，使工具回调日志、权限与主会话对齐。
     * <p>
     * 同时绑定 runId，以便子 agent 内再次派生时能够追溯父子关系。
     */
    private static void bindWorkerThreadTraceContext(SpawnRequest request, String runId, String sessionId) {
        String trace = request.getTraceId() != null && !request.getTraceId().isBlank()
                ? request.getTraceId()
                : TraceContext.generateTraceId();
        TraceContext.init(trace, TraceContext.generateSpanId());
        TraceContext.setSessionId(sessionId);
        String tenant = request.getTenantId() != null && !request.getTenantId().isBlank()
                ? request.getTenantId() : "default";
        TraceContext.setTenantId(tenant);
        String app = request.getAppId() != null && !request.getAppId().isBlank()
                ? request.getAppId() : "subagent-worker";
        TraceContext.setAppId(app);
        TraceContext.setUserId("subagent-run:" + runId);
        TraceContext.setRunId(runId); // 绑定 runId，支持子 agent 内再次派生时的父子追溯
        log.info("[LocalRuntime] Worker TraceContext: sessionId={}, runId={}, traceId={}, tenantId={}, appId={}",
                sessionId, runId, trace, tenant, app);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "subtask";
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return "subtask";
        }
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "...";
    }
}
