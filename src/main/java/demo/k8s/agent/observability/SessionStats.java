package demo.k8s.agent.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话级统计信息，与 Claude Code 的 StatsContext 对齐。
 * <p>
 * 使用 @SessionScope（需要在配置中声明）跟踪当前会话的所有指标。
 */
@Component
public class SessionStats {

    private static final Logger log = LoggerFactory.getLogger(SessionStats.class);

    // 会话 ID
    private final String sessionId = generateSessionId();

    // 会话开始时间
    private final Instant sessionStartedAt = Instant.now();

    // 计数器
    private final AtomicLong totalModelCalls = new AtomicLong(0);
    private final AtomicLong totalToolCalls = new AtomicLong(0);
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final AtomicLong totalCacheReadTokens = new AtomicLong(0);
    private final AtomicLong totalCacheWriteTokens = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong successfulToolCalls = new AtomicLong(0);
    private final AtomicLong failedToolCalls = new AtomicLong(0);

    // 最近的工具调用记录（保留最近 50 条）
    private final ConcurrentLinkedQueue<ToolCallMetrics> recentToolCalls = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECENT_TOOL_CALLS = 50;

    // 最近的模型调用记录（保留最近 20 条）
    private final ConcurrentLinkedQueue<ModelCallMetrics> recentModelCalls = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECENT_MODEL_CALLS = 20;

    // Micrometer 注册表（可选）
    private MeterRegistry meterRegistry;

    public SessionStats() {
        log.info("创建新会话：{}", sessionId);
    }

    // ===== 记录方法 =====

    /**
     * 记录模型调用开始
     */
    public ModelCallMetrics startModelCall(String model) {
        totalModelCalls.incrementAndGet();
        ModelCallMetrics metrics = ModelCallMetrics.create(model, TokenCounts.ZERO);
        log.debug("开始模型调用：{}", metrics.id());
        return metrics;
    }

    /**
     * 记录模型调用完成
     */
    public void completeModelCall(ModelCallMetrics metrics, TokenCounts counts) {
        ModelCallMetrics completed = metrics.complete(counts);

        totalInputTokens.addAndGet(counts.inputTokens());
        totalOutputTokens.addAndGet(counts.outputTokens());
        totalCacheReadTokens.addAndGet(counts.cacheReadTokens());
        totalCacheWriteTokens.addAndGet(counts.cacheWriteTokens());
        totalLatencyMs.addAndGet(completed.getLatencyMs());

        // 添加到最近记录
        addRecentModelCall(completed);

        // 更新 Micrometer
        if (meterRegistry != null) {
            updateMicrometer();
        }

        log.debug("完成模型调用：{} tokens, {} ms", counts.total(), completed.getLatencyMs());
    }

    /**
     * 记录模型调用失败
     */
    public void failModelCall(ModelCallMetrics metrics, String error) {
        ModelCallMetrics completed = metrics.completeError(error);
        totalLatencyMs.addAndGet(completed.getLatencyMs());
        addRecentModelCall(completed);
        log.warn("模型调用失败：{}", error);
    }

    /**
     * 记录工具调用开始
     */
    public ToolCallMetrics startToolCall(String toolName, String inputSummary) {
        totalToolCalls.incrementAndGet();
        ToolCallMetrics metrics = ToolCallMetrics.create(toolName, inputSummary);
        log.debug("开始工具调用：{}", toolName);
        return metrics;
    }

    /**
     * 记录工具调用完成
     */
    public void completeToolCall(ToolCallMetrics metrics, String output) {
        ToolCallMetrics completed = metrics.complete(output);
        successfulToolCalls.incrementAndGet();
        totalLatencyMs.addAndGet(completed.getLatencyMs());
        addRecentToolCall(completed);

        if (meterRegistry != null) {
            Timer timer = Timer.builder("agent.tool.execution_time")
                    .tag("tool", completed.toolName())
                    .tag("status", "success")
                    .register(meterRegistry);
            timer.record(completed.latency());
        }

        log.debug("完成工具调用：{} ({} ms)", completed.toolName(), completed.getLatencyMs());
    }

    /**
     * 记录工具调用失败
     */
    public void failToolCall(ToolCallMetrics metrics, String error) {
        ToolCallMetrics completed = metrics.completeError(error);
        failedToolCalls.incrementAndGet();
        totalLatencyMs.addAndGet(completed.getLatencyMs());
        addRecentToolCall(completed);

        if (meterRegistry != null) {
            Timer timer = Timer.builder("agent.tool.execution_time")
                    .tag("tool", completed.toolName())
                    .tag("status", "error")
                    .register(meterRegistry);
            timer.record(completed.latency());
        }

        log.warn("工具调用失败：{} - {}", completed.toolName(), error);
    }

    // ===== 获取方法 =====

    public String getSessionId() {
        return sessionId;
    }

    public Instant getSessionStartedAt() {
        return sessionStartedAt;
    }

    public long getTotalModelCalls() {
        return totalModelCalls.get();
    }

    public long getTotalToolCalls() {
        return totalToolCalls.get();
    }

    public long getTotalInputTokens() {
        return totalInputTokens.get();
    }

    public long getTotalOutputTokens() {
        return totalOutputTokens.get();
    }

    public long getTotalTokens() {
        return totalInputTokens.get() + totalOutputTokens.get();
    }

    public long getTotalCacheReadTokens() {
        return totalCacheReadTokens.get();
    }

    public long getTotalCacheWriteTokens() {
        return totalCacheWriteTokens.get();
    }

    public long getTotalLatencyMs() {
        return totalLatencyMs.get();
    }

    public long getSuccessfulToolCalls() {
        return successfulToolCalls.get();
    }

    public long getFailedToolCalls() {
        return failedToolCalls.get();
    }

    public List<ToolCallMetrics> getRecentToolCalls() {
        return List.copyOf(recentToolCalls);
    }

    public List<ModelCallMetrics> getRecentModelCalls() {
        return List.copyOf(recentModelCalls);
    }

    public Duration getSessionDuration() {
        return Duration.between(sessionStartedAt, Instant.now());
    }

    public double getAverageModelLatencyMs() {
        long calls = totalModelCalls.get();
        return calls > 0 ? (double) totalLatencyMs.get() / calls : 0;
    }

    public double getToolSuccessRate() {
        long total = totalToolCalls.get();
        return total > 0 ? (double) successfulToolCalls.get() / total : 1.0;
    }

    // ===== Micrometer 集成 =====

    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private void updateMicrometer() {
        // Gauge 指标
        meterRegistry.gauge("agent.session.input_tokens", this, SessionStats::getTotalInputTokens);
        meterRegistry.gauge("agent.session.output_tokens", this, SessionStats::getTotalOutputTokens);
        meterRegistry.gauge("agent.session.model_calls", this, SessionStats::getTotalModelCalls);
        meterRegistry.gauge("agent.session.tool_calls", this, SessionStats::getTotalToolCalls);
    }

    // ===== 内部方法 =====

    private void addRecentToolCall(ToolCallMetrics metrics) {
        recentToolCalls.add(metrics);
        while (recentToolCalls.size() > MAX_RECENT_TOOL_CALLS) {
            recentToolCalls.poll();
        }
    }

    private void addRecentModelCall(ModelCallMetrics metrics) {
        recentModelCalls.add(metrics);
        while (recentModelCalls.size() > MAX_RECENT_MODEL_CALLS) {
            recentModelCalls.poll();
        }
    }

    private static String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 会话统计快照
     */
    public record StatsSnapshot(
            String sessionId,
            Instant sessionStartedAt,
            Duration sessionDuration,
            long totalModelCalls,
            long totalToolCalls,
            long totalInputTokens,
            long totalOutputTokens,
            long totalCacheReadTokens,
            long totalCacheWriteTokens,
            double averageModelLatencyMs,
            double toolSuccessRate,
            List<ToolCallMetrics> recentToolCalls,
            List<ModelCallMetrics> recentModelCalls
    ) {
        public static StatsSnapshot from(SessionStats stats) {
            return new StatsSnapshot(
                    stats.getSessionId(),
                    stats.getSessionStartedAt(),
                    stats.getSessionDuration(),
                    stats.getTotalModelCalls(),
                    stats.getTotalToolCalls(),
                    stats.getTotalInputTokens(),
                    stats.getTotalOutputTokens(),
                    stats.getTotalCacheReadTokens(),
                    stats.getTotalCacheWriteTokens(),
                    stats.getAverageModelLatencyMs(),
                    stats.getToolSuccessRate(),
                    stats.getRecentToolCalls(),
                    stats.getRecentModelCalls()
            );
        }
    }

    public StatsSnapshot getSnapshot() {
        return StatsSnapshot.from(this);
    }
}
