package demo.k8s.agent.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * JSON 技能执行器
 *
 * 提供 JSON 验证、格式化、转换等功能
 */
public class JsonSkillExecutor implements SkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(JsonSkillExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String getSkillName() {
        return "json";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String action = (String) args.get("action");
        String input = (String) args.get("input");
        String schema = (String) args.get("schema");

        if (action == null || input == null) {
            return "错误：缺少必需参数 action 和 input";
        }

        try {
            return switch (action) {
                case "validate" -> validateJson(input, schema);
                case "format" -> formatJson(input);
                case "parse" -> parseJson(input);
                case "transform" -> transformJson(input, (Map<?, ?>) args.get("transforms"));
                default -> "错误：未知的 action: " + action;
            };
        } catch (Exception e) {
            log.error("JSON 操作失败", e);
            return "执行失败：" + e.getMessage();
        }
    }

    /**
     * 验证 JSON
     */
    private String validateJson(String input, String schema) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(input);

            if (schema != null && !schema.isBlank()) {
                // TODO: 实现 JSON Schema 验证
                return "JSON 有效 (Schema 验证暂未实现)";
            }

            return "JSON 格式有效";
        } catch (Exception e) {
            return "JSON 格式无效：" + e.getMessage();
        }
    }

    /**
     * 格式化 JSON
     */
    private String formatJson(String input) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(input);
            String pretty = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            return "格式化后的 JSON:\n" + pretty;
        } catch (Exception e) {
            return "无法格式化 - 无效的 JSON: " + e.getMessage();
        }
    }

    /**
     * 解析 JSON 并显示结构
     */
    private String parseJson(String input) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(input);
            StringBuilder sb = new StringBuilder();
            sb.append("JSON 结构:\n");
            appendNodeInfo(sb, jsonNode, 0);
            return sb.toString();
        } catch (Exception e) {
            return "无法解析 JSON: " + e.getMessage();
        }
    }

    /**
     * 转换 JSON
     */
    private String transformJson(String input, Map<?, ?> transforms) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(input);

            if (transforms != null) {
                // TODO: 实现转换逻辑
                return "转换功能暂未实现";
            }

            return "转换功能需要指定 transforms 参数";
        } catch (Exception e) {
            return "转换失败：" + e.getMessage();
        }
    }

    private void appendNodeInfo(StringBuilder sb, JsonNode node, int depth) {
        String indent = "  ".repeat(depth);

        if (node.isObject()) {
            sb.append(indent).append("Object with ").append(node.size()).append(" fields:\n");
            node.fields().forEachRemaining(entry -> {
                sb.append(indent).append("  - ").append(entry.getKey()).append(":\n");
                appendNodeInfo(sb, entry.getValue(), depth + 1);
            });
        } else if (node.isArray()) {
            sb.append(indent).append("Array with ").append(node.size()).append(" items:\n");
            for (int i = 0; i < node.size(); i++) {
                sb.append(indent).append("  [").append(i).append("]:\n");
                appendNodeInfo(sb, node.get(i), depth + 1);
            }
        } else {
            sb.append(indent).append(node.asText()).append("\n");
        }
    }
}
