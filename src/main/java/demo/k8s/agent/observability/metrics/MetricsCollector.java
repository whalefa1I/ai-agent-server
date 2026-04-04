package demo.k8s.agent.observability.metrics;

import demo.k8s.agent.observability.TokenCounts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标收集器（基于 Micrometer）
 */
@Component
public class MetricsCollector {

    private final MeterRegistry meterRegistry;

    // 计数器
    private final Counter requestsTotal;
    private final Counter requestsSuccess;
    private final Counter requestsError;
    private final Counter toolCallsTotal;
    private final Counter toolCallsSuccess;
    private final Counter toolCallsError;
    private final Counter modelCallsTotal;
    private final Counter modelCallsError;
    private final Counter tokensInputTotal;
    private final Counter tokensOutputTotal;
    private final Counter quotaExceededTotal;
    private final Counter permissionRequestsTotal;
    private final Counter permissionDeniedTotal;

    // 延迟指标
    private final Timer requestLatency;
    private final Timer toolCallLatency;
    private final Timer modelCallLatency;

    // 会话指标
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong totalUsers = new AtomicLong(0);

    // 按用户统计
    private final Map<String, UserMetrics> userMetrics = new ConcurrentHashMap<>();

    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 注册计数器
        requestsTotal = meterRegistry.counter("agent_requests_total");
        requestsSuccess = meterRegistry.counter("agent_requests_success");
        requestsError = meterRegistry.counter("agent_requests_error");

        toolCallsTotal = meterRegistry.counter("agent_tool_calls_total");
        toolCallsSuccess = meterRegistry.counter("agent_tool_calls_success");
        toolCallsError = meterRegistry.counter("agent_tool_calls_error");

        modelCallsTotal = meterRegistry.counter("agent_model_calls_total");
        modelCallsError = meterRegistry.counter("agent_model_calls_error");

        tokensInputTotal = meterRegistry.counter("agent_tokens_input_total");
        tokensOutputTotal = meterRegistry.counter("agent_tokens_output_total");

        quotaExceededTotal = meterRegistry.counter("agent_quota_exceeded_total");
        permissionRequestsTotal = meterRegistry.counter("agent_permission_requests_total");
        permissionDeniedTotal = meterRegistry.counter("agent_permission_denied_total");

        // 注册计时器
        requestLatency = meterRegistry.timer("agent_request_latency");
        toolCallLatency = meterRegistry.timer("agent_tool_call_latency");
        modelCallLatency = meterRegistry.timer("agent_model_call_latency");

        // 注册 Gauge
        Gauge.builder("agent_active_sessions", activeSessions, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("agent_total_users", totalUsers, AtomicLong::get)
                .register(meterRegistry);
    }

    /**
     * 记录请求
     */
    public void recordRequest(String userId, boolean success) {
        requestsTotal.increment();
        if (success) {
            requestsSuccess.increment();
        } else {
            requestsError.increment();
        }
        getUserMetrics(userId).requestsTotal.incrementAndGet();
    }

    /**
     * 记录请求延迟
     */
    public void recordRequestLatency(String userId, Duration latency) {
        requestLatency.record(latency);
        getUserMetrics(userId).requestLatencyMs.addAndGet(latency.toMillis());
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String userId, String toolName, boolean success, Duration latency) {
        toolCallsTotal.increment();
        if (success) {
            toolCallsSuccess.increment();
        } else {
            toolCallsError.increment();
        }
        toolCallLatency.record(latency);
        getUserMetrics(userId).toolCallsTotal.incrementAndGet();
    }

    /**
     * 记录模型调用
     */
    public void recordModelCall(String userId, String model, boolean success, Duration latency,
                                 TokenCounts tokenCounts) {
        modelCallsTotal.increment();
        if (!success) {
            modelCallsError.increment();
        }
        modelCallLatency.record(latency);

        if (tokenCounts != null) {
            tokensInputTotal.increment(tokenCounts.inputTokens());
            tokensOutputTotal.increment(tokenCounts.outputTokens());
            getUserMetrics(userId).tokensUsed.addAndGet(
                    tokenCounts.inputTokens() + tokenCounts.outputTokens());
        }
    }

    /**
     * 记录配额超出
     */
    public void recordQuotaExceeded(String userId, String quotaType) {
        quotaExceededTotal.increment();
        getUserMetrics(userId).quotaExceededCount.incrementAndGet();
    }

    /**
     * 记录权限请求
     */
    public void recordPermissionRequest(String userId, boolean granted) {
        permissionRequestsTotal.increment();
        if (!granted) {
            permissionDeniedTotal.increment();
        }
    }

    /**
     * 增加活跃 Session 数
     */
    public void incrementActiveSessions() {
        activeSessions.incrementAndGet();
    }

    /**
     * 减少活跃 Session 数
     */
    public void decrementActiveSessions() {
        activeSessions.decrementAndGet();
    }

    /**
     * 更新总用户数
     */
    public void setTotalUsers(long count) {
        totalUsers.set(count);
    }

    /**
     * 获取用户指标
     */
    public UserMetrics getUserMetrics(String userId) {
        if (userId == null) {
            return UserMetrics.ANONYMOUS;
        }
        return userMetrics.computeIfAbsent(userId, k -> new UserMetrics());
    }

    /**
     * 用户级别指标
     */
    public static class UserMetrics {
        public final AtomicLong requestsTotal = new AtomicLong(0);
        public final AtomicLong requestLatencyMs = new AtomicLong(0);
        public final AtomicLong toolCallsTotal = new AtomicLong(0);
        public final AtomicLong tokensUsed = new AtomicLong(0);
        public final AtomicLong quotaExceededCount = new AtomicLong(0);

        public static final UserMetrics ANONYMOUS = new UserMetrics();
    }
}
