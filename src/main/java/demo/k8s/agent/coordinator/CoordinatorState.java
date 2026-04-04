package demo.k8s.agent.coordinator;

import demo.k8s.agent.state.ConversationSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
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
@Scope("prototype")
public class CoordinatorState {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorState.class);

    /**
     * 活跃任务：taskId -> TaskState
     */
    private final ConcurrentHashMap<String, TaskState> activeTasks = new ConcurrentHashMap<>();

    /**
     * 已完成任务（保留最近 100 条）
     */
    private final ConcurrentLinkedQueue<TaskState> completedTasks = new ConcurrentLinkedQueue<>();
    private static final int MAX_COMPLETED_TASKS = 100;

    /**
     * 任务结果 Future：taskId -> CompletableFuture
     */
    private final ConcurrentHashMap<String, CompletableFuture<TaskResult>> taskFutures = new ConcurrentHashMap<>();

    /**
     * 任务创建时间：用于超时检测
     */
    private final ConcurrentHashMap<String, Instant> taskCreatedAt = new ConcurrentHashMap<>();

    /**
     * 获取所有活跃任务
     */
    public List<TaskState> getActiveTasks() {
        return activeTasks.values().stream().toList();
    }

    /**
     * 获取所有任务（包括已完成）
     */
    public List<TaskState> getAllTasks() {
        List<TaskState> all = new java.util.ArrayList<>(activeTasks.values());
        all.addAll(completedTasks);
        return all;
    }

    /**
     * 创建新任务
     */
    public TaskHandle createTask(String name, String goal, String assignedTo) {
        String taskId = generateTaskId();
        Instant now = Instant.now();

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

        activeTasks.put(taskId, state);
        taskCreatedAt.put(taskId, now);
        taskFutures.put(taskId, new CompletableFuture<>());

        log.info("创建任务：{} (name={}, goal={}, assignedTo={})", taskId, name, truncate(goal, 50), assignedTo);

        return new TaskHandle(taskId);
    }

    /**
     * 启动任务（PENDING -> RUNNING）
     */
    public boolean startTask(String taskId) {
        TaskState task = activeTasks.get(taskId);
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
        TaskState task = activeTasks.get(taskId);
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
        TaskState task = activeTasks.get(taskId);
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
        TaskState task = activeTasks.get(taskId);
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
        TaskState task = activeTasks.get(taskId);
        return task != null ? task.outputs().stream().toList() : List.of();
    }

    /**
     * 完成任务
     */
    public void completeTask(String taskId, String result) {
        if (!updateTaskStatus(taskId, TaskStatus.COMPLETED)) {
            return;
        }

        TaskState task = activeTasks.get(taskId);
        if (task != null) {
            // 通知等待的 Future
            CompletableFuture<TaskResult> future = taskFutures.get(taskId);
            if (future != null) {
                future.complete(new TaskResult(taskId, result, null));
            }

            // 移动到已完成队列
            activeTasks.remove(taskId);
            completedTasks.add(task);
            trimCompletedTasks();

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

        TaskState task = activeTasks.get(taskId);
        if (task != null) {
            CompletableFuture<TaskResult> future = taskFutures.get(taskId);
            if (future != null) {
                future.complete(new TaskResult(taskId, null, error));
            }

            activeTasks.remove(taskId);
            completedTasks.add(task);
            trimCompletedTasks();

            log.warn("任务失败：{} - {}", taskId, error);
        }
    }

    /**
     * 停止任务
     */
    public void stopTask(String taskId) {
        if (!updateTaskStatus(taskId, TaskStatus.STOPPED)) {
            return;
        }

        TaskState task = activeTasks.get(taskId);
        if (task != null) {
            CompletableFuture<TaskResult> future = taskFutures.get(taskId);
            if (future != null) {
                future.complete(new TaskResult(taskId, null, "stopped"));
            }

            activeTasks.remove(taskId);
            completedTasks.add(task);
            trimCompletedTasks();

            log.info("任务停止：{}", taskId);
        }
    }

    /**
     * 等待任务完成（阻塞）
     */
    public TaskResult waitForTask(String taskId, Duration timeout) throws TimeoutException {
        CompletableFuture<TaskResult> future = taskFutures.get(taskId);
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
        CompletableFuture<TaskResult> future = taskFutures.get(taskId);
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
        return Optional.ofNullable(activeTasks.get(taskId));
    }

    /**
     * 检查任务是否存在
     */
    public boolean hasTask(String taskId) {
        return activeTasks.containsKey(taskId) || completedTasks.stream().anyMatch(t -> t.taskId().equals(taskId));
    }

    /**
     * 更新任务状态
     */
    private boolean updateTaskStatus(String taskId, TaskStatus newStatus) {
        TaskState task = activeTasks.get(taskId);
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

        activeTasks.put(taskId, updated);
        log.debug("任务状态变更：{} -> {}", taskId, newStatus);
        return true;
    }

    /**
     * 更新时间戳
     */
    private TaskState updateTaskTime(String taskId) {
        TaskState task = activeTasks.get(taskId);
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

        activeTasks.put(taskId, updated);
        return updated;
    }

    /**
     * 修剪已完成任务队列
     */
    private void trimCompletedTasks() {
        while (completedTasks.size() > MAX_COMPLETED_TASKS) {
            completedTasks.poll();
        }
    }

    /**
     * 检查超时任务并自动失败
     */
    public void checkTimeouts(Duration timeout) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(timeout);

        for (Map.Entry<String, Instant> entry : taskCreatedAt.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                String taskId = entry.getKey();
                TaskState task = activeTasks.get(taskId);
                if (task != null && task.status() == TaskStatus.RUNNING) {
                    log.warn("任务超时：{} (运行超过 {})", taskId, timeout);
                    failTask(taskId, "timeout: exceeded " + timeout);
                }
            }
        }
    }

    /**
     * 获取统计信息
     */
    public CoordinatorStats getStats() {
        return new CoordinatorStats(
                activeTasks.size(),
                completedTasks.size(),
                activeTasks.values().stream().filter(t -> t.status() == TaskStatus.RUNNING).count(),
                activeTasks.values().stream().filter(t -> t.status() == TaskStatus.WAITING).count()
        );
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
}
