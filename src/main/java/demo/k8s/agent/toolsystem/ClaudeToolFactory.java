package demo.k8s.agent.toolsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * 与 Claude Code {@code buildTool(def)} 对齐：将 {@link ToolDefPartial} 合并为完整 {@link ClaudeLikeTool}。
 */
public final class ClaudeToolFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClaudeToolFactory() {}

    /**
     * 构建基础工具定义（无自定义权限检查）
     */
    public static ClaudeLikeTool buildTool(ToolDefPartial def) {
        return buildTool(def, null, null);
    }

    /**
     * 构建工具定义，支持自定义权限检查和输入验证
     *
     * @param def 工具定义
     * @param permissionChecker 权限检查函数 (inputJson, ctx) -> PermissionResult
     * @param inputValidator 输入验证函数 (input) -> errorMessage (null = 通过)
     */
    public static ClaudeLikeTool buildTool(
            ToolDefPartial def,
            BiFunction<String, ToolPermissionContext, PermissionResult> permissionChecker,
            Function<JsonNode, String> inputValidator) {

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

            @Override
            public PermissionResult checkPermissions(String argumentsJson, ToolPermissionContext ctx) {
                if (permissionChecker != null) {
                    return permissionChecker.apply(argumentsJson, ctx);
                }
                return ClaudeLikeTool.super.checkPermissions(argumentsJson, ctx);
            }

            @Override
            public String validateInput(JsonNode input) {
                if (inputValidator != null) {
                    return inputValidator.apply(input);
                }
                return ClaudeLikeTool.super.validateInput(input);
            }
        };
    }

    /**
     * 将 ClaudeLikeTool 转换为 Spring AI ToolCallback
     * 用于将 Skills 和 MCP 工具集成到 Spring AI 工具系统
     */
    public static ToolCallback toToolCallback(ClaudeLikeTool claudeTool) {
        return toToolCallback(claudeTool, null);
    }

    /**
     * 将 ClaudeLikeTool 转换为 Spring AI ToolCallback，带自定义执行函数
     * @param claudeTool 工具定义
     * @param executor 执行函数 (inputJson) -> resultJson
     */
    public static ToolCallback toToolCallback(ClaudeLikeTool claudeTool, Function<String, String> executor) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(claudeTool.name())
                .description(claudeTool.description())
                .inputSchema(claudeTool.inputSchemaJson())
                .build();

        Function<String, String> actualExecutor = executor != null ? executor :
            (Function<String, String>) input -> {
                // 占位响应
                return "{\"success\": true, \"tool\": \"" + claudeTool.name() + "\", \"input\": " + input + "}";
            };

        return FunctionToolCallback.builder(
                claudeTool.name(),
                actualExecutor)
                .description(claudeTool.description())
                .inputSchema(claudeTool.inputSchemaJson())
                .build();
    }
}
