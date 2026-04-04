package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对齐 Claude Code Coordinator Mode 的<strong>第一阶段</strong>开关（运行时配置）。
 * TS 侧还有 {@code feature('COORDINATOR_MODE')} + {@code CLAUDE_CODE_COORDINATOR_MODE} 双闸门；此处用单配置简化。
 */
@ConfigurationProperties(prefix = "demo.coordinator")
public class DemoCoordinatorProperties {

    /**
     * 为 true 时：主会话仅暴露 Task / SendMessage / TaskStop（见 {@link demo.k8s.agent.config.DemoToolRegistryConfiguration}），
     * 不直接持有 k8s_sandbox_run、Skill；子 Agent 仍由 {@link org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType} 执行。
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
