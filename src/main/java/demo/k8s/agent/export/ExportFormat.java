package demo.k8s.agent.export;

/**
 * 导出格式枚举
 */
public enum ExportFormat {
    /**
     * Alpaca JSONL 格式
     */
    ALPACA,

    /**
     * ShareGPT JSON 格式
     */
    SHAREGPT,

    /**
     * ChatML 文本格式
     */
    CHATML,

    /**
     * 原始 JSONL 格式
     */
    JSONL
}
