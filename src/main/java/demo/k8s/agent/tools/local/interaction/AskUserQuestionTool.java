package demo.k8s.agent.tools.local.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AskUserQuestion 工具 - 向用户提问并获取回答
 *
 * 与 claude-code 的 AskUserQuestionTool 对齐，支持：
 * - 单选/多选问题
 * - 2-4 个选项
 * - 可选的 preview 预览内容
 * - 可选的 annotations 注释
 */
public class AskUserQuestionTool {

    private static final Logger log = LoggerFactory.getLogger(AskUserQuestionTool.class);

    /**
     * 问题选项
     */
    public static class QuestionOption {
        public String label;
        public String description;
        public String preview;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("label", label);
            map.put("description", description);
            if (preview != null && !preview.isBlank()) {
                map.put("preview", preview);
            }
            return map;
        }
    }

    /**
     * 问题结构
     */
    public static class Question {
        public String question;
        public String header;
        public List<QuestionOption> options;
        public boolean multiSelect;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("question", question);
            map.put("header", header);
            map.put("options", options.stream().map(QuestionOption::toMap).toList());
            map.put("multiSelect", multiSelect);
            return map;
        }
    }

    /**
     * 用户回答
     */
    public static class UserAnswer {
        public String questionText;
        public String answer;
        public Map<String, String> annotations;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("question", questionText);
            map.put("answer", answer);
            if (annotations != null && !annotations.isEmpty()) {
                map.put("annotations", annotations);
            }
            return map;
        }
    }

    /**
     * 等待回答的请求
     */
    public static class PendingRequest {
        public final String id;
        public final List<Question> questions;
        public final CountDownLatch latch;
        public final AtomicReference<Map<String, String>> answers;
        public final long createdAt;

        public PendingRequest(String id, List<Question> questions) {
            this.id = id;
            this.questions = questions;
            this.latch = new CountDownLatch(1);
            this.answers = new AtomicReference<>();
            this.createdAt = System.currentTimeMillis();
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        public void respond(Map<String, String> answers) {
            this.answers.set(answers);
            latch.countDown();
        }

        public Map<String, String> getAnswers() {
            return answers.get();
        }
    }

    /**
     * 等待回答的请求存储（会话级别）
     */
    private static final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"questions\": {" +
            "      \"type\": \"array\"," +
            "      \"items\": {" +
            "        \"type\": \"object\"," +
            "        \"properties\": {" +
            "          \"question\": {\"type\": \"string\", \"description\": \"The complete question to ask the user\"}," +
            "          \"header\": {\"type\": \"string\", \"description\": \"Very short label displayed as a chip/tag (max 12 chars)\"}," +
            "          \"options\": {" +
            "            \"type\": \"array\"," +
            "            \"items\": {" +
            "              \"type\": \"object\"," +
            "              \"properties\": {" +
            "                \"label\": {\"type\": \"string\", \"description\": \"The display text for this option (1-5 words)\"}," +
            "                \"description\": {\"type\": \"string\", \"description\": \"Explanation of what this option means\"}," +
            "                \"preview\": {\"type\": \"string\", \"description\": \"Optional preview content\"}" +
            "              }," +
            "              \"required\": [\"label\", \"description\"]" +
            "            }," +
            "            \"minItems\": 2," +
            "            \"maxItems\": 4" +
            "          }," +
            "          \"multiSelect\": {\"type\": \"boolean\", \"description\": \"Set to true to allow multiple selections\"}" +
            "        }," +
            "        \"required\": [\"question\", \"header\", \"options\", \"multiSelect\"]" +
            "      }," +
            "      \"minItems\": 1," +
            "      \"maxItems\": 4" +
            "    }," +
            "    \"metadata\": {" +
            "      \"type\": \"object\"," +
            "      \"properties\": {" +
            "        \"source\": {\"type\": \"string\", \"description\": \"Optional identifier for the source of this question\"}" +
            "      }" +
            "    }" +
            "  }," +
            "  \"required\": [\"questions\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "AskUserQuestion",
                        ToolCategory.PLANNING,
                        "Ask the user a question with multiple choice options",
                        INPUT_SCHEMA,
                        null,
                        false),
                // 权限检查 - 需要用户确认（因为要打断用户）
                (json, ctx) -> null,
                // 输入验证
                AskUserQuestionTool::validateInput);
    }

    /**
     * 输入验证
     */
    public static String validateInput(JsonNode input) {
        if (!input.has("questions")) {
            return "questions is required";
        }

        JsonNode questionsNode = input.get("questions");
        if (!questionsNode.isArray()) {
            return "questions must be an array";
        }

        if (questionsNode.size() < 1 || questionsNode.size() > 4) {
            return "questions must have 1-4 items";
        }

        for (JsonNode qNode : questionsNode) {
            if (!qNode.has("question") || qNode.get("question").asText("").isBlank()) {
                return "Each question must have a non-empty 'question' field";
            }

            if (!qNode.has("header") || qNode.get("header").asText("").isBlank()) {
                return "Each question must have a non-empty 'header' field";
            }

            if (qNode.get("header").asText("").length() > 12) {
                return "header must be at most 12 characters";
            }

            if (!qNode.has("options") || !qNode.get("options").isArray()) {
                return "Each question must have an 'options' array";
            }

            JsonNode optionsNode = qNode.get("options");
            if (optionsNode.size() < 2 || optionsNode.size() > 4) {
                return "Each question must have 2-4 options";
            }

            Set<String> labels = new HashSet<>();
            for (JsonNode optNode : optionsNode) {
                if (!optNode.has("label") || optNode.get("label").asText("").isBlank()) {
                    return "Each option must have a non-empty 'label' field";
                }
                if (!optNode.has("description") || optNode.get("description").asText("").isBlank()) {
                    return "Each option must have a non-empty 'description' field";
                }

                String label = optNode.get("label").asText("");
                if (labels.contains(label)) {
                    return "Option labels must be unique within each question: " + label;
                }
                labels.add(label);
            }
        }

        // 检查问题是否唯一
        Set<String> questionTexts = new HashSet<>();
        for (JsonNode qNode : questionsNode) {
            String qText = qNode.get("question").asText("");
            if (questionTexts.contains(qText)) {
                return "Question texts must be unique: " + qText;
            }
            questionTexts.add(qText);
        }

        return null;
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionsInput = (List<Map<String, Object>>) input.get("questions");

            if (questionsInput == null || questionsInput.isEmpty()) {
                return LocalToolResult.error("questions is required and cannot be empty");
            }

            // 解析问题
            List<Question> questions = new ArrayList<>();
            for (Map<String, Object> qInput : questionsInput) {
                Question q = new Question();
                q.question = (String) qInput.get("question");
                q.header = (String) qInput.get("header");
                q.multiSelect = Boolean.TRUE.equals(qInput.get("multiSelect"));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> optionsInput = (List<Map<String, Object>>) qInput.get("options");
                q.options = new ArrayList<>();
                for (Map<String, Object> optInput : optionsInput) {
                    QuestionOption opt = new QuestionOption();
                    opt.label = (String) optInput.get("label");
                    opt.description = (String) optInput.get("description");
                    opt.preview = (String) optInput.get("preview");
                    q.options.add(opt);
                }

                questions.add(q);
            }

            // 创建请求 ID
            String requestId = "question-" + UUID.randomUUID().toString().substring(0, 8);

            // 创建等待请求
            PendingRequest request = new PendingRequest(requestId, questions);
            pendingRequests.put(requestId, request);

            log.info("创建用户问题请求：{}, 共 {} 个问题", requestId, questions.size());

            // 构建输出（包含请求 ID，前端需要显示问题并等待用户回答）
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("requestId", requestId);
            output.put("questions", questions.stream().map(Question::toMap).toList());

            // 返回需要等待用户回答的状态
            return LocalToolResult.builder()
                    .success(true)
                    .content("Waiting for user response...")
                    .executionLocation("local")
                    .metadata(new ObjectMapper().valueToTree(output))
                    .build();

        } catch (Exception e) {
            log.error("AskUserQuestion 执行失败", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 响应用户问题
     *
     * @param requestId 请求 ID
     * @param answers 用户回答（问题文本 -> 答案）
     * @return 响应结果
     */
    public static LocalToolResult respond(String requestId, Map<String, String> answers) {
        PendingRequest request = pendingRequests.get(requestId);
        if (request == null) {
            return LocalToolResult.error("Request not found: " + requestId);
        }

        // 验证回答
        if (answers == null || answers.isEmpty()) {
            return LocalToolResult.error("answers is required");
        }

        // 验证每个问题都有回答
        for (Question q : request.questions) {
            if (!answers.containsKey(q.question)) {
                return LocalToolResult.error("Missing answer for question: " + q.question);
            }
        }

        // 响应请求
        request.respond(answers);
        pendingRequests.remove(requestId);

        log.info("用户问题请求已响应：{}", requestId);

        // 构建输出
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("requestId", requestId);
        output.put("questions", request.questions.stream().map(Question::toMap).toList());
        output.put("answers", answers);

        return LocalToolResult.builder()
                .success(true)
                .content("User responded to " + answers.size() + " question(s)")
                .executionLocation("local")
                .metadata(new ObjectMapper().valueToTree(output))
                .build();
    }

    /**
     * 获取等待的回答
     *
     * @param requestId 请求 ID
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 用户回答，如果超时则返回 null
     */
    public static Map<String, String> getAnswers(String requestId, long timeout, TimeUnit unit) {
        PendingRequest request = pendingRequests.get(requestId);
        if (request == null) {
            return null;
        }

        try {
            if (request.await(timeout, unit)) {
                return request.getAnswers();
            }
            return null; // 超时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 获取所有待处理的请求
     */
    public static List<PendingRequest> getPendingRequests() {
        return new ArrayList<>(pendingRequests.values());
    }

    /**
     * 清除过期的请求
     */
    public static void cleanupExpiredRequests(long maxAgeMs) {
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry ->
            (now - entry.getValue().createdAt) > maxAgeMs
        );
    }
}
