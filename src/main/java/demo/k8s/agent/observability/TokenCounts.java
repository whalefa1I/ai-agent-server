package demo.k8s.agent.observability;

/**
 * Token 计数记录，与 Claude Code 的 token tracking 对齐。
 *
 * @param inputTokens 输入 tokens（prompt）
 * @param outputTokens 输出 tokens（completion）
 * @param cacheReadTokens 缓存读取 tokens（cache read）
 * @param cacheWriteTokens 缓存写入 tokens（cache write）
 */
public record TokenCounts(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
) {
    public static TokenCounts ZERO = new TokenCounts(0, 0, 0, 0);

    /**
     * 总消耗 tokens（输入 + 输出）
     */
    public long total() {
        return inputTokens + outputTokens;
    }

    /**
     * 总计费 tokens（部分 API 缓存读取免费，缓存写入收费）
     */
    public long billable() {
        return inputTokens + outputTokens + cacheWriteTokens;
    }

    /**
     * 累加另一个 TokenCounts
     */
    public TokenCounts add(TokenCounts other) {
        return new TokenCounts(
                this.inputTokens + other.inputTokens,
                this.outputTokens + other.outputTokens,
                this.cacheReadTokens + other.cacheReadTokens,
                this.cacheWriteTokens + other.cacheWriteTokens
        );
    }

    /**
     * 从另一个 TokenCounts 减去
     */
    public TokenCounts subtract(TokenCounts other) {
        return new TokenCounts(
                Math.max(0, this.inputTokens - other.inputTokens),
                Math.max(0, this.outputTokens - other.outputTokens),
                Math.max(0, this.cacheReadTokens - other.cacheReadTokens),
                Math.max(0, this.cacheWriteTokens - other.cacheWriteTokens)
        );
    }
}
