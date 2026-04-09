package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外置上下文对象（ContextObjectStore）与 {@code read_context_object} 工具开关及读保护参数。
 */
@ConfigurationProperties(prefix = "demo.context-object")
public class DemoContextObjectProperties {

    /**
     * 是否启用读取工具及写入路径（表结构由 Flyway 始终创建；关闭时工具仍可注册但返回禁用语义）。
     */
    private boolean enabled = false;

    /**
     * 单租户部署默认租户标识；多租户时应由上层解析为真实 tenantId 并扩展 TraceContext。
     */
    private String defaultTenantId = "default";

    /**
     * 单次读取返回正文的最大字符数（硬顶，防止一次拉回超大文本撑爆上下文）。
     */
    private int readMaxChars = 200_000;

    /**
     * 单次读取允许的最大 token 估算（与 token_estimate 比较；超出则拒绝全文并提示分片）。
     */
    private int readMaxTokenEstimate = 50_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public int getReadMaxChars() {
        return readMaxChars;
    }

    public void setReadMaxChars(int readMaxChars) {
        this.readMaxChars = readMaxChars;
    }

    public int getReadMaxTokenEstimate() {
        return readMaxTokenEstimate;
    }

    public void setReadMaxTokenEstimate(int readMaxTokenEstimate) {
        this.readMaxTokenEstimate = readMaxTokenEstimate;
    }
}
