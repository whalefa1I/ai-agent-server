package demo.k8s.agent.subagent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子 Agent 可观测性指标（v1 M6 实现）。
 * <p>
 * 黄金指标：
 * - subagent.spawn.rejected.rate: 门控拦截率
 * - subagent.wallclock.timeout.count: Wall-Clock TTL 触发次数
 * - context_object.write.failure.count: 外置写入失败次数
 * - context_object.read.bytes: 读回流量
 * - subagent.active.runs: 活跃任务数
 */
@Component
public class SubagentMetrics {

    private static final Logger log = LoggerFactory.getLogger(SubagentMetrics.class);

    private final MeterRegistry meterRegistry;

    // 计数器
    private final Counter spawnRejectedCounter;
    private final Counter spawnTimeoutCounter;
    private final Counter contextObjectWriteFailureCounter;
    private final Counter contextObjectReadBytesCounter;
    private final Counter spawnSuccessCounter;
    private final Counter reconcileTimeoutCounter;
    private final Counter reconcilePreservedCounter;
    private final Counter spawnShadowEvaluatedCounter;

    // 计时器
    private final Timer spawnDurationTimer;
    private final Timer reconcileDurationTimer;

    // 活跃任务数（按会话）
    private final Map<String, AtomicInteger> activeRunsBySession = new ConcurrentHashMap<>();

    public SubagentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 计数器
        this.spawnRejectedCounter = meterRegistry.counter("subagent.spawn.rejected", "reason", "gatekeeper");
        this.spawnTimeoutCounter = meterRegistry.counter("subagent.wallclock.timeout");
        this.contextObjectWriteFailureCounter = meterRegistry.counter("context_object.write.failure");
        this.contextObjectReadBytesCounter = meterRegistry.counter("context_object.read.bytes");
        this.spawnSuccessCounter = meterRegistry.counter("subagent.spawn.success");
        this.reconcileTimeoutCounter = meterRegistry.counter("subagent.reconcile.timeout");
        this.reconcilePreservedCounter = meterRegistry.counter("subagent.reconcile.preserved");
        this.spawnShadowEvaluatedCounter = meterRegistry.counter("subagent.shadow.evaluated");

        // 计时器
        this.spawnDurationTimer = meterRegistry.timer("subagent.spawn.duration");
        this.reconcileDurationTimer = meterRegistry.timer("subagent.reconcile.duration");

        // 活跃任务数 Gauge（汇总）
        Gauge.builder("subagent.active.runs", this, SubagentMetrics::getTotalActiveRuns)
                .description("Total active subagent runs across all sessions")
                .register(meterRegistry);

        log.info("[SubagentMetrics] Initialized subagent metrics");
    }

    /**
     * 记录派生被拒绝。
     */
    public void recordSpawnRejected(String reason) {
        spawnRejectedCounter.increment();
        log.debug("[Metrics] Spawn rejected: reason={}", reason);
    }

    /**
     * 记录 Shadow 模式下的评估（未实际执行）。
     */
    public void recordSpawnShadowEvaluated() {
        spawnShadowEvaluatedCounter.increment();
        log.debug("[Metrics] Shadow mode spawn evaluated: reason=shadow_only");
    }

    /**
     * 记录派生成功。
     */
    public void recordSpawnSuccess(String runId) {
        spawnSuccessCounter.increment();
        log.debug("[Metrics] Spawn success: runId={}", runId);
    }

    /**
     * 记录超时。
     */
    public void recordTimeout() {
        spawnTimeoutCounter.increment();
    }

    /**
     * 记录 ContextObject 写入失败。
     */
    public void recordContextObjectWriteFailure() {
        contextObjectWriteFailureCounter.increment();
    }

    /**
     * 记录 ContextObject 读取字节数。
     */
    public void recordContextObjectRead(int bytes) {
        contextObjectReadBytesCounter.increment(bytes);
    }

    /**
     * 记录恢复超时任务。
     */
    public void recordReconcileTimeout() {
        reconcileTimeoutCounter.increment();
    }

    /**
     * 记录恢复保留任务。
     */
    public void recordReconcilePreserved() {
        reconcilePreservedCounter.increment();
    }

    /**
     * 记录派生耗时。
     */
    public <T> T recordSpawnDuration(java.util.function.Supplier<T> supplier) {
        return spawnDurationTimer.record(supplier);
    }

    /**
     * 记录恢复耗时。
     */
    public <T> T recordReconcileDuration(java.util.function.Supplier<T> supplier) {
        return reconcileDurationTimer.record(supplier);
    }

    /**
     * 设置活跃任务数。
     */
    public void setActiveRuns(String sessionId, int count) {
        activeRunsBySession.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).set(count);
    }

    /**
     * 获取总活跃任务数。
     */
    private double getTotalActiveRuns() {
        return activeRunsBySession.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
    }

    /**
     * 清理会话指标。
     */
    public void cleanupSession(String sessionId) {
        activeRunsBySession.remove(sessionId);
    }
}
