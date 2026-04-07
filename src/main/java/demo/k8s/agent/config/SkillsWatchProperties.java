package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 技能热加载配置属性
 */
@ConfigurationProperties(prefix = "demo.skills.watch")
public class SkillsWatchProperties {

    /**
     * 是否启用技能热加载监听
     */
    private boolean enabled = true;

    /**
     * 防抖延迟（毫秒），避免频繁重载
     */
    private long debounceMs = 250;

    /**
     * 是否忽略 .git 目录
     */
    private boolean ignoreGit = true;

    /**
     * 是否忽略 node_modules 目录
     */
    private boolean ignoreNodeModules = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getDebounceMs() { return debounceMs; }
    public void setDebounceMs(long debounceMs) { this.debounceMs = debounceMs; }

    public boolean isIgnoreGit() { return ignoreGit; }
    public void setIgnoreGit(boolean ignoreGit) { this.ignoreGit = ignoreGit; }

    public boolean isIgnoreNodeModules() { return ignoreNodeModules; }
    public void setIgnoreNodeModules(boolean ignoreNodeModules) { this.ignoreNodeModules = ignoreNodeModules; }
}
