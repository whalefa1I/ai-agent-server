package demo.k8s.agent.subagent.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SubagentMetrics} 单元测试。
 */
@DisplayName("子 Agent 指标测试")
class SubagentMetricsTest {

    private MeterRegistry meterRegistry;
    private SubagentMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new SubagentMetrics(meterRegistry);
    }

    @Test
    @DisplayName("记录派生被拒绝")
    void recordSpawnRejected() {
        metrics.recordSpawnRejected("depth_exceeded");

        var counter = meterRegistry.find("subagent.spawn.rejected")
                .tag("reason", "gatekeeper")
                .counter();

        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    @DisplayName("记录派生成功")
    void recordSpawnSuccess() {
        metrics.recordSpawnSuccess("run-123");

        var counter = meterRegistry.find("subagent.spawn.success").counter();

        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    @DisplayName("记录超时")
    void recordTimeout() {
        metrics.recordTimeout();

        var counter = meterRegistry.find("subagent.wallclock.timeout").counter();

        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    @DisplayName("记录 ContextObject 写入失败")
    void recordContextObjectWriteFailure() {
        metrics.recordContextObjectWriteFailure();

        var counter = meterRegistry.find("context_object.write.failure").counter();

        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    @DisplayName("记录 ContextObject 读取字节")
    void recordContextObjectRead() {
        metrics.recordContextObjectRead(1024);

        var counter = meterRegistry.find("context_object.read.bytes").counter();

        assertNotNull(counter);
        assertEquals(1024, counter.count());
    }

    @Test
    @DisplayName("记录恢复超时")
    void recordReconcileTimeout() {
        metrics.recordReconcileTimeout();

        var counter = meterRegistry.find("subagent.reconcile.timeout").counter();

        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    @DisplayName("记录恢复保留")
    void recordReconcilePreserved() {
        metrics.recordReconcilePreserved();

        var counter = meterRegistry.find("subagent.reconcile.preserved").counter();

        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    @DisplayName("设置活跃任务数")
    void setActiveRuns() {
        metrics.setActiveRuns("session-1", 5);
        metrics.setActiveRuns("session-2", 3);

        var gauge = meterRegistry.find("subagent.active.runs").gauge();

        assertNotNull(gauge);
        assertEquals(8, gauge.value());
    }

    @Test
    @DisplayName("清理会话指标")
    void cleanupSession() {
        metrics.setActiveRuns("session-1", 5);
        metrics.cleanupSession("session-1");

        var gauge = meterRegistry.find("subagent.active.runs").gauge();

        assertNotNull(gauge);
        assertEquals(0, gauge.value());
    }

    @Test
    @DisplayName("记录派生耗时")
    void recordSpawnDuration() {
        String result = metrics.recordSpawnDuration(() -> "test");

        assertEquals("test", result);

        var timer = meterRegistry.find("subagent.spawn.duration").timer();

        assertNotNull(timer);
        assertEquals(1, timer.count());
    }
}
