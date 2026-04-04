package demo.k8s.agent.coordinator;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * 内存级「工人邮箱」占位：记录 SendMessage 入队与 TaskStop 取消请求。
 * 后续可换为与 {@link org.springaicommunity.agent.tools.task.repository.TaskRepository} 或进程外总线对接。
 */
@Component
public class InMemoryWorkerMailbox {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> queues = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Boolean> cancelRequested = new ConcurrentHashMap<>();

    /** 本会话中由 Task 工具输出解析得到的 task_id，供 SendMessage 等对账（演示）。 */
    private final Set<String> knownTaskIds = ConcurrentHashMap.newKeySet();

    public void registerKnownTaskId(String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            knownTaskIds.add(taskId.trim());
        }
    }

    public boolean isKnownTaskId(String taskId) {
        return taskId != null && !taskId.isBlank() && knownTaskIds.contains(taskId.trim());
    }

    public void enqueueMessage(String taskId, String message) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        queues.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(message);
    }

    public List<String> drain(String taskId) {
        var q = queues.get(taskId);
        if (q == null) {
            return List.of();
        }
        var copy = List.copyOf(q);
        q.clear();
        return copy;
    }

    public void requestCancel(String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            cancelRequested.put(taskId, true);
        }
    }

    public boolean isCancelRequested(String taskId) {
        return Boolean.TRUE.equals(cancelRequested.get(taskId));
    }
}
