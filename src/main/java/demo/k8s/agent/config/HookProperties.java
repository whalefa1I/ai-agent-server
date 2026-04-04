package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hook 配置属性
 */
@Component
@ConfigurationProperties(prefix = "demo.hooks")
public class HookProperties {

    /**
     * 是否启用 Hook 系统
     */
    private boolean enabled = true;

    /**
     * 工作区 Hook 目录（默认 .openclaw/hooks/）
     */
    private String workspaceDir;

    /**
     * 允许执行的 Hook 类型列表（空表示允许所有）
     */
    private List<String> allowedHooks = new ArrayList<>();

    /**
     * Hook 执行超时时间（毫秒）
     */
    private long timeoutMs = 5000;

    /**
     * 是否异步执行 Hook
     */
    private boolean async = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public List<String> getAllowedHooks() {
        return allowedHooks;
    }

    public void setAllowedHooks(List<String> allowedHooks) {
        this.allowedHooks = allowedHooks;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }
}
