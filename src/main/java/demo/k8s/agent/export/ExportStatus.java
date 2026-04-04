package demo.k8s.agent.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * 导出任务状态枚举
 */
public enum ExportStatus {
    /**
     * 等待中
     */
    PENDING,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 已完成
     */
    COMPLETED,

    /**
     * 已失败
     */
    FAILED,

    /**
     * 已取消
     */
    CANCELLED
}
