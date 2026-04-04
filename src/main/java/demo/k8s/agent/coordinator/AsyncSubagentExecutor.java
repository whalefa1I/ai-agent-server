package demo.k8s.agent.coordinator;

import demo.k8s.agent.subagent.SpawnPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步子 Agent 执行器，与 Claude Code 的 AgentTool 派发路径对齐。
 * <p>
 * 支持完整的 {@link SpawnPath}：
 * <ul>
 *   <li>{@link SpawnPath#SYNCHRONOUS_TYPED_AGENT} - 阻塞父回合直到子 Agent 结束</li>
 *   <li>{@link SpawnPath#ASYNC_BACKGROUND} - 后台启动子 Agent，立即返回 TaskHandle</li>
 *   <li>{@link SpawnPath#TEAMMATE} - 队友/多 Agent 终端派发（预留）</li>
 *   <li>{@link SpawnPath#FORK_CACHE_ALIGNED} - fork 实验路径（预留）</li>
 * </ul>
 */
@Service
public class AsyncSubagentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncSubagentExecutor.class);

    private final CoordinatorState coordinatorState;
    private final WorkerAgentExecutor workerExecutor;
    private final ExecutorService executorService;

    public AsyncSubagentExecutor(
            CoordinatorState coordinatorState,
            WorkerAgentExecutor workerExecutor) {
        this.coordinatorState = coordinatorState;
        this.workerExecutor = workerExecutor;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("subagent-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 同步执行子 Agent（阻塞直到完成）
     * <p>
     * 对应 {@link SpawnPath#SYNCHRONOUS_TYPED_AGENT}
     *
     * @param name 任务名称
     * @param goal 任务目标
     * @param agentType Agent 类型（general/explore/plan/bash）
     * @param timeout 超时时间
     * @return 任务结果
     * @throws Exception 超时或执行失败时抛出
     */
    public CoordinatorState.TaskResult runSynchronousAgent(
            String name,
            String goal,
            String agentType,
            Duration timeout) throws Exception {

        log.info("启动同步子 Agent: name={}, goal={}, agentType={}", name, truncate(goal, 50), agentType);

        // 创建任务
        CoordinatorState.TaskHandle handle = coordinatorState.createTask(name, goal, agentType);

        // 异步执行 Worker
        CompletableFuture<CoordinatorState.TaskResult> future =
                runBackgroundAgentInternal(handle.taskId(), agentType);

        // 阻塞等待完成
        try {
            CoordinatorState.TaskResult result = future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            log.info("同步子 Agent 完成：{} - status={}", handle.taskId(),
                    result.error() != null ? "FAILED" : "COMPLETED");
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            coordinatorState.failTask(handle.taskId(), "timeout");
            throw new Exception("子 Agent 超时：" + handle.taskId(), e);
        } catch (java.util.concurrent.ExecutionException e) {
            coordinatorState.failTask(handle.taskId(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            throw new Exception("子 Agent 执行失败：" + handle.taskId(), e);
        }
    }

    /**
     * 后台启动子 Agent（立即返回 TaskHandle）
     * <p>
     * 对应 {@link SpawnPath#ASYNC_BACKGROUND}
     *
     * @param name 任务名称
     * @param goal 任务目标
     * @param agentType Agent 类型
     * @return TaskHandle
     */
    public CoordinatorState.TaskHandle spawnBackgroundAgent(
            String name,
            String goal,
            String agentType) {

        log.info("后台启动子 Agent: name={}, goal={}, agentType={}", name, truncate(goal, 50), agentType);

        // 创建任务
        CoordinatorState.TaskHandle handle = coordinatorState.createTask(name, goal, agentType);

        // 异步执行
        runBackgroundAgentInternal(handle.taskId(), agentType);

        return handle;
    }

    /**
     * 后台启动子 Agent 并返回 CompletableFuture
     *
     * @param name 任务名称
     * @param goal 任务目标
     * @param agentType Agent 类型
     * @return CompletableFuture<TaskResult>
     */
    public CompletableFuture<CoordinatorState.TaskResult> spawnBackgroundAgentAsync(
            String name,
            String goal,
            String agentType) {

        log.info("后台启动子 Agent（异步）: name={}, goal={}, agentType={}", name, truncate(goal, 50), agentType);

        CoordinatorState.TaskHandle handle = coordinatorState.createTask(name, goal, agentType);
        return runBackgroundAgentInternal(handle.taskId(), agentType);
    }

    /**
     * 派发队友 Agent（多 Agent 协作）
     * <p>
     * 对应 {@link SpawnPath#TEAMMATE}
     *
     * @param teammateName 队友名称
     * @param goal 任务目标
     * @param teamName 团队名称
     * @return TaskHandle
     */
    public CoordinatorState.TaskHandle spawnTeammateAgent(
            String teammateName,
            String goal,
            String teamName) {

        log.info("启动队友 Agent: teammate={}, goal={}, team={}", teammateName, truncate(goal, 50), teamName);

        // 使用队友名称作为 agentType
        String agentType = "teammate_" + teammateName;
        CoordinatorState.TaskHandle handle = coordinatorState.createTask(
                teammateName,
                goal,
                agentType);

        // 启动 Worker
        runBackgroundAgentInternal(handle.taskId(), agentType);

        return handle;
    }

    /**
     * Fork 子 Agent（实验性，提示缓存对齐）
     * <p>
     * 对应 {@link SpawnPath#FORK_CACHE_ALIGNED}
     *
     * @param goal 任务目标
     * @param useExactTools 是否使用精确工具集
     * @return TaskHandle
     */
    public CoordinatorState.TaskHandle spawnForkAgent(
            String goal,
            boolean useExactTools) {

        log.info("Fork 子 Agent: goal={}, useExactTools={}", truncate(goal, 50), useExactTools);

        CoordinatorState.TaskHandle handle = coordinatorState.createTask(
                "fork-agent",
                goal,
                "fork" + (useExactTools ? "_exact" : ""));

        // 启动 Worker
        runBackgroundAgentInternal(handle.taskId(), handle.taskId());

        return handle;
    }

    /**
     * 发送消息给子 Agent
     */
    public void sendMessage(String taskId, String message) {
        log.debug("发送消息给子 Agent {}: {}", taskId, truncate(message, 50));
        coordinatorState.sendMessage(taskId, message);
    }

    /**
     * 停止子 Agent
     */
    public void stopAgent(String taskId) {
        log.info("停止子 Agent: {}", taskId);
        coordinatorState.stopTask(taskId);
    }

    /**
     * 等待子 Agent 完成
     */
    public CoordinatorState.TaskResult waitForAgent(String taskId, Duration timeout) throws Exception {
        try {
            return coordinatorState.waitForTask(taskId, timeout);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new Exception("等待子 Agent 超时：" + taskId, e);
        }
    }

    /**
     * 异步等待子 Agent 完成
     */
    public CompletableFuture<CoordinatorState.TaskResult> waitForAgentAsync(String taskId) {
        return coordinatorState.waitForTaskAsync(taskId);
    }

    /**
     * 获取子 Agent 状态
     */
    public java.util.Optional<demo.k8s.agent.coordinator.TaskState> getAgentStatus(String taskId) {
        return coordinatorState.getTask(taskId);
    }

    /**
     * 获取所有活跃子 Agent
     */
    public java.util.List<demo.k8s.agent.coordinator.TaskState> getActiveAgents() {
        return coordinatorState.getActiveTasks();
    }

    /**
     * 获取协调器统计
     */
    public CoordinatorState.CoordinatorStats getStats() {
        return coordinatorState.getStats();
    }

    // ===== 内部方法 =====

    private CompletableFuture<CoordinatorState.TaskResult> runBackgroundAgentInternal(
            String taskId,
            String agentType) {

        CompletableFuture<CoordinatorState.TaskResult> future = new CompletableFuture<>();

        executorService.submit(() -> {
            try {
                // 执行 Worker
                workerExecutor.executeWorker(taskId, agentType);

                // 等待任务完成
                CoordinatorState.TaskResult result = coordinatorState.waitForTask(
                        taskId,
                        Duration.ofMinutes(30)); // 默认 30 分钟超时

                future.complete(result);
            } catch (Exception e) {
                log.error("子 Agent 执行失败：taskId={}", taskId, e);
                coordinatorState.failTask(taskId, e.getMessage());
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
