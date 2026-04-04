package demo.k8s.agent.sandbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AgentScope 沙盒配置属性。
 *
 * @deprecated 暂时禁用，修复 bean 冲突问题
 */
// @Component  // 已禁用 - 注释掉以避免 bean 冲突
@ConfigurationProperties(prefix = "agentscope.sandbox")
public class AgentScopeSandboxProperties {

    /**
     * 远程 AgentScope sandbox-server 地址，默认：http://localhost:8000
     */
    private String baseUrl = "http://localhost:8000";

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 默认会话超时时间（秒）
     */
    private int sessionTimeoutSeconds = 3600;

    /**
     * 默认沙盒类型
     */
    private String defaultSandboxType = "base";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    public void setSessionTimeoutSeconds(int sessionTimeoutSeconds) {
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    public String getDefaultSandboxType() {
        return defaultSandboxType;
    }

    public void setDefaultSandboxType(String defaultSandboxType) {
        this.defaultSandboxType = defaultSandboxType;
    }
}
