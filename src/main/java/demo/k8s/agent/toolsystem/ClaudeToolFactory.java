package demo.k8s.agent.toolsystem;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Supplier;

/**
 * 与 Claude Code {@code buildTool(def)} 对齐：将 {@link ToolDefPartial} 合并为完整 {@link ClaudeLikeTool}。
 */
public final class ClaudeToolFactory {

    private ClaudeToolFactory() {}

    public static ClaudeLikeTool buildTool(ToolDefPartial def) {
        Supplier<Boolean> enabled = def.isEnabled();
        boolean ro = def.readOnly();
        return new ClaudeLikeTool() {
            @Override
            public String name() {
                return def.name();
            }

            @Override
            public ToolCategory category() {
                return def.category();
            }

            @Override
            public String description() {
                return def.description();
            }

            @Override
            public String inputSchemaJson() {
                return def.inputSchemaJson();
            }

            @Override
            public boolean isEnabled() {
                return enabled == null || Boolean.TRUE.equals(enabled.get());
            }

            @Override
            public boolean isReadOnly(JsonNode input) {
                return ro;
            }

            @Override
            public boolean defaultReadOnlyHint() {
                return ro;
            }
        };
    }
}
