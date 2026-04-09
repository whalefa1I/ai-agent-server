package demo.k8s.agent.subagent;

import java.util.concurrent.CompletableFuture;

/**
 * 子 Agent 运行时抽象（v1 M4 契约冻结）。
 * <p>
 * 封装子 Agent 执行位置（本地/远端），Facade 只依赖此接口，不感知具体实现。
 */
public interface SubAgentRuntime {

    /**
     * 派生新的子 Agent 任务。
     *
     * @param request 派生请求
     * @return 派生结果 Future
     */
    CompletableFuture<SpawnResult> spawn(SpawnRequest request);

    /**
     * 取消正在运行的子任务。
     *
     * @param runId 运行 ID
     * @return 是否成功取消
     */
    boolean cancel(String runId);

    /**
     * 获取子任务状态。
     *
     * @param runId 运行 ID
     * @return 子任务状态，不存在时返回 null
     */
    SubRunEvent getStatus(String runId);

    /**
     * 运行时类型标识（用于可观测性）。
     *
     * @return "local" 或 "remote"
     */
    String getRuntimeType();
}
