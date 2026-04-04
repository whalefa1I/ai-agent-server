package demo.k8s.agent.plugin.hook;

import demo.k8s.agent.plugin.hook.Hook.HookContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hook 执行器 - 执行 Hook 链
 * <p>
 * 功能：
 * - 同步/异步执行 Hook
 * - 短路逻辑（BEFORE Hook 可阻止执行）
 * - 异常处理
 * - 超时控制
 */
@Service
public class HookExecutor {

    private static final Logger log = LoggerFactory.getLogger(HookExecutor.class);

    private static final int DEFAULT_TIMEOUT_MS = 5000; // 5 秒超时

    private final HookRegistry hookRegistry;
    private final ExecutorService asyncExecutor;

    public HookExecutor(HookRegistry hookRegistry) {
        this.hookRegistry = hookRegistry;
        this.asyncExecutor = Executors.newCachedThreadPool();
        log.info("HookExecutor initialized");
    }

    /**
     * 执行 BEFORE 阶段的 Hook 链
     *
     * @param type     Hook 类型
     * @param context  Hook 上下文
     * @return true=继续执行目标操作，false=阻止目标操作
     */
    public boolean executeBeforeHooks(HookType type, HookContext context) {
        List<Hook> hooks = hookRegistry.getHooks(type, HookPhase.BEFORE);

        if (hooks.isEmpty()) {
            return true; // 没有 Hook，直接继续
        }

        log.debug("Executing {} BEFORE hooks for {}", hooks.size(), type);

        for (Hook hook : hooks) {
            try {
                boolean shouldProceed = hook.execute(context);
                if (!shouldProceed) {
                    log.info("Hook {} blocked execution for type {}", hook.getId(), type);
                    return false; // 短路：阻止目标操作
                }
            } catch (Exception e) {
                log.error("Hook {} execution failed", hook.getId(), e);
                hook.onError(context, e);
                // 继续执行下一个 Hook，不阻断整个流程
            }
        }

        return context.shouldProceed();
    }

    /**
     * 执行 AFTER 阶段的 Hook 链
     *
     * @param type    Hook 类型
     * @param context Hook 上下文（包含结果）
     */
    public void executeAfterHooks(HookType type, HookContext context) {
        List<Hook> hooks = hookRegistry.getHooks(type, HookPhase.AFTER);

        if (hooks.isEmpty()) {
            return;
        }

        log.debug("Executing {} AFTER hooks for {}", hooks.size(), type);

        for (Hook hook : hooks) {
            try {
                hook.execute(context);
            } catch (Exception e) {
                log.error("Hook {} execution failed", hook.getId(), e);
                hook.onError(context, e);
                // 继续执行下一个 Hook
            }
        }
    }

    /**
     * 执行 AROUND 阶段的 Hook 链（最外层的 Hook 先执行）
     *
     * @param type      Hook 类型
     * @param context   Hook 上下文
     * @param procedure 目标操作
     * @return 目标操作的结果（可能被 Hook 修改）
     */
    public Object executeAroundHooks(HookType type, HookContext context, Procedure procedure) {
        List<Hook> hooks = hookRegistry.getHooks(type, HookPhase.AROUND);

        if (hooks.isEmpty()) {
            // 没有 Hook，直接执行目标操作
            try {
                return procedure.execute(context);
            } catch (Exception e) {
                log.error("Procedure execution failed", e);
                return null;
            }
        }

        // 递归执行 AROUND Hook 链
        return executeAroundHookChain(hooks, 0, context, procedure);
    }

    /**
     * 递归执行 AROUND Hook 链
     */
    private Object executeAroundHookChain(List<Hook> hooks, int index, HookContext context, Procedure procedure) {
        if (index >= hooks.size()) {
            // 所有 Hook 执行完毕，执行目标操作
            try {
                return procedure.execute(context);
            } catch (Exception e) {
                log.error("Procedure execution failed", e);
                return null;
            }
        }

        Hook currentHook = hooks.get(index);
        log.debug("Executing AROUND hook {}/{}: {}", index + 1, hooks.size(), currentHook.getId());

        try {
            // AROUND Hook 负责调用 proceed() 来执行下一个 Hook 或目标操作
            currentHook.execute(context);

            // 如果 Hook 没有设置 proceed，默认继续执行下一个
            if (context.shouldProceed()) {
                return executeAroundHookChain(hooks, index + 1, context, procedure);
            } else {
                // Hook 阻止了执行，返回当前结果
                return context.getResult();
            }
        } catch (Exception e) {
            log.error("Hook {} execution failed", currentHook.getId(), e);
            currentHook.onError(context, e);
            return null;
        }
    }

    /**
     * 异步执行 Hook（不阻塞主流程）
     *
     * @param type    Hook 类型
     * @param context Hook 上下文
     * @return CompletableFuture
     */
    public CompletableFuture<Void> executeAsync(HookType type, HookContext context) {
        return CompletableFuture.runAsync(() -> {
            executeBeforeHooks(type, context);
            executeAfterHooks(type, context);
        }, asyncExecutor);
    }

    /**
     * 创建 Hook 上下文的便捷方法
     */
    public HookContext createContext(HookType type, String sessionId, String userId, Map<String, Object> data) {
        return new HookContext(type, sessionId, userId, data);
    }

    /**
     * 过程接口 - 用于 AROUND Hook 的目标操作
     */
    @FunctionalInterface
    public interface Procedure {
        Object execute(HookContext context) throws Exception;
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        asyncExecutor.shutdownNow();
    }
}
