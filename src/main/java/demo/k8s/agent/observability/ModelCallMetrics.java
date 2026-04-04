package demo.k8s.agent.observability;

import java.time.Duration;
import java.time.Instant;

/**
 * 单次模型调用指标记录。
 *
 * @param id 调用唯一 ID
 * @param startTime 开始时间
 * @param endTime 结束时间
 * @param latency 延迟（毫秒）
 * @param tokenCounts Token 计数
 * @param model 使用的模型名称
 * @param success 是否成功
 * @param errorMessage 错误信息（失败时）
 */
public record ModelCallMetrics(
        String id,
        Instant startTime,
        Instant endTime,
        Duration latency,
        TokenCounts tokenCounts,
        String model,
        boolean success,
        String errorMessage
) {
    public static ModelCallMetrics create(
            String model,
            TokenCounts tokenCounts) {
        Instant now = Instant.now();
        return new ModelCallMetrics(
                generateId(),
                now,
                now,
                Duration.ZERO,
                tokenCounts,
                model,
                true,
                null
        );
    }

    public static ModelCallMetrics error(
            String model,
            String errorMessage) {
        Instant now = Instant.now();
        return new ModelCallMetrics(
                generateId(),
                now,
                now,
                Duration.ZERO,
                TokenCounts.ZERO,
                model,
                false,
                errorMessage
        );
    }

    /**
     * 完成一次调用并计算延迟
     */
    public ModelCallMetrics complete(TokenCounts counts) {
        Instant end = Instant.now();
        return new ModelCallMetrics(
                this.id,
                this.startTime,
                end,
                Duration.between(this.startTime, end),
                counts,
                this.model,
                this.success,
                this.errorMessage
        );
    }

    public ModelCallMetrics completeError(String error) {
        Instant end = Instant.now();
        return new ModelCallMetrics(
                this.id,
                this.startTime,
                end,
                Duration.between(this.startTime, end),
                TokenCounts.ZERO,
                this.model,
                false,
                error
        );
    }

    private static String generateId() {
        return "model_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取延迟（毫秒）
     */
    public long getLatencyMs() {
        return latency.toMillis();
    }
}
