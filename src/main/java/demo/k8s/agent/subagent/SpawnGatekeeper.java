package demo.k8s.agent.subagent;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.toolsystem.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static demo.k8s.agent.subagent.SpawnResult.MustDoNext;

/**
 * 子 Agent 派生门控（v1 M3 实现，v2 兼容）。
 * <p>
 * 统一执行深度、并发、TTL、工具权限校验，拒绝时返回结构化 {@link MustDoNext}。
 * <p>
 * 并发限制通过 {@link #tryAcquireConcurrentSlot} / {@link #releaseConcurrentSlot} 与
 * {@link MultiAgentFacade} 配合，在真正派生前原子占位，避免仅"读计数"导致的竞态。
 * <p>
 * 工具白名单由 {@link demo.k8s.agent.toolsystem.ToolRegistry} 注入，支持租户维度的工具权限差异化。
 */
@Component
public class SpawnGatekeeper {

    private static final Logger log = LoggerFactory.getLogger(SpawnGatekeeper.class);

    private final DemoMultiAgentProperties props;
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;

    /**
     * 会话维度的嵌套深度（由 {@link #onSpawnStart}/{@link #onSpawnEnd} 维护）
     */
    private final ConcurrentHashMap<String, AtomicInteger> sessionDepthCounter = new ConcurrentHashMap<>();

    /**
     * 会话维度的并发占位（tryAcquire 成功 +1，release/onSpawnEnd 配对 -1）
     */
    private final ConcurrentHashMap<String, AtomicInteger> sessionConcurrentCounter = new ConcurrentHashMap<>();

    public SpawnGatekeeper(DemoMultiAgentProperties props, ObjectProvider<ToolRegistry> toolRegistryProvider) {
        this.props = props;
        this.toolRegistryProvider = toolRegistryProvider;
    }

    /**
     * 检查并原子占位（深度、并发、工具白名单三合一检查）。
     * <p>
     * 此方法合并了原有的 {@link #checkSpawn} 和 {@link #tryAcquireConcurrentSlot}，
     * 避免两者之间的竞态条件。
     *
     * @return 如果通过检查返回 {@code null}，否则返回结构化拒绝建议
     */
    public MustDoNext checkAndAcquire(String sessionId, int currentDepth, Set<String> allowedTools) {
        // 1. 深度检查
        if (currentDepth >= props.getMaxSpawnDepth()) {
            log.info("[Gatekeeper] Depth limit exceeded: current={}, max={}", currentDepth, props.getMaxSpawnDepth());
            return MustDoNext.simplify("Maximum spawn depth (" + props.getMaxSpawnDepth() + ") reached. Complete current subtasks first or simplify the request.");
        }

        // 2. 工具白名单检查（从 Registry 动态获取）
        Set<String> globalSafeTools = getGlobalSafeTools();
        for (String tool : allowedTools) {
            if (!globalSafeTools.contains(tool)) {
                log.warn("[Gatekeeper] Tool not in allowed scope: tool={}", tool);
                return MustDoNext.simplify("Tool '" + tool + "' is not available in this context. Remove it from your request.");
            }
        }

        // 3. 并发槽位原子占位
        int max = props.getMaxConcurrentSpawns();
        AtomicInteger c = sessionConcurrentCounter.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
        while (true) {
            int v = c.get();
            if (v >= max) {
                log.info("[Gatekeeper] Concurrent limit exceeded: current={}, max={}", v, max);
                return MustDoNext.simplify("Too many concurrent subtasks (" + v + "/" + max + "). Wait for existing tasks to complete.");
            }
            if (c.compareAndSet(v, v + 1)) {
                return null; // 检查全部通过
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

    /**
     * 获取全局安全工具集合（从 ToolRegistry 动态获取）。
     * <p>
     * 工具白名单由 ToolRegistry 动态提供，确保新增工具时不需要修改门控类。
     * v1: 返回所有已注册工具名称；未来可扩展为按租户/会话过滤的白名单子集。
     */
    private Set<String> getGlobalSafeTools() {
        ToolRegistry registry = toolRegistryProvider.getIfAvailable();
        if (registry == null) {
            // 容器早期阶段/异常场景兜底：不在创建期强依赖 ToolRegistry，避免循环依赖导致启动失败。
            log.warn("[Gatekeeper] ToolRegistry unavailable, fallback to conservative allowlist");
            return Set.of("TaskCreate", "TaskList", "TaskGet", "TaskUpdate", "TaskStop", "TaskOutput");
        }
        return registry.getAllToolNames();
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
