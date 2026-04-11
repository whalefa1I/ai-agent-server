package demo.k8s.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 百炼 API 直接调用客户端，支持 reasoning_content 字段的完整解析。
 * 绕过 Spring AI 的 OpenAI 兼容层，因为后者不保留 reasoning_content 字段。
 */
@Component
public class BailianClient {
    private static final Logger logger = LoggerFactory.getLogger(BailianClient.class);

    private final RestClient restClient;
    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public BailianClient(
            @Value("${DASHSCOPE_BASE_URL:https://coding.dashscope.aliyuncs.com}") String baseUrl,
            @Value("${DASHSCOPE_API_KEY:sk-sp-ab63f62c8df3494a8763982b1a741081}") String apiKey,
            @Value("${DASHSCOPE_MODEL:qwen3.5-plus}") String model) {

        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                })
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
                })
                .build();
    }

    /**
     * 流式调用百炼 API，支持 reasoning_content 和工具调用
     * @param messages 消息列表
     * @param tools 工具定义列表（可选）
     * @param onReasoningChunk reasoning 内容增量回调
     * @param onContentChunk 普通内容增量回调
     * @param onToolCall 工具调用回调
     * @return 最终响应
     */
    public BailianResponse chatStream(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Consumer<String> onReasoningChunk,
            Consumer<String> onContentChunk,
            Consumer<ToolCall> onToolCall) {

        Instant startTime = Instant.now();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
        }

        // 用于累积响应
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        Map<String, Object> tokenCounts = new HashMap<>();
        List<ToolCall> toolCalls = new ArrayList<>();

        try {
            Flux<String> flux = webClient.post()
                    .uri("/v1/chat/completions")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToFlux(String.class);

            // 逐行处理 SSE 流
            flux.doOnNext(line -> {
                try {
                    parseSseLine(line, contentBuilder, reasoningBuilder, toolCalls,
                            onReasoningChunk, onContentChunk, onToolCall);
                } catch (Exception e) {
                    logger.error("Failed to parse SSE line: {}", line, e);
                }
            }).blockLast();

            // 提取 token 计数（从最后一个 chunk）
            tokenCounts.put("input", 0); // SSE 中通常不包含 usage
            tokenCounts.put("output", 0);
            tokenCounts.put("total", 0);

        } catch (Exception e) {
            logger.error("Stream call failed", e);
            BailianResponse errorResponse = new BailianResponse();
            errorResponse.setError("Stream call failed: " + e.getMessage());
            return errorResponse;
        }

        Duration latency = Duration.between(startTime, Instant.now());

        BailianResponse response = new BailianResponse();
        response.setContent(contentBuilder.toString());
        response.setReasoningContent(reasoningBuilder.toString());
        response.setTokenCounts(tokenCounts);
        response.setToolCalls(toolCalls.isEmpty() ? null : toolCalls);
        response.setLatencyMs(latency.toMillis());

        return response;
    }

    /**
     * 解析 SSE 流中的一行
     */
    private void parseSseLine(String line, StringBuilder contentBuilder, StringBuilder reasoningBuilder,
                               List<ToolCall> toolCalls,
                               Consumer<String> onReasoningChunk,
                               Consumer<String> onContentChunk,
                               Consumer<ToolCall> onToolCall) throws Exception {
        // 跳过空行和注释
        if (line == null || line.trim().isEmpty() || line.startsWith(":")) {
            return;
        }

        // 处理 [DONE] 标记
        if ("data: [DONE]".equals(line.trim())) {
            return;
        }

        // 提取 JSON 部分
        String jsonPart = line.startsWith("data: ") ? line.substring(6) : line;
        if (jsonPart.trim().isEmpty()) {
            return;
        }

        JsonNode chunk = objectMapper.readTree(jsonPart);

        // 提取 choices
        JsonNode choices = chunk.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            return;
        }

        JsonNode delta = choices.get(0).get("delta");
        if (delta == null) {
            return;
        }

        // 提取 reasoning_content 增量
        JsonNode reasoningNode = delta.get("reasoning_content");
        if (reasoningNode != null && !reasoningNode.isNull()) {
            String reasoningChunk = reasoningNode.asText();
            if (!reasoningChunk.isEmpty()) {
                reasoningBuilder.append(reasoningChunk);
                if (onReasoningChunk != null) {
                    onReasoningChunk.accept(reasoningChunk);
                }
                logger.debug("Reasoning chunk: {}", reasoningChunk);
            }
        }

        // 提取普通 content 增量
        JsonNode contentNode = delta.get("content");
        if (contentNode != null && !contentNode.isNull()) {
            String contentChunk = contentNode.asText();
            if (!contentChunk.isEmpty()) {
                contentBuilder.append(contentChunk);
                if (onContentChunk != null) {
                    onContentChunk.accept(contentChunk);
                }
                logger.debug("Content chunk: {}", contentChunk);
            }
        }

        // 提取工具调用
        JsonNode toolCallsNode = delta.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            for (JsonNode toolCallNode : toolCallsNode) {
                ToolCall toolCall = parseToolCallNode(toolCallNode);
                if (toolCall != null) {
                    toolCalls.add(toolCall);
                    if (onToolCall != null) {
                        onToolCall.accept(toolCall);
                    }
                    logger.info("Tool call detected: {}", toolCall.getFunction().getName());
                }
            }
        }
    }

    /**
     * 解析工具调用节点
     */
    private ToolCall parseToolCallNode(JsonNode toolCallNode) throws JsonProcessingException {
        ToolCall toolCall = new ToolCall();

        JsonNode idNode = toolCallNode.get("id");
        if (idNode != null) {
            toolCall.setId(idNode.asText());
        }

        JsonNode typeNode = toolCallNode.get("type");
        if (typeNode != null) {
            toolCall.setType(typeNode.asText());
        }

        JsonNode functionNode = toolCallNode.get("function");
        if (functionNode != null) {
            FunctionCall function = new FunctionCall();
            JsonNode nameNode = functionNode.get("name");
            if (nameNode != null) {
                function.setName(nameNode.asText());
            }
            JsonNode argsNode = functionNode.get("arguments");
            if (argsNode != null) {
                function.setArguments(argsNode.asText());
            }
            toolCall.setFunction(function);
        }

        return toolCall;
    }

    /**
     * 调用百炼 API 并解析包含 reasoning_content 的响应（非流式）
     */
    public BailianResponse chat(List<Map<String, Object>> messages) {
        return chat(messages, null);
    }

    /**
     * 调用百炼 API 并解析包含 reasoning_content 的响应（非流式）
     * @param messages 消息列表
     * @param tools 工具定义列表（可选）
     * @return 响应
     */
    public BailianResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Instant startTime = Instant.now();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);

        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
        }

        String rawResponse = restClient.post()
                .uri("/v1/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        Duration latency = Duration.between(startTime, Instant.now());

        return parseResponse(rawResponse, latency.toMillis());
    }

    /**
     * 解析百炼 API 响应，提取 reasoning_content 和工具调用
     */
    private BailianResponse parseResponse(String rawResponse, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            // 提取 choices[0].message.content
            JsonNode choices = root.get("choices");
            String content = "";
            String reasoningContent = "";
            List<ToolCall> toolCalls = null;

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode contentNode = message.get("content");
                    if (contentNode != null && !contentNode.isNull()) {
                        content = contentNode.asText();
                    }
                    // 关键：提取 reasoning_content 字段
                    JsonNode reasoningNode = message.get("reasoning_content");
                    if (reasoningNode != null && !reasoningNode.isNull()) {
                        reasoningContent = reasoningNode.asText();
                        logger.debug("Found reasoning_content: {} chars", reasoningContent.length());
                    }

                    // 提取工具调用
                    JsonNode toolCallsNode = message.get("tool_calls");
                    if (toolCallsNode != null && toolCallsNode.isArray()) {
                        toolCalls = new ArrayList<>();
                        for (JsonNode toolCallNode : toolCallsNode) {
                            ToolCall toolCall = parseToolCallNode(toolCallNode);
                            if (toolCall != null) {
                                toolCalls.add(toolCall);
                            }
                        }
                    }
                }
            }

            // 提取 token 使用情况
            JsonNode usage = root.get("usage");
            Map<String, Object> tokenCounts = new HashMap<>();
            if (usage != null) {
                if (usage.has("prompt_tokens")) {
                    tokenCounts.put("input", usage.get("prompt_tokens").asInt());
                }
                if (usage.has("completion_tokens")) {
                    tokenCounts.put("output", usage.get("completion_tokens").asInt());
                }
                if (usage.has("total_tokens")) {
                    tokenCounts.put("total", usage.get("total_tokens").asInt());
                }

                // 提取 reasoning_tokens（如果有）
                JsonNode completionTokensDetails = usage.get("completion_tokens_details");
                if (completionTokensDetails != null && completionTokensDetails.has("reasoning_tokens")) {
                    tokenCounts.put("reasoning_tokens", completionTokensDetails.get("reasoning_tokens").asInt());
                }
            }

            BailianResponse response = new BailianResponse();
            response.setContent(content);
            response.setReasoningContent(reasoningContent);
            response.setTokenCounts(tokenCounts);
            response.setToolCalls(toolCalls);
            response.setLatencyMs(latencyMs);
            response.setRawResponse(rawResponse);

            return response;

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse Bailian response", e);
            BailianResponse errorResponse = new BailianResponse();
            errorResponse.setError("Failed to parse response: " + e.getMessage());
            errorResponse.setRawResponse(rawResponse);
            return errorResponse;
        }
    }

    /**
     * 百炼 API 响应对象
     */
    public static class BailianResponse {
        private String content;
        private String reasoningContent;
        private Map<String, Object> tokenCounts = new HashMap<>();
        private Long latencyMs;
        private String rawResponse;
        private String error;
        private List<ToolCall> toolCalls;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getReasoningContent() {
            return reasoningContent;
        }

        public void setReasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
        }

        public Map<String, Object> getTokenCounts() {
            return tokenCounts;
        }

        public void setTokenCounts(Map<String, Object> tokenCounts) {
            this.tokenCounts = tokenCounts;
        }

        public Long getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public void setRawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public boolean hasReasoningContent() {
            return reasoningContent != null && !reasoningContent.isEmpty();
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    /**
     * 工具调用对象
     */
    public static class ToolCall {
        private String id;
        private String type = "function";
        private FunctionCall function;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public FunctionCall getFunction() {
            return function;
        }

        public void setFunction(FunctionCall function) {
            this.function = function;
        }
    }

    /**
     * 函数调用对象
     */
    public static class FunctionCall {
        private String name;
        private String arguments;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }
}
