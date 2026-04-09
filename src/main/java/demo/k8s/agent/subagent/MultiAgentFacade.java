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

    public MultiAgentFacade(DemoMultiAgentProperties props,
                            SpawnGatekeeper gatekeeper,
                            SubAgentRuntime runtime,
                            SubagentMetrics metrics) {
        this.props = props;
        this.gatekeeper = gatekeeper;
        this.runtime = runtime;
        this.metrics = metrics;
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
                    new SpawnRequest.SpawnConstraints(
                            8000, // maxBudgetTokens
                            currentDepth + 1,
                            allowedTools,
                            gatekeeper.calculateDeadline().toEpochMilli()
                    )
            );

            // 执行派生（运行时负责 onSpawnStart/onSpawnEnd 与 DB 终态；此处仅处理“未进入运行时”的失败）
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
