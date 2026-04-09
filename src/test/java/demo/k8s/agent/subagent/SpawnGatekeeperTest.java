package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.toolsystem.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * {@link SpawnGatekeeper} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("子 Agent 门控测试")
class SpawnGatekeeperTest {

    @Mock
    private DemoMultiAgentProperties props;
    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private ObjectProvider<ToolRegistry> toolRegistryProvider;

    private SpawnGatekeeper gatekeeper;

    @BeforeEach
    void setUp() {
        gatekeeper = new SpawnGatekeeper(props, toolRegistryProvider);
        when(toolRegistryProvider.getIfAvailable()).thenReturn(toolRegistry);
        when(toolRegistry.getAllToolNames()).thenReturn(Set.of("file_read", "task"));
    }

    @Test
    @DisplayName("超过深度限制时拒绝")
    void checkAndAcquire_whenDepthExceeded_rejects() {
        when(props.getMaxSpawnDepth()).thenReturn(3);

        SpawnResult.MustDoNext result = gatekeeper.checkAndAcquire("session-1", 3, Set.of("file_read"));

        assertNotNull(result);
        assertEquals(SpawnResult.MustDoNext.Action.SIMPLIFY, result.action());
    }

    @Test
    @DisplayName("并发槽占满时 checkAndAcquire 拒绝")
    void checkAndAcquire_whenConcurrentSlotsFull_rejects() {
        when(props.getMaxSpawnDepth()).thenReturn(5);
        when(props.getMaxConcurrentSpawns()).thenReturn(1);

        assertNull(gatekeeper.checkAndAcquire("session-1", 0, Set.of("file_read")));

        SpawnResult.MustDoNext result = gatekeeper.checkAndAcquire("session-1", 0, Set.of("file_read"));

        assertNotNull(result);
        assertEquals(SpawnResult.MustDoNext.Action.SIMPLIFY, result.action());
    }

    @Test
    @DisplayName("工具不在白名单时拒绝")
    void checkAndAcquire_whenToolNotAllowed_rejects() {
        when(props.getMaxSpawnDepth()).thenReturn(5);
        when(props.getMaxConcurrentSpawns()).thenReturn(5);

        SpawnResult.MustDoNext result = gatekeeper.checkAndAcquire("session-1", 0, Set.of("dangerous_tool"));

        assertNotNull(result);
        assertEquals(SpawnResult.MustDoNext.Action.SIMPLIFY, result.action());
    }

    @Test
    @DisplayName("检查通过时返回 null")
    void checkAndAcquire_whenAllChecksPass_returnsNull() {
        when(props.getMaxSpawnDepth()).thenReturn(5);
        when(props.getMaxConcurrentSpawns()).thenReturn(5);

        SpawnResult.MustDoNext result = gatekeeper.checkAndAcquire("session-1", 0, Set.of("file_read"));

        assertNull(result);
    }

    @Test
    @DisplayName("onSpawnStart 仅增加深度计数")
    void onSpawnStart_incrementsDepthOnly() {
        String sessionId = "session-1";
        when(props.getMaxConcurrentSpawns()).thenReturn(10);
        when(props.getMaxSpawnDepth()).thenReturn(10);
        assertNull(gatekeeper.checkAndAcquire(sessionId, 0, Set.of("file_read")));

        gatekeeper.onSpawnStart(sessionId, "run-1");
        gatekeeper.onSpawnStart(sessionId, "run-2");

        SpawnGatekeeper.SessionStats stats = gatekeeper.getSessionStats(sessionId);

        assertEquals(2, stats.depth());
        assertEquals(1, stats.concurrent());

        gatekeeper.onSpawnEnd(sessionId, "run-2");
        gatekeeper.onSpawnEnd(sessionId, "run-1");

        SpawnGatekeeper.SessionStats after = gatekeeper.getSessionStats(sessionId);
        assertEquals(0, after.depth());
        assertEquals(0, after.concurrent());
    }

    @Test
    @DisplayName("releaseConcurrentSlot 可安全多次调用")
    void releaseConcurrentSlot_neverNegative() {
        gatekeeper.releaseConcurrentSlot("session-1");
        gatekeeper.releaseConcurrentSlot("session-1");
        assertEquals(0, gatekeeper.getSessionStats("session-1").concurrent());
    }

    @Test
    @DisplayName("cleanupSession 清理计数")
    void cleanupSession_clearsCounters() {
        String sessionId = "session-1";
        when(props.getMaxConcurrentSpawns()).thenReturn(5);
        when(props.getMaxSpawnDepth()).thenReturn(5);
        assertNull(gatekeeper.checkAndAcquire(sessionId, 0, Set.of("file_read")));
        gatekeeper.onSpawnStart(sessionId, "run-1");
        gatekeeper.cleanupSession(sessionId);

        SpawnGatekeeper.SessionStats stats = gatekeeper.getSessionStats(sessionId);

        assertEquals(0, stats.depth());
        assertEquals(0, stats.concurrent());
    }

    @Test
    @DisplayName("多线程下 checkAndAcquire 不超过上限")
    void checkAndAcquire_underConcurrency_respectsMax() throws Exception {
        when(props.getMaxSpawnDepth()).thenReturn(10);
        when(props.getMaxConcurrentSpawns()).thenReturn(3);
        String sessionId = "session-1";
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger rejects = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        SpawnResult.MustDoNext r = gatekeeper.checkAndAcquire(sessionId, 0, Set.of("file_read"));
                        if (r != null) {
                            rejects.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(threads - 3, rejects.get());
        assertEquals(3, gatekeeper.getSessionStats(sessionId).concurrent());
    }
}
