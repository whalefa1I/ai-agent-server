package demo.k8s.agent.tools.local.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExitPlanMode 工具 - 退出计划模式并提交计划
 *
 * 功能：
 * - 退出计划模式
 * - 提交最终计划供用户确认
 *
 * 与 claude-code 的 ExitPlanMode 对齐
 */
public class ExitPlanModeTool {

    private static final Logger log = LoggerFactory.getLogger(ExitPlanModeTool.class);

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"plan\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"The plan you came up with\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"plan\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "ExitPlanMode",
                        ToolCategory.PLANNING,
                        "Exit plan mode and submit the plan for user approval",
                        INPUT_SCHEMA,
                        null,
                        false),
                // 权限检查 - 需要用户确认（因为要提交计划）
                (json, ctx) -> null,
                // 输入验证
                ExitPlanModeTool::validateInput);
    }

    /**
     * 输入验证
     */
    public static String validateInput(JsonNode input) {
        if (!input.has("plan") || input.get("plan").asText("").isBlank()) {
            return "plan is required and cannot be empty";
        }
        return null;
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        try {
            String plan = (String) input.get("plan");

            if (plan == null || plan.isBlank()) {
                return LocalToolResult.error("plan is required and cannot be empty");
            }

            log.info("退出计划模式，计划长度：{} 字符", plan.length());

            // 构建输出
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("plan", plan);

            return LocalToolResult.builder()
                    .success(true)
                    .content("Plan submitted for approval")
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("ExitPlanMode 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }
}
