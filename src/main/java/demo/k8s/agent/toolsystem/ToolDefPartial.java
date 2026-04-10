package demo.k8s.agent.toolsystem;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 与 Claude Code 中 {@code ToolDef}（部分字段）对应：必填元数据 + 可选覆盖；其余由 {@link ClaudeToolFactory#buildTool(ToolDefPartial)} 补全为
 * {@link ClaudeLikeTool} 默认值（对齐 TS {@code TOOL_DEFAULTS} / {@code buildTool}）。
 *
 * @param isEnabled 为 {@code null} 时等价于始终 {@code true}
 * @param concurrencySafe 为 {@code true} 时表示工具可与其他并发安全工具并行执行（只读工具通常为 true）
 */
public record ToolDefPartial(
        String name,
        ToolCategory category,
        String description,
        String inputSchemaJson,
        Supplier<Boolean> isEnabled,
        boolean readOnly,
        boolean concurrencySafe) {

    public ToolDefPartial {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputSchemaJson, "inputSchemaJson");
    }

    /** 最小构造：启用、非只读、非并发安全 */
    public static ToolDefPartial of(String name, ToolCategory category, String description, String inputSchemaJson) {
        return new ToolDefPartial(name, category, description, inputSchemaJson, null, false, false);
    }

    /** 只读工具构造：启用、只读、并发安全 */
    public static ToolDefPartial ofReadOnly(String name, ToolCategory category, String description, String inputSchemaJson) {
        return new ToolDefPartial(name, category, description, inputSchemaJson, null, true, true);
    }
}
