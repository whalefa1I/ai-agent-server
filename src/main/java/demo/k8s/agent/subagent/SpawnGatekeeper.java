package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static demo.k8s.agent.subagent.SpawnResult.MustDoNext;

/**
 * 子 Agent 派生门控（v1 M3 实现）。
 * <p>
 * 统一执行深度、并发、TTL、工具权限校验，拒绝时返回结构化 {@link MustDoNext}。
 * <p>
 * 并发限制通过 {@link #tryAcquireConcurrentSlot} / {@link #releaseConcurrentSlot} 与
 * {@link MultiAgentFacade} 配合，在真正派生前原子占位，避免仅“读计数”导致的竞态。
 */
@Component
public class SpawnGatekeeper {

    private static final Logger log = LoggerFactory.getLogger(SpawnGatekeeper.class);

    private final DemoMultiAgentProperties props;

    /**
     * 会话维度的嵌套深度（由 {@link #onSpawnStart}/{@link #onSpawnEnd} 维护）
     */
    private final ConcurrentHashMap<String, AtomicInteger> sessionDepthCounter = new ConcurrentHashMap<>();

    /**
     * 会话维度的并发占位（tryAcquire 成功 +1，release/onSpawnEnd 配对 -1）
     */
    private final ConcurrentHashMap<String, AtomicInteger> sessionConcurrentCounter = new ConcurrentHashMap<>();

    public SpawnGatekeeper(DemoMultiAgentProperties props) {
        this.props = props;
    }

    /**
     * 检查是否允许派生新的子 Agent（深度、工具白名单；不含并发占位）。
     */
    public MustDoNext checkSpawn(String sessionId, int currentDepth, Set<String> allowedTools) {
        if (currentDepth >= props.getMaxSpawnDepth()) {
            log.info("[Gatekeeper] Depth limit exceeded: current={}, max={}", currentDepth, props.getMaxSpawnDepth());
            return MustDoNext.simplify("Maximum spawn depth (" + props.getMaxSpawnDepth() + ") reached. Complete current subtasks first or simplify the request.");
        }

        Set<String> globalSafeTools = getGlobalSafeTools();
        for (String tool : allowedTools) {
            if (!globalSafeTools.contains(tool)) {
                log.warn("[Gatekeeper] Tool not in allowed scope: tool={}", tool);
                return MustDoNext.simplify("Tool '" + tool + "' is not available in this context. Remove it from your request.");
            }
        }
        return null;
    }

    /**
     * 原子占用一个并发槽位；失败时返回结构化拒绝（调用方应拒绝派生且不得启动运行时）。
     */
    public MustDoNext tryAcquireConcurrentSlot(String sessionId) {
        int max = props.getMaxConcurrentSpawns();
        AtomicInteger c = sessionConcurrentCounter.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
        while (true) {
            int v = c.get();
            if (v >= max) {
                log.info("[Gatekeeper] Concurrent limit exceeded: current={}, max={}", v, max);
                return MustDoNext.simplify("Too many concurrent subtasks (" + v + "/" + max + "). Wait for existing tasks to complete.");
            }
            if (c.compareAndSet(v, v + 1)) {
                return null;
            }
        }
    }

    /**
     * 释放一个并发槽位（与 {@link #tryAcquireConcurrentSlot} 配对；可安全多次调用，计数不低于 0）。
     */
    public void releaseConcurrentSlot(String sessionId) {
        AtomicInteger c = sessionConcurrentCounter.get(sessionId);
        if (c == null) {
            return;
        }
        while (true) {
            int v = c.get();
            if (v <= 0) {
                return;
            }
            if (c.compareAndSet(v, v - 1)) {
                return;
            }
        }
    }

    /**
     * 子任务实际开始执行时增加嵌套深度（由运行时在线程内调用，与 tryAcquire 分离）。
     */
    public void onSpawnStart(String sessionId, String runId) {
        sessionDepthCounter.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).incrementAndGet();
        log.debug("[Gatekeeper] Spawn started: sessionId={}, runId={}", sessionId, runId);
    }

    /**
     * 子任务结束：减少嵌套深度并释放并发槽位（与 tryAcquire 配对）。
     */
    public void onSpawnEnd(String sessionId, String runId) {
        AtomicInteger d = sessionDepthCounter.get(sessionId);
        if (d != null) {
            while (true) {
                int v = d.get();
                if (v <= 0) {
                    break;
                }
                if (d.compareAndSet(v, v - 1)) {
                    break;
                }
            }
        }
        releaseConcurrentSlot(sessionId);
        log.debug("[Gatekeeper] Spawn ended: sessionId={}, runId={}", sessionId, runId);
    }

    public Instant calculateDeadline() {
        return Instant.now().plusSeconds(props.getWallclockTtlSeconds());
    }

    private Set<String> getGlobalSafeTools() {
        // v1：与 LocalToolRegistry / 配置对齐前，先保持显式白名单；后续改为配置注入
        return Set.of(
                "file_read", "file_write", "file_edit",
                "glob", "grep", "bash",
                "read_context_object",
                "web_fetch", "web_search"
        );
    }

    /**
     * 与门控白名单一致的工具名集合，供 {@link MultiAgentFacade} / TaskCreate 路由传入 allowedTools。
     */
    public Set<String> globalSafeToolNames() {
        return getGlobalSafeTools();
    }

    public void cleanupSession(String sessionId) {
        sessionDepthCounter.remove(sessionId);
        sessionConcurrentCounter.remove(sessionId);
        log.debug("[Gatekeeper] Session cleanup: sessionId={}", sessionId);
    }

    public SessionStats getSessionStats(String sessionId) {
        AtomicInteger depth = sessionDepthCounter.get(sessionId);
        AtomicInteger concurrent = sessionConcurrentCounter.get(sessionId);
        return new SessionStats(
                depth != null ? depth.get() : 0,
                concurrent != null ? concurrent.get() : 0
        );
    }

    public record SessionStats(int depth, int concurrent) {}
}
