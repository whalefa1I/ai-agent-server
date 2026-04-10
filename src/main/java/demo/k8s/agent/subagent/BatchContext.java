package demo.k8s.agent.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批次上下文：追踪一组相关子任务的执行状态。
 * <p>
 * 用于 Map-Reduce 模式下的批次进度追踪和结果收集。
 * 结果全文外置存储，本类仅存储路径和摘要以节省主 Agent 上下文。
 */
public class BatchContext {

    private static final Logger log = LoggerFactory.getLogger(BatchContext.class);

    /**
     * 单条任务结果记录（外置存储后仅保留元数据）
     */
    public record TaskResult(String resultPath, String summary, String status) {}

    private final String batchId;
    private final String sessionId;
    private final String mainRunId;
    private final int totalTasks;
    private final AtomicInteger completedCount;
    private final Map<String, TaskResult> taskResults;  // runId -> TaskResult
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
     * <p>
     * 结果全文已外置存储，此处仅存储路径和摘要。
     *
     * @param runId     子任务运行 ID
     * @param resultPath 结果文件路径（相对于 resultsRoot）
     * @param summary   结果摘要（~100 字符）
     * @param status    执行状态（COMPLETED/FAILED/TIMEOUT/CANCELLED）
     * @return 完成后的计数
     */
    public int markCompleted(String runId, String resultPath, String summary, String status) {
        TaskResult taskResult = new TaskResult(resultPath, summary, status);
        if (taskResults.putIfAbsent(runId, taskResult) != null) {
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
     * 收集所有任务结果为轻量级汇总文本（外置路径 + 摘要）。
     * <p>
     * 用于注入主 Agent 的系统消息，唤醒主线程进行汇总。
     * 主模型需要全文时可调用 file_read 读取结果文件。
     *
     * @return 轻量级汇总文本
     */
    public String collectResultsAsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SUBAGENT BATCH COMPLETED ===\n\n");
        sb.append("Batch ID: ").append(batchId).append("\n");
        sb.append("Total Tasks: ").append(totalTasks).append("\n");
        sb.append("Completed: ").append(completedCount.get()).append("\n\n");

        sb.append("=== Results (use file_read to view full content) ===\n\n");

        int index = 0;
        for (Map.Entry<String, TaskResult> entry : taskResults.entrySet()) {
            index++;
            TaskResult result = entry.getValue();
            sb.append("[Task ").append(index).append("/").append(totalTasks).append("] ");
            sb.append("Status: ").append(result.status());
            sb.append(" | Summary: ").append(result.summary());
            sb.append(" | File: ").append(result.resultPath()).append("\n");
        }

        sb.append("\n=== END OF BATCH RESULTS ===\n");
        sb.append("Tip: Use file_read tool to view full results for any task.\n");

        return sb.toString();
    }

    /**
     * 获取批次结果详情（供运维/产品 API 使用）。
     *
     * @return 任务结果列表
     */
    public Map<String, TaskResult> getTaskResults() {
        return new LinkedHashMap<>(taskResults);
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
}
