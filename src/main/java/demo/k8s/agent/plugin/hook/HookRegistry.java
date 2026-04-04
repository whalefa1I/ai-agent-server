package demo.k8s.agent.plugin.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hook 注册表 - 管理所有已注册的 Hook
 * <p>
 * 功能：
 * - 注册/注销 Hook
 * - 按类型查询 Hook
 * - 按优先级排序 Hook 链
 */
@Service
public class HookRegistry {

    private static final Logger log = LoggerFactory.getLogger(HookRegistry.class);

    /**
     * 按 HookType 分组存储 Hook，使用 ConcurrentHashMap 支持并发注册
     */
    private final Map<HookType, List<Hook>> hooksByType = new ConcurrentHashMap<>();

    /**
     * 按 Hook ID 存储，用于快速查找和注销
     */
    private final Map<String, Hook> hooksById = new ConcurrentHashMap<>();

    /**
     * 注册一个 Hook
     *
     * @param hook 要注册的 Hook
     */
    public void register(Hook hook) {
        if (hook == null) {
            throw new IllegalArgumentException("Hook cannot be null");
        }

        String hookId = hook.getId();
        if (hooksById.containsKey(hookId)) {
            log.warn("Hook {} already registered, skipping", hookId);
            return;
        }

        // 添加到索引
        hooksById.put(hookId, hook);
        hooksByType
                .computeIfAbsent(hook.getType(), k -> new ArrayList<>())
                .add(hook);

        // 按优先级排序
        sortHooksByPriority(hook.getType());

        // 调用 Hook 的注册回调
        try {
            hook.onRegister();
        } catch (Exception e) {
            log.error("Hook onRegister callback failed: {}", hookId, e);
        }

        log.info("Registered hook: {} (type={}, phase={}, priority={})",
                hookId, hook.getType(), hook.getPhase(), hook.getPriority());
    }

    /**
     * 注销一个 Hook
     *
     * @param hookId Hook ID
     */
    public void unregister(String hookId) {
        Hook hook = hooksById.remove(hookId);
        if (hook != null) {
            List<Hook> typeHooks = hooksByType.get(hook.getType());
            if (typeHooks != null) {
                typeHooks.remove(hook);
            }

            // 调用 Hook 的注销回调
            try {
                hook.onUnregister();
            } catch (Exception e) {
                log.error("Hook onUnregister callback failed: {}", hookId, e);
            }

            log.info("Unregistered hook: {}", hookId);
        }
    }

    /**
     * 获取指定类型和阶段的所有 Hook（按优先级排序）
     *
     * @param type  Hook 类型
     * @param phase Hook 阶段
     * @return Hook 列表
     */
    public List<Hook> getHooks(HookType type, HookPhase phase) {
        List<Hook> typeHooks = hooksByType.getOrDefault(type, List.of());
        return typeHooks.stream()
                .filter(hook -> hook.getPhase() == phase)
                .toList();
    }

    /**
     * 获取指定类型的所有 Hook（按优先级排序，包含所有阶段）
     *
     * @param type Hook 类型
     * @return Hook 列表
     */
    public List<Hook> getHooks(HookType type) {
        return new ArrayList<>(hooksByType.getOrDefault(type, List.of()));
    }

    /**
     * 获取所有已注册的 Hook
     *
     * @return 所有 Hook
     */
    public List<Hook> getAllHooks() {
        return new ArrayList<>(hooksById.values());
    }

    /**
     * 按 ID 获取 Hook
     *
     * @param hookId Hook ID
     * @return Hook（如果存在）
     */
    public Hook getHook(String hookId) {
        return hooksById.get(hookId);
    }

    /**
     * 获取已注册 Hook 数量
     *
     * @return Hook 数量
     */
    public int size() {
        return hooksById.size();
    }

    /**
     * 清空所有 Hook
     */
    public void clear() {
        for (Hook hook : getAllHooks()) {
            try {
                hook.onUnregister();
            } catch (Exception e) {
                log.error("Hook onUnregister callback failed: {}", hook.getId(), e);
            }
        }
        hooksById.clear();
        hooksByType.clear();
        log.info("Cleared all hooks");
    }

    /**
     * 获取 Hook 注册统计信息
     *
     * @return 按类型分组的 Hook 数量
     */
    public Map<HookType, Integer> getHookStats() {
        return hooksByType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().size()
                ));
    }

    /**
     * 按优先级对 Hook 进行排序（数字越小优先级越高）
     */
    private void sortHooksByPriority(HookType type) {
        List<Hook> hooks = hooksByType.get(type);
        if (hooks != null) {
            hooks.sort(Comparator.comparingInt(Hook::getPriority));
        }
    }
}
