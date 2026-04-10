package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.metrics.SubagentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 多 Agent 门面（v1 M3 实现）。
 * <p>
 * 上游入口唯一化：所有子 Agent 派生请求必须经过此门面。
 * 支持三种模式：off（关闭）、shadow（影子）、on（启用）。
 */
@Component
public class MultiAgentFacade {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentFacade.class);

    private final DemoMultiAgentProperties props;
    private final SpawnGatekeeper gatekeeper;
    private final SubAgentRuntime runtime;
    private final SubagentMetrics metrics;
    private final BatchCompletionListener batchCompletionListener;

    public MultiAgentFacade(DemoMultiAgentProperties props,
                            SpawnGatekeeper gatekeeper,
                            SubAgentRuntime runtime,
                            SubagentMetrics metrics,
                            BatchCompletionListener batchCompletionListener) {
        this.props = props;
        this.gatekeeper = gatekeeper;
        this.runtime = runtime;
        this.metrics = metrics;
        this.batchCompletionListener = batchCompletionListener;
    }

    /**
     * 派生子 Agent（主入口，仅 goal；任务名与类型由运行时推导）。
     */
    public SpawnResult spawn(String goal, int currentDepth, Set<String> allowedTools) {
        return spawnTask(null, goal, null, currentDepth, allowedTools);
    }

    /**
     * 派生子 Agent（与 TaskCreate / 编排层对齐：显式任务名与 Worker 类型）。
     */
    public SpawnResult spawnTask(String taskName, String goal, String agentType, int currentDepth, Set<String> allowedTools) {
        return spawnTask(taskName, goal, agentType, currentDepth, allowedTools, null, 1, 0, null);
    }

    /**
     * 派生子 Agent（支持批次字段）。
     *
     * @param taskName   任务名
     * @param goal       目标
     * @param agentType  Agent 类型
     * @param currentDepth 当前深度
     * @param allowedTools 允许的工具
     * @param batchId    批次 ID（可为 null）
     * @param batchTotal 批次总任务数
     * @param batchIndex 批次内序号
     * @param mainRunId  主 Agent 运行 ID（可为 null）
     */
    public SpawnResult spawnTask(String taskName, String goal, String agentType, int currentDepth,
                                  Set<String> allowedTools, String batchId, int batchTotal, int batchIndex,
                                  String mainRunId) {
        return metrics.recordSpawnDuration(() -> {
            String sessionId = TraceContext.getSessionId();
            String tenantId = TraceContext.getTenantId();
            String appId = TraceContext.getAppId();
            String traceId = TraceContext.getTraceId();

            if (sessionId == null || sessionId.isBlank()) {
                log.warn("[Facade] No active session, rejecting spawn");
                return SpawnResult.error("No active session");
            }

            // 检查是否启用
            if (!props.isEnabled() || props.getMode() == DemoMultiAgentProperties.Mode.off) {
                log.debug("[Facade] Multi-agent disabled, returning no-op");
                metrics.recordSpawnRejected("disabled");
                return SpawnResult.rejected(
                        "Multi-agent system is disabled",
                        SpawnResult.MustDoNext.useLocal("Use local tools to complete this task."));
            }

            // 门控检查 + 并发槽位原子占位（合并为单一操作，避免竞态条件）
            SpawnResult.MustDoNext rejected = gatekeeper.checkAndAcquire(sessionId, currentDepth, allowedTools);
            if (rejected != null) {
                log.info("[Facade] Spawn rejected by gatekeeper: action={}", rejected.action());
                String detail = rejected.suggestion() != null ? rejected.suggestion() : rejected.reason();
                metrics.recordSpawnRejected(detail);
                return SpawnResult.rejected(detail, rejected);
            }

            // Shadow 模式：只记录统计，不实际执行（用于评估门控策略和提示词拆解效果）
            if (props.getMode() == DemoMultiAgentProperties.Mode.shadow) {
                log.info("[Facade] Shadow mode: evaluating spawn without execution");
                // Shadow 模式不注入伪成功结果，仅记录指标
                metrics.recordSpawnShadowEvaluated();
                return SpawnResult.rejected(
                        "Shadow mode evaluation only",
                        SpawnResult.MustDoNext.none());
            }

            // 获取当前运行 ID 作为父运行 ID（用于父子关系追溯）
            String currentRunId = TraceContext.getRunId();

            // 构建派生请求
            SpawnRequest request = new SpawnRequest(
                    "v1",
                    traceId,
                    sessionId,
                    tenantId != null ? tenantId : props.getDefaultTenantId(),
                    appId != null ? appId : "default",
                    taskName,
                    agentType,
                    goal,
                    currentRunId, // parentRunId
                    batchId,      // batchId
                    batchTotal,   // batchTotal
                    batchIndex,   // batchIndex
                    mainRunId,    // mainRunId
                    new SpawnRequest.SpawnConstraints(
                            8000, // maxBudgetTokens
                            currentDepth + 1,
                            allowedTools,
                            gatekeeper.calculateDeadline().toEpochMilli()
                    )
            );

            // 执行派生（运行时负责 onSpawnStart/onSpawnEnd 与 DB 终态；此处仅处理"未进入运行时"的失败）
            try {
                SpawnResult result = runtime.spawn(request).join();
                if (result.isSuccess()) {
                    metrics.recordSpawnSuccess(result.getRunId());
                    log.info("[Facade] Spawn success: runId={}", result.getRunId());
                } else {
                    log.warn("[Facade] Spawn failed: message={}", result.getMessage());
                }
                return result;
            } catch (Exception e) {
                log.error("[Facade] Spawn exception: {}", e.getMessage(), e);
                gatekeeper.releaseConcurrentSlot(sessionId);
                return SpawnResult.error("Spawn failed: " + e.getMessage());
            }
        });
    }

    /**
     * 派生单个子 Agent，并通过合成批次（batch-of-1）确保完成后能唤醒主 Agent。
     * <p>
     * 与 {@link #spawnTask(String, String, String, int, Set)} 的不同之处在于：此方法会先在
     * {@link BatchCompletionListener} 中注册一个 totalTasks=1 的批次上下文，
     * 使得子任务完成时能正常触发 {@link BatchCompletionListener.BatchCompletedEvent}，
     * 进而通过 {@link MainAgentResumeListener} 将结果作为 SYSTEM 消息注入主会话。
     * <p>
     * mainRunId 通过 {@link TraceContext#getRunId()} 自动获取（调用方的运行 ID）。
     */
    public SpawnResult spawnSingle(String taskName, String goal, String agentType,
                                    int currentDepth, Set<String> allowedTools) {
        String sessionId = TraceContext.getSessionId();
        String mainRunId = TraceContext.getRunId();

        BatchContext batchContext = batchCompletionListener.createBatch(sessionId, mainRunId, 1);
        String batchId = batchContext.getBatchId();

        return spawnTask(
                taskName, goal, agentType, currentDepth, allowedTools,
                batchId,   // batchId
                1,          // batchTotal
                0,          // batchIndex
                mainRunId   // mainRunId
        );
    }

    /**
     * 批量派生子 Agent（Map-Reduce 模式）。
     * <p>
     * 创建批次上下文，派发所有子任务，并注册批次完成监听器。
     *
     * @param sessionId  会话 ID
     * @param mainRunId  主 Agent 运行 ID
     * @param tasks      任务列表（每个任务包含 goal 和 agentType）
     * @param currentDepth 当前深度
     * @param allowedTools 允许的工具
     * @return 批次结果（包含 batchId 和所有 runId）
     */
    public BatchSpawnResult spawnBatch(String sessionId, String mainRunId,
                                        java.util.List<BatchTask> tasks,
                                        int currentDepth, Set<String> allowedTools) {
        log.info("[Facade] Spawning batch: sessionId={}, mainRunId={}, totalTasks={}",
                sessionId, mainRunId, tasks.size());

        String previousSession = TraceContext.getSessionId();
        try {
            TraceContext.setSessionId(sessionId);

            BatchContext batchContext = batchCompletionListener.createBatch(sessionId, mainRunId, tasks.size());
            String batchId = batchContext.getBatchId();

            java.util.List<SpawnResult> results = new java.util.ArrayList<>();
            int index = 0;
            for (BatchTask task : tasks) {
                SpawnResult result = spawnTask(
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
                results.add(result);
            }

            return new BatchSpawnResult(batchId, sessionId, tasks.size(), results);
        } finally {
            TraceContext.setSessionId(previousSession);
        }
    }

    /**
     * 批次任务定义
     */
    public record BatchTask(String taskName, String goal, String agentType) {}

    /**
     * 批量派生结果
     */
    public record BatchSpawnResult(String batchId, String sessionId, int totalTasks,
                                    java.util.List<SpawnResult> taskResults) {}

    /**
     * 完成子任务（回调）。
     */
    public void onComplete(String runId, String result) {
        String sessionId = TraceContext.getSessionId();
        if (sessionId != null) {
            gatekeeper.onSpawnEnd(sessionId, runId);
        }
        log.info("[Facade] Task completed: runId={}", runId);
    }

    /**
     * 取消子任务。
     */
    public boolean cancel(String runId) {
        boolean cancelled = runtime.cancel(runId);
        if (cancelled) {
            String sessionId = TraceContext.getSessionId();
            if (sessionId != null) {
                gatekeeper.onSpawnEnd(sessionId, runId);
            }
        }
        return cancelled;
    }

    /**
     * 获取任务状态。
     */
    public SubRunEvent getStatus(String runId) {
        return runtime.getStatus(runId);
    }

    /**
     * 会话结束时清理。
     */
    public void cleanupSession(String sessionId) {
        gatekeeper.cleanupSession(sessionId);
        log.info("[Facade] Session cleanup: sessionId={}", sessionId);
    }
}
