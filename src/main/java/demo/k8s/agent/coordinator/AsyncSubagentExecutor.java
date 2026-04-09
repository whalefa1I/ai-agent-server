package demo.k8s.agent.coordinator;

import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.SpawnPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 子 Agent <strong>Worker 引擎</strong>：驱动 {@link CoordinatorState} 与底层 Worker 循环。
 * <p>
 * <strong>编排唯一入口</strong>为 {@link demo.k8s.agent.subagent.MultiAgentFacade}（门控、{@code SubagentRun}、模式开关）。
 * 本类仅供 {@link demo.k8s.agent.subagent.SubAgentRuntime} 实现（当前为 {@link demo.k8s.agent.subagent.LocalSubAgentRuntime}）注入调用；
 * 禁止再新增 Spring Bean、工具类或 HTTP 层对其直连，以免绕过 Gatekeeper 与持久化语义。
 * <p>
 * 能力划分与 {@link SpawnPath} 对齐（由 Runtime 选择调用方式）：
 * <ul>
 *   <li>{@link SpawnPath#SYNCHRONOUS_TYPED_AGENT} — {@link #runSynchronousAgent}</li>
 *   <li>{@link SpawnPath#ASYNC_BACKGROUND} — 后台 API（预留由 Runtime/Facade 扩展时调用）</li>
 *   <li>{@link SpawnPath#TEAMMATE}、{@link SpawnPath#FORK_CACHE_ALIGNED} — 预留</li>
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
            t.setName("subagent-" + t.threadId());
            t.setDaemon(true);
            return t;
        });
        log.info("[SubagentExec] CoordinatorState wired: instanceId={}",
                System.identityHashCode(coordinatorState));
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

        TraceContext.TraceInfo callerTrace = TraceContext.getTraceInfo();

        // 创建任务
        CoordinatorState.TaskHandle handle = coordinatorState.createTask(name, goal, agentType);
        log.info("[SubagentExec] task created: taskId={}, agentType={}, sessionId={}, traceId={}",
                handle.taskId(), agentType, callerTrace != null ? callerTrace.sessionId() : null,
                callerTrace != null ? callerTrace.traceId() : null);

        // 异步执行 Worker（在子线程恢复 callerTrace，使 Worker 内工具回调与主会话一致）
        CompletableFuture<CoordinatorState.TaskResult> future =
                runBackgroundAgentInternal(handle.taskId(), agentType, callerTrace);

        // 阻塞等待完成
        try {
            CoordinatorState.TaskResult result = future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            log.info("同步子 Agent 完成：{} - status={}", handle.taskId(),
                    result.error() != null ? "FAILED" : "COMPLETED");
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            coordinatorState.failTask(handle.taskId(), "timeout");
            log.warn("[SubagentExec] synchronous worker timeout: taskId={}, agentType={}, timeoutSec={}",
                    handle.taskId(), agentType, timeout.toSeconds());
            throw new Exception("子 Agent 超时：" + handle.taskId(), e);
        } catch (java.util.concurrent.ExecutionException e) {
            coordinatorState.failTask(handle.taskId(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            log.error("[SubagentExec] synchronous worker failed: taskId={}, agentType={}, err={}",
                    handle.taskId(), agentType,
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
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
        log.info("[SubagentExec] background task created: taskId={}, agentType={}", handle.taskId(), agentType);

        // 异步执行
        runBackgroundAgentInternal(handle.taskId(), agentType, null);

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
        log.info("[SubagentExec] background-async task created: taskId={}, agentType={}", handle.taskId(), agentType);
        return runBackgroundAgentInternal(handle.taskId(), agentType, null);
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
        runBackgroundAgentInternal(handle.taskId(), agentType, null);

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
        runBackgroundAgentInternal(handle.taskId(), handle.taskId(), null);

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
            String agentType,
            TraceContext.TraceInfo parentTrace) {

        CompletableFuture<CoordinatorState.TaskResult> future = new CompletableFuture<>();

        executorService.submit(() -> {
            try {
                applyTraceSnapshotForWorker(parentTrace);
                workerExecutor.executeWorker(taskId, agentType);

                CoordinatorState.TaskResult result = coordinatorState.waitForTask(
                        taskId,
                        Duration.ofMinutes(30)); // 默认 30 分钟超时

                future.complete(result);
            } catch (Exception e) {
                log.error("子 Agent 执行失败：taskId={}", taskId, e);
                coordinatorState.failTask(taskId, e.getMessage());
                future.completeExceptionally(e);
            } finally {
                TraceContext.clear();
            }
        });

        return future;
    }

    /**
     * 在 Worker 线程恢复调用方追踪快照（通常为 {@link demo.k8s.agent.subagent.LocalSubAgentRuntime} 已绑定的会话）。
     */
    private static void applyTraceSnapshotForWorker(TraceContext.TraceInfo snap) {
        if (snap == null) {
            TraceContext.init(TraceContext.generateTraceId(), TraceContext.generateSpanId());
            return;
        }
        String tid = snap.traceId() != null && !snap.traceId().isBlank()
                ? snap.traceId()
                : TraceContext.generateTraceId();
        TraceContext.init(tid, TraceContext.generateSpanId());
        if (snap.requestId() != null && !snap.requestId().isBlank()) {
            TraceContext.setRequestId(snap.requestId());
        }
        if (snap.sessionId() != null && !snap.sessionId().isBlank()) {
            TraceContext.setSessionId(snap.sessionId());
        }
        if (snap.userId() != null && !snap.userId().isBlank()) {
            TraceContext.setUserId(snap.userId());
        }
        if (snap.tenantId() != null && !snap.tenantId().isBlank()) {
            TraceContext.setTenantId(snap.tenantId());
        }
        if (snap.appId() != null && !snap.appId().isBlank()) {
            TraceContext.setAppId(snap.appId());
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
