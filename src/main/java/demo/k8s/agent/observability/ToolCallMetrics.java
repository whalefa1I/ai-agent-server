package demo.k8s.agent.observability;

import java.time.Duration;
import java.time.Instant;

/**
 * 单次工具调用指标记录。
 *
 * @param id 调用唯一 ID
 * @param startTime 开始时间
 * @param endTime 结束时间
 * @param latency 延迟
 * @param toolName 工具名称
 * @param toolInputSummary 输入摘要
 * @param toolOutputSummary 输出摘要
 * @param success 是否成功
 * @param errorMessage 错误信息
 */
public record ToolCallMetrics(
        String id,
        Instant startTime,
        Instant endTime,
        Duration latency,
        String toolName,
        String toolInputSummary,
        String toolOutputSummary,
        boolean success,
        String errorMessage
) {
    public static ToolCallMetrics create(String toolName, String inputSummary) {
        Instant now = Instant.now();
        return new ToolCallMetrics(
                generateId(),
                now,
                now,
                Duration.ZERO,
                toolName,
                inputSummary,
                null,
                true,
                null
        );
    }

    /**
     * 完成一次调用并计算延迟
     */
    public ToolCallMetrics complete(String output) {
        Instant end = Instant.now();
        String summary = output != null && output.length() > 200
                ? output.substring(0, 200) + "..."
                : output;

        return new ToolCallMetrics(
                this.id,
                this.startTime,
                end,
                Duration.between(this.startTime, end),
                this.toolName,
                this.toolInputSummary,
                summary,
                this.success,
                this.errorMessage
        );
    }

    public ToolCallMetrics completeError(String error) {
        Instant end = Instant.now();
        return new ToolCallMetrics(
                this.id,
                this.startTime,
                end,
                Duration.between(this.startTime, end),
                this.toolName,
                this.toolInputSummary,
                null,
                false,
                error
        );
    }

    private static String generateId() {
        return "tool_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    public long getLatencyMs() {
        return latency.toMillis();
    }
}
