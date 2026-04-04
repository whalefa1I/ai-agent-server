package demo.k8s.agent.config;

import java.util.Collections;

import demo.k8s.agent.toolsystem.ToolFeatureFlags;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import demo.k8s.agent.toolsystem.ToolPermissionMode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolPermissionConfiguration {

    @Bean
    ToolPermissionContext toolPermissionContext(DemoToolsProperties props) {
        String mode = props.getPermissionMode();
        String m = mode == null ? "default" : mode.trim().toLowerCase();
        var deny = props.getDenyToolNames() != null ? props.getDenyToolNames() : Collections.<String>emptySet();
        return switch (m) {
            case "read_only", "readonly" ->
                    new ToolPermissionContext(ToolPermissionMode.READ_ONLY, Collections.emptySet(), deny);
            case "bypass" -> new ToolPermissionContext(ToolPermissionMode.BYPASS, Collections.emptySet(), deny);
            default -> new ToolPermissionContext(ToolPermissionMode.DEFAULT, Collections.emptySet(), deny);
        };
    }

    @Bean
    ToolFeatureFlags toolFeatureFlags(DemoToolsProperties props) {
        return ToolFeatureFlags.from(props);
    }
}
