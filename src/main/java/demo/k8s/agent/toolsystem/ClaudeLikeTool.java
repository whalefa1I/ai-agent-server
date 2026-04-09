package demo.k8s.agent.toolsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 与 Claude Code {@code Tool} 对齐的「元数据 + 行为」接口：JSON Schema、权限位、校验。
 * 执行本身由 {@link org.springframework.ai.tool.ToolCallback} 完成，二者通过 {@link ToolModule} 绑定。
 *
 * <p>默认值与 {@code src/Tool.ts} 中 {@code TOOL_DEFAULTS} / {@code buildTool} 一致：
 * <ul>
 *   <li>{@link #isEnabled()} → {@code true}</li>
 *   <li>{@link #isConcurrencySafe(JsonNode)} → {@code false}</li>
 *   <li>{@link #isReadOnly(JsonNode)} → {@code false}</li>
 *   <li>{@link #isDestructive(JsonNode)} → {@code false}</li>
 * </ul>
 */
public interface ClaudeLikeTool {

    String name();

    ToolCategory category();

    /** 展示给模型的工具说明 */
    String description();

    /**
     * 工具入参的 JSON Schema（OpenAPI 风格 object），与 Claude Code 中 Zod 转 Schema 对应。
     */
    String inputSchemaJson();

    /** 是否向当前会话暴露该工具 */
    default boolean isEnabled() {
        return true;
    }

    /** 是否可在并行回合中与其它调用并发（默认保守为 false） */
    default boolean isConcurrencySafe(JsonNode input) {
        return false;
    }

    /** 是否不修改外部状态（读操作）；用于 READ_ONLY 模式过滤 */
    default boolean isReadOnly(JsonNode input) {
        return false;
    }

    /** 是否不可逆（删库、覆盖、外发等） */
    default boolean isDestructive(JsonNode input) {
        return false;
    }

    /**
     * 与 Claude Code {@code validateInput} 类似：执行前校验。
     *
     * @return {@code null} 表示通过；否则为错误信息
     */
    default String validateInput(JsonNode input) {
        return null;
    }

    /**
     * 与 Claude Code {@code checkPermissions} 对齐；默认放行并原样返回 JSON 字符串。
     */
    default PermissionResult checkPermissions(String argumentsJson, ToolPermissionContext ctx) {
        return PermissionResult.allow(argumentsJson);
    }

    /** 供过滤：无具体 input 时是否视为只读工具 */
    default boolean defaultReadOnlyHint() {
        return false;
    }

    static JsonNode parseInput(String argumentsJson, ObjectMapper mapper) {
        try {
            return mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid tool JSON: " + e.getMessage(), e);
        }
    }
}
