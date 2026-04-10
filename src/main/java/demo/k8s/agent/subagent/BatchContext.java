package demo.k8s.agent.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批次上下文：追踪一组相关子任务的执行状态。
 * <p>
 * 用于 Map-Reduce 模式下的批次进度追踪和结果收集。
 */
public class BatchContext {

    private static final Logger log = LoggerFactory.getLogger(BatchContext.class);

    private final String batchId;
    private final String sessionId;
    private final String mainRunId;
    private final int totalTasks;
    private final AtomicInteger completedCount;
    private final Map<String, String> taskResults;
    private final Instant createdAt;
    private final Instant deadline;

    public BatchContext(String batchId, String sessionId, String mainRunId, int totalTasks, long wallclockTtlSeconds) {
        this.batchId = batchId;
        this.sessionId = sessionId;
        this.mainRunId = mainRunId;
        this.totalTasks = totalTasks;
        this.completedCount = new AtomicInteger(0);
        this.taskResults = new ConcurrentHashMap<>();
        this.createdAt = Instant.now();
        this.deadline = Instant.now().plusSeconds(wallclockTtlSeconds);
    }

    /**
     * 标记子任务完成。
     *
     * @param runId   子任务运行 ID
     * @param result  执行结果（可为 null 或错误信息）
     * @return 完成后的计数
     */
    public int markCompleted(String runId, String result) {
        String value = result != null ? result : "(no result)";
        if (taskResults.putIfAbsent(runId, value) != null) {
            log.debug("[BatchContext] Duplicate completion ignored: batchId={}, runId={}", batchId, runId);
            return completedCount.get();
        }
        int count = completedCount.incrementAndGet();
        log.debug("[BatchContext] Task completed: batchId={}, runId={}, count={}/{}", batchId, runId, count, totalTasks);
        return count;
    }

    /**
     * 检查批次是否全部完成。
     *
     * @return true 如果所有任务都已完成
     */
    public boolean isAllCompleted() {
        return completedCount.get() >= totalTasks;
    }

    /**
     * 检查批次是否超时。
     *
     * @return true 如果已超过截止时间
     */
    public boolean isTimedOut() {
        return Instant.now().isAfter(deadline);
    }

    /**
     * 收集所有任务结果为汇总文本。
     * <p>
     * 用于注入主 Agent 的系统消息，唤醒主线程进行汇总。
     *
     * @return 汇总文本
     */
    public String collectResultsAsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Batch Completion Summary ===\n");
        sb.append("Batch ID: ").append(batchId).append("\n");
        sb.append("Session ID: ").append(sessionId).append("\n");
        sb.append("Total Tasks: ").append(totalTasks).append("\n");
        sb.append("Completed: ").append(completedCount.get()).append("\n");
        sb.append("\n=== Task Results ===\n\n");

        int index = 0;
        for (Map.Entry<String, String> entry : taskResults.entrySet()) {
            index++;
            sb.append("[Task ").append(index).append("/").append(totalTasks).append("] Run ID: ").append(entry.getKey()).append("\n");
            sb.append("Result: ").append(truncate(entry.getValue(), 2000)).append("\n\n");
        }

        sb.append("\n=== End of Batch Results ===\n");
        sb.append("All ").append(totalTasks).append(" subtasks completed. Please summarize the results and provide a consolidated response to the user.\n");

        return sb.toString();
    }

    /**
     * 获取剩余未完成的任务数。
     *
     * @return pending 任务数
     */
    public int getPendingCount() {
        return totalTasks - completedCount.get();
    }

    // Getters

    public String getBatchId() {
        return batchId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMainRunId() {
        return mainRunId;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public int getCompletedCount() {
        return completedCount.get();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeadline() {
        return deadline;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\n... [truncated, " + (s.length() - maxLen) + " chars omitted]";
    }
}
