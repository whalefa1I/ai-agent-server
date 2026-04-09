package demo.k8s.agent.coordinator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import demo.k8s.agent.observability.tracing.TraceContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Coordinator 状态机，与 Claude Code 的 Coordinator 工具分区思路对齐。
 * <p>
 * 管理任务生命周期：
 * <ul>
 *   <li>{@link TaskStatus#PENDING} - 等待启动</li>
 *   <li>{@link TaskStatus#RUNNING} - 正在执行</li>
 *   <li>{@link TaskStatus#WAITING} - 等待用户输入或外部事件</li>
 *   <li>{@link TaskStatus#COMPLETED} - 已完成</li>
 *   <li>{@link TaskStatus#FAILED} - 失败</li>
 *   <li>{@link TaskStatus#STOPPED} - 已停止</li>
 * </ul>
 */
@Component
public class CoordinatorState {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorState.class);
    private static final String GLOBAL_SCOPE_KEY = "global";

    /**
     * 作用域状态桶：scopeKey -> ScopeState
     * <p>
     * 当前已按 scope 分桶存储，为后续 session/run 级别隔离打基础。
     */
    private final ConcurrentHashMap<String, ScopeState> scopedStates = new ConcurrentHashMap<>();
    private static final int MAX_COMPLETED_TASKS = 100;
    /**
     * 任务所属作用域（为后续 session/run 级隔离预留）。
     */
    private final ConcurrentHashMap<String, String> taskScopes = new ConcurrentHashMap<>();

    /**
     * 获取所有活跃任务
     */
    public List<TaskState> getActiveTasks() {
        List<TaskState> out = new ArrayList<>();
        for (ScopeState scopeState : scopedStates.values()) {
            out.addAll(scopeState.activeTasks.values());
        }
        return out;
    }

    /**
     * 获取所有任务（包括已完成）
     */
    public List<TaskState> getAllTasks() {
        List<TaskState> all = new ArrayList<>();
        for (ScopeState scopeState : scopedStates.values()) {
            all.addAll(scopeState.activeTasks.values());
            all.addAll(scopeState.completedTasks);
        }
        return all;
    }

    /**
     * 获取指定作用域下的活跃任务（用于隔离场景）。
     */
    public List<TaskState> getActiveTasksByScope(String scopeKey) {
        ScopeState scopeState = scopedStates.get(normalizeScopeKey(scopeKey));
        if (scopeState == null) {
            return List.of();
        }
        return scopeState.activeTasks.values().stream().toList();
    }

    /**
     * 创建新任务
     */
    public TaskHandle createTask(String name, String goal, String assignedTo) {
        return createTask(resolveRuntimeScopeKey(), name, goal, assignedTo);
    }

    /**
     * 在指定作用域下创建任务。
     * <p>
     * 说明：当前仍使用单实例全局存储，scope 先作为元数据记录；
     * 后续可平滑升级为按 scope 分区的状态存储，无需改动调用方接口。
     */
    public TaskHandle createTask(String scopeKey, String name, String goal, String assignedTo) {
        String taskId = generateTaskId();
        Instant now = Instant.now();
        String normalizedScope = normalizeScopeKey(scopeKey);
        ScopeState scopeState = getOrCreateScopeState(normalizedScope);

        TaskState state = new TaskState(
                taskId,
                name,
                goal,
                assignedTo != null ? assignedTo : "general",
                TaskStatus.PENDING,
                new ConcurrentLinkedQueue<>(),
                new ConcurrentLinkedQueue<>(),
                now,
                now
        );

        scopeState.activeTasks.put(taskId, state);
        scopeState.taskCreatedAt.put(taskId, now);
        scopeState.taskFutures.put(taskId, new CompletableFuture<>());
        taskScopes.put(taskId, normalizedScope);

        log.info("创建任务：{} (scope={}, name={}, goal={}, assignedTo={})",
                taskId, normalizedScope, name, truncate(goal, 50), assignedTo);

        return new TaskHandle(taskId);
    }

    /**
     * 启动任务（PENDING -> RUNNING）
     */
    public boolean startTask(String taskId) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            log.warn("任务不存在：{}", taskId);
            return false;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task == null) {
            log.warn("任务不存在：{}", taskId);
            return false;
        }

        if (task.status() != TaskStatus.PENDING) {
            log.warn("任务状态不是 PENDING，无法启动：{} (status={})", taskId, task.status());
            return false;
        }

        updateTaskStatus(taskId, TaskStatus.RUNNING);
        log.info("启动任务：{}", taskId);
        return true;
    }

    /**
     * 发送消息给任务
     */
    public void sendMessage(String taskId, String message) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            log.warn("任务不存在：{}", taskId);
            return;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task == null) {
            log.warn("任务不存在：{}", taskId);
            return;
        }

        task.messages().add(message);
        task = updateTaskTime(taskId);

        log.debug("发送消息给任务 {}: {}", taskId, truncate(message, 50));
    }

    /**
     * 获取任务收件箱（并清空）
     */
    public List<String> drainMessages(String taskId) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        TaskState task = scopeState != null ? scopeState.activeTasks.get(taskId) : null;
        if (task == null) {
            return List.of();
        }

        List<String> messages = task.messages().stream().toList();
        ((ConcurrentLinkedQueue<String>) task.messages()).clear();
        return messages;
    }

    /**
     * 添加任务输出
     */
    public void addOutput(String taskId, String output) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            log.warn("任务不存在：{}", taskId);
            return;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task == null) {
            log.warn("任务不存在：{}", taskId);
            return;
        }

        task.outputs().add(output);
        log.debug("添加任务输出 {}: {} chars", taskId, output.length());
    }

    /**
     * 获取任务输出历史
     */
    public List<String> getOutputs(String taskId) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        TaskState task = scopeState != null ? scopeState.activeTasks.get(taskId) : null;
        return task != null ? task.outputs().stream().toList() : List.of();
    }

    /**
     * 完成任务
     */
    public void completeTask(String taskId, String result) {
        if (!updateTaskStatus(taskId, TaskStatus.COMPLETED)) {
            return;
        }

        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            return;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task != null) {
            // 通知等待的 Future
            CompletableFuture<TaskResult> future = scopeState.taskFutures.get(taskId);
            if (future != null) {
                future.complete(new TaskResult(taskId, result, null));
            }

            // 移动到已完成队列
            scopeState.activeTasks.remove(taskId);
            scopeState.completedTasks.add(task);
            trimCompletedTasks(scopeState);
            scopeState.taskCreatedAt.remove(taskId);

            log.info("任务完成：{} - {} chars", taskId, result != null ? result.length() : 0);
        }
    }

    /**
     * 失败任务
     */
    public void failTask(String taskId, String error) {
        if (!updateTaskStatus(taskId, TaskStatus.FAILED)) {
            return;
        }

        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            return;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task != null) {
            CompletableFuture<TaskResult> future = scopeState.taskFutures.get(taskId);
            if (future != null) {
                future.complete(new TaskResult(taskId, null, error));
            }

            scopeState.activeTasks.remove(taskId);
            scopeState.completedTasks.add(task);
            trimCompletedTasks(scopeState);
            String scopeKey = taskScopes.get(taskId);
            Instant createdAt = scopeState.taskCreatedAt.remove(taskId);

            Long elapsedMs = createdAt != null ? Duration.between(createdAt, Instant.now()).toMillis() : null;
            log.warn("任务失败：taskId={}, scope={}, assignedTo={}, error={}, elapsedMs={}",
                    taskId, scopeKey, task.assignedTo(), error, elapsedMs);
        }
    }

    /**
     * 停止任务
     */
    public void stopTask(String taskId) {
        if (!updateTaskStatus(taskId, TaskStatus.STOPPED)) {
            return;
        }

        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            return;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task != null) {
            CompletableFuture<TaskResult> future = scopeState.taskFutures.get(taskId);
            if (future != null) {
                future.complete(new TaskResult(taskId, null, "stopped"));
            }

            scopeState.activeTasks.remove(taskId);
            scopeState.completedTasks.add(task);
            trimCompletedTasks(scopeState);
            scopeState.taskCreatedAt.remove(taskId);

            log.info("任务停止：{}", taskId);
        }
    }

    /**
     * 等待任务完成（阻塞）
     */
    public TaskResult waitForTask(String taskId, Duration timeout) throws TimeoutException {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        CompletableFuture<TaskResult> future =
                scopeState != null ? scopeState.taskFutures.get(taskId) : null;
        if (future == null) {
            throw new IllegalArgumentException("任务不存在：" + taskId);
        }

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("等待任务超时：" + taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待被中断", e);
        } catch (ExecutionException e) {
            return new TaskResult(taskId, null, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }

    /**
     * 异步等待任务完成（返回 CompletableFuture）
     */
    public CompletableFuture<TaskResult> waitForTaskAsync(String taskId) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        CompletableFuture<TaskResult> future =
                scopeState != null ? scopeState.taskFutures.get(taskId) : null;
        if (future == null) {
            CompletableFuture<TaskResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("任务不存在：" + taskId));
            return failed;
        }
        return future;
    }

    /**
     * 获取任务状态
     */
    public Optional<TaskState> getTask(String taskId) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        return Optional.ofNullable(scopeState != null ? scopeState.activeTasks.get(taskId) : null);
    }

    /**
     * 检查任务是否存在
     */
    public boolean hasTask(String taskId) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            return false;
        }
        return scopeState.activeTasks.containsKey(taskId)
                || scopeState.completedTasks.stream().anyMatch(t -> t.taskId().equals(taskId));
    }

    /**
     * 更新任务状态
     */
    private boolean updateTaskStatus(String taskId, TaskStatus newStatus) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            return false;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task == null) {
            return false;
        }

        TaskState updated = new TaskState(
                task.taskId(),
                task.name(),
                task.goal(),
                task.assignedTo(),
                newStatus,
                task.messages(),
                task.outputs(),
                task.createdAt(),
                Instant.now()
        );

        scopeState.activeTasks.put(taskId, updated);
        log.debug("任务状态变更：{} -> {}", taskId, newStatus);
        return true;
    }

    /**
     * 更新时间戳
     */
    private TaskState updateTaskTime(String taskId) {
        ScopeState scopeState = getScopeStateByTaskId(taskId);
        if (scopeState == null) {
            return null;
        }
        TaskState task = scopeState.activeTasks.get(taskId);
        if (task == null) {
            return null;
        }

        TaskState updated = new TaskState(
                task.taskId(),
                task.name(),
                task.goal(),
                task.assignedTo(),
                task.status(),
                task.messages(),
                task.outputs(),
                task.createdAt(),
                Instant.now()
        );

        scopeState.activeTasks.put(taskId, updated);
        return updated;
    }

    /**
     * 修剪已完成任务队列
     */
    private void trimCompletedTasks(ScopeState scopeState) {
        while (scopeState.completedTasks.size() > MAX_COMPLETED_TASKS) {
            TaskState removed = scopeState.completedTasks.poll();
            if (removed != null) {
                scopeState.taskFutures.remove(removed.taskId());
                String currentScope = taskScopes.get(removed.taskId());
                if (currentScope != null && currentScope.equals(scopeState.scopeKey)) {
                    taskScopes.remove(removed.taskId());
                }
            }
        }
    }

    /**
     * 检查超时任务并自动失败
     */
    public void checkTimeouts(Duration timeout) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(timeout);

        for (ScopeState scopeState : scopedStates.values()) {
            for (Map.Entry<String, Instant> entry : scopeState.taskCreatedAt.entrySet()) {
                if (entry.getValue().isBefore(cutoff)) {
                    String taskId = entry.getKey();
                    TaskState task = scopeState.activeTasks.get(taskId);
                    if (task != null && task.status() == TaskStatus.RUNNING) {
                        log.warn("任务超时：{} (运行超过 {})", taskId, timeout);
                        failTask(taskId, "timeout: exceeded " + timeout);
                    }
                }
            }
        }
    }

    /**
     * 获取统计信息
     */
    public CoordinatorStats getStats() {
        long active = 0;
        long completed = 0;
        long running = 0;
        long waiting = 0;
        for (ScopeState scopeState : scopedStates.values()) {
            active += scopeState.activeTasks.size();
            completed += scopeState.completedTasks.size();
            running += scopeState.activeTasks.values().stream().filter(t -> t.status() == TaskStatus.RUNNING).count();
            waiting += scopeState.activeTasks.values().stream().filter(t -> t.status() == TaskStatus.WAITING).count();
        }
        return new CoordinatorStats(
                active,
                completed,
                running,
                waiting
        );
    }

    /**
     * 返回任务当前记录的作用域（若未知则为空）。
     */
    public Optional<String> getTaskScope(String taskId) {
        return Optional.ofNullable(taskScopes.get(taskId));
    }

    private static String resolveRuntimeScopeKey() {
        String sessionId = TraceContext.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            return "session:" + sessionId;
        }
        return GLOBAL_SCOPE_KEY;
    }

    private static String normalizeScopeKey(String scopeKey) {
        if (scopeKey == null || scopeKey.isBlank()) {
            return GLOBAL_SCOPE_KEY;
        }
        return scopeKey;
    }

    private ScopeState getOrCreateScopeState(String scopeKey) {
        return scopedStates.computeIfAbsent(scopeKey, ScopeState::new);
    }

    private ScopeState getScopeStateByTaskId(String taskId) {
        String scope = taskScopes.get(taskId);
        if (scope == null || scope.isBlank()) {
            return null;
        }
        return scopedStates.get(scope);
    }

    private static String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 任务句柄
     */
    public record TaskHandle(String taskId) {}

    /**
     * 任务结果
     */
    public record TaskResult(String taskId, String output, String error) {}

    /**
     * 协调器统计
     */
    public record CoordinatorStats(
            long activeTasks,
            long completedTasks,
            long runningTasks,
            long waitingTasks
    ) {}

    /**
     * 单个作用域的任务状态桶。
     */
    private static final class ScopeState {
        private final String scopeKey;
        private final ConcurrentHashMap<String, TaskState> activeTasks = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<TaskState> completedTasks = new ConcurrentLinkedQueue<>();
        private final ConcurrentHashMap<String, CompletableFuture<TaskResult>> taskFutures = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Instant> taskCreatedAt = new ConcurrentHashMap<>();

        private ScopeState(String scopeKey) {
            this.scopeKey = scopeKey;
        }
    }
}
