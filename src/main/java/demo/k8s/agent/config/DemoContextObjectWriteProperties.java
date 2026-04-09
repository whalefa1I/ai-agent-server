package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外置上下文对象写入路径（ContextObjectWriteService）的配置。
 */
@ConfigurationProperties(prefix = "demo.context-object")
public class DemoContextObjectWriteProperties {

    /**
     * 是否启用写入路径；关闭时所有超长内容直接走降级 fallback。
     */
    private boolean writeEnabled = false;

    /**
     * 单租户部署默认租户标识；多租户时应由 TraceContext 扩展注入真实 tenantId。
     */
    private String defaultTenantId = "default";

    /**
     * 触发外置写入的字符阈值；超过此值的工具结果才会被写入 DB。
     */
    private int writeThresholdChars = 10_000;

    /**
     * 写入失败时 fallback 保留的头部字符数。
     */
    private int fallbackHeadChars = 2000;

    /**
     * 写入失败时 fallback 保留的尾部字符数。
     */
    private int fallbackTailChars = 2000;

    /**
     * 上下文对象默认存活时间（小时）。
     */
    private int defaultTtlHours = 24;

    public boolean isWriteEnabled() {
        return writeEnabled;
    }

    public void setWriteEnabled(boolean writeEnabled) {
        this.writeEnabled = writeEnabled;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public int getWriteThresholdChars() {
        return writeThresholdChars;
    }

    public void setWriteThresholdChars(int writeThresholdChars) {
        this.writeThresholdChars = writeThresholdChars;
    }

    public int getFallbackHeadChars() {
        return fallbackHeadChars;
    }

    public void setFallbackHeadChars(int fallbackHeadChars) {
        this.fallbackHeadChars = fallbackHeadChars;
    }

    public int getFallbackTailChars() {
        return fallbackTailChars;
    }

    public void setFallbackTailChars(int fallbackTailChars) {
        this.fallbackTailChars = fallbackTailChars;
    }

    public int getDefaultTtlHours() {
        return defaultTtlHours;
    }

    public void setDefaultTtlHours(int defaultTtlHours) {
        this.defaultTtlHours = defaultTtlHours;
    }
}
