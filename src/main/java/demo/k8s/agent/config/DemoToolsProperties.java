package demo.k8s.agent.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 Claude Code 中环境变量 + {@code feature()} 组合出的「工具相关」开关；用于 {@link demo.k8s.agent.toolsystem.ToolAssembly}。
 */
@ConfigurationProperties(prefix = "demo.tools")
public class DemoToolsProperties {

    private String permissionMode = "default";

    /** 对应 TS 侧 {@code CLAUDE_CODE_SIMPLE}：仅暴露「简单」子集（见 {@link #simpleToolNames}） */
    private boolean simpleMode = false;

    private Feature feature = new Feature();

    /** 对应 {@code USER_TYPE === 'ant'} 等内部能力；默认关闭 */
    private boolean internalAntTools = false;

    /** 名称级拒绝列表，对应 {@code filterToolsByDenyRules} 中的 blanket deny */
    private Set<String> denyToolNames = new HashSet<>();

    /**
     * 简单模式下保留的工具名；TS 默认为 Bash / Read / Edit，本 demo 默认仅 {@code Skill}（只读）作占位。
     */
    private Set<String> simpleToolNames = new HashSet<>(Set.of("Skill"));

    public String getPermissionMode() {
        return permissionMode;
    }

    public void setPermissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
    }

    public boolean isSimpleMode() {
        return simpleMode;
    }

    public void setSimpleMode(boolean simpleMode) {
        this.simpleMode = simpleMode;
    }

    public Feature getFeature() {
        return feature;
    }

    public void setFeature(Feature feature) {
        this.feature = feature;
    }

    public boolean isInternalAntTools() {
        return internalAntTools;
    }

    public void setInternalAntTools(boolean internalAntTools) {
        this.internalAntTools = internalAntTools;
    }

    public Set<String> getDenyToolNames() {
        return denyToolNames;
    }

    public void setDenyToolNames(Set<String> denyToolNames) {
        this.denyToolNames = denyToolNames;
    }

    public Set<String> getSimpleToolNames() {
        return simpleToolNames;
    }

    public void setSimpleToolNames(Set<String> simpleToolNames) {
        this.simpleToolNames = simpleToolNames;
    }

    public static class Feature {

        /** 对应 {@code ENABLE_AGENT_SWARMS} / {@code isAgentSwarmsEnabled()} — Agent/Task 类工具 */
        private boolean agentSwarms = true;

        private boolean experimental = false;

        public boolean isAgentSwarms() {
            return agentSwarms;
        }

        public void setAgentSwarms(boolean agentSwarms) {
            this.agentSwarms = agentSwarms;
        }

        public boolean isExperimental() {
            return experimental;
        }

        public void setExperimental(boolean experimental) {
            this.experimental = experimental;
        }
    }
}
