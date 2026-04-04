package demo.k8s.agent.toolsystem;

import java.util.Set;

import demo.k8s.agent.config.DemoToolsProperties;

/**
 * 从 {@link DemoToolsProperties} 投影出的运行时标志，供 {@link ToolAssembly} / {@link ToolRegistry} 使用。
 */
public record ToolFeatureFlags(
        boolean simpleMode,
        boolean agentSwarmsEnabled,
        boolean experimentalEnabled,
        boolean internalAntTools,
        Set<String> simpleToolNames) {

    public static ToolFeatureFlags from(DemoToolsProperties p) {
        DemoToolsProperties.Feature f = p.getFeature() != null ? p.getFeature() : new DemoToolsProperties.Feature();
        Set<String> simple = p.getSimpleToolNames();
        if (simple == null) {
            simple = Set.of("Skill");
        }
        return new ToolFeatureFlags(
                p.isSimpleMode(),
                f.isAgentSwarms(),
                f.isExperimental(),
                p.isInternalAntTools(),
                Set.copyOf(simple));
    }
}
