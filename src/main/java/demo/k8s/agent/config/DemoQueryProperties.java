package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对齐 Claude Code {@code queryLoop} 的可调参数：最大轮次、压缩阈值、HTTP 重试。
 */
@ConfigurationProperties(prefix = "demo.query")
public class DemoQueryProperties {

    /** 外层 while 与 TS {@code maxTurns} 类似 */
    private int maxTurns = 32;

    /** Tier1：单条 tool 结果字符串超过则截断（microcompact 类比） */
    private int microcompactMaxCharsPerToolResponse = 24_000;

    /** Tier2：时间触发压缩 - 空闲超过此阈值（分钟）后清除旧工具结果，<=0 表示禁用 */
    private int timeBasedCompactionGapMinutes = 30;

    /** Tier2：时间触发压缩时保留的最近工具结果数量 */
    private int timeBasedCompactionKeepRecent = 3;

    /** 估算总字符超过则尝试 Tier3 摘要（autocompact 类比，需开启 full-compact-enabled） */
    private int fullCompactThresholdChars = 120_000;

    /** 跨用户轮次拼接的历史窗口消息数（仅 USER/ASSISTANT） */
    private int historyWindowMessages = 16;

    /** 是否启用「子 LLM 摘要」式全量压缩（额外 API 调用） */
    private boolean fullCompactEnabled = false;

    private Retry retry = new Retry();

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public int getMicrocompactMaxCharsPerToolResponse() {
        return microcompactMaxCharsPerToolResponse;
    }

    public void setMicrocompactMaxCharsPerToolResponse(int microcompactMaxCharsPerToolResponse) {
        this.microcompactMaxCharsPerToolResponse = microcompactMaxCharsPerToolResponse;
    }

    public int getTimeBasedCompactionGapMinutes() {
        return timeBasedCompactionGapMinutes;
    }

    public void setTimeBasedCompactionGapMinutes(int timeBasedCompactionGapMinutes) {
        this.timeBasedCompactionGapMinutes = timeBasedCompactionGapMinutes;
    }

    public int getTimeBasedCompactionKeepRecent() {
        return timeBasedCompactionKeepRecent;
    }

    public void setTimeBasedCompactionKeepRecent(int timeBasedCompactionKeepRecent) {
        this.timeBasedCompactionKeepRecent = timeBasedCompactionKeepRecent;
    }

    public int getFullCompactThresholdChars() {
        return fullCompactThresholdChars;
    }

    public void setFullCompactThresholdChars(int fullCompactThresholdChars) {
        this.fullCompactThresholdChars = fullCompactThresholdChars;
    }

    public boolean isFullCompactEnabled() {
        return fullCompactEnabled;
    }

    public void setFullCompactEnabled(boolean fullCompactEnabled) {
        this.fullCompactEnabled = fullCompactEnabled;
    }

    public int getHistoryWindowMessages() {
        return historyWindowMessages;
    }

    public void setHistoryWindowMessages(int historyWindowMessages) {
        this.historyWindowMessages = historyWindowMessages;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public static class Retry {

        private int maxAttempts = 5;

        private long baseDelayMs = 500L;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBaseDelayMs() {
            return baseDelayMs;
        }

        public void setBaseDelayMs(long baseDelayMs) {
            this.baseDelayMs = baseDelayMs;
        }
    }
}
