package demo.k8s.agent.happy;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.query.EnhancedAgenticQueryLoop;
import demo.k8s.agent.query.AgenticTurnResult;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.toolstate.ToolArtifact;
import demo.k8s.agent.toolstate.ToolArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Happy Chat Service - 处理 Happy 格式消息的自动回复
 */
@Service
public class HappyChatService {

    private static final Logger log = LoggerFactory.getLogger(HappyChatService.class);

    @Autowired
    private ToolArtifactRepository repository;

    @Autowired
    private EnhancedAgenticQueryLoop agenticQueryLoop;

    @Autowired
    private ConversationManager conversationManager;

    @Autowired
    private demo.k8s.agent.privacykit.PrivacyKitService privacyKitService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public void processUserMessage(ToolArtifact artifact) {
        try {
            String headerJson = new String(Base64.getDecoder().decode(artifact.getHeader()));
            var header = objectMapper.readTree(headerJson);

            String type = header.has("type") ? header.get("type").asText() : "";
            String subtype = header.has("subtype") ? header.get("subtype").asText() : "";

            if (!"message".equals(type) || !"user-message".equals(subtype)) {
                return;
            }

            if (artifact.getBody() == null || artifact.getBody().isEmpty()) {
                log.warn("User message artifact body is empty: {}", artifact.getId());
                return;
            }

            String bodyJson = new String(Base64.getDecoder().decode(artifact.getBody()));
            var body = objectMapper.readTree(bodyJson);

            String content = body.has("content") ? body.get("content").asText() : "";
            if (content.isEmpty()) {
                log.warn("User message content is empty: {}", artifact.getId());
                return;
            }

            String sessionId = artifact.getSessionId();
            String accountId = artifact.getAccountId();
            String userTurnId = "user-turn-" + UUID.randomUUID();
            ConversationManager.TurnContext turnContext = conversationManager.startTurn(content);

            log.info("收到用户消息：sessionId={}, userTurnId={}, content={}", sessionId, userTurnId, content);

            // 创建"正在生成中"的 assistant-message artifact（用于流式输出）
            String assistantArtifactId = createStreamingAssistantMessage(accountId, sessionId, userTurnId);

            // 调用 AI，支持流式输出和工具调用
            String replyText = callAIWithToolCallbacks(accountId, sessionId, userTurnId, content, assistantArtifactId);

            // 更新最终消息
            updateAssistantMessage(assistantArtifactId, replyText, true, userTurnId);
            conversationManager.completeTurn(turnContext, replyText, java.util.List.of());

            log.info("已创建 AI 回复：sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("处理用户消息失败：artifactId={}", artifact.getId(), e);
        }
    }

    private String createStreamingThinkingMessage(String accountId, String sessionId, String userTurnId) {
        try {
            String id = "thinking-" + System.currentTimeMillis();
            Map<String, Object> header = Map.of(
                "type", "message",
                "subtype", "thinking-message",
                "title", "Thinking Message",
                "timestamp", System.currentTimeMillis(),
                "status", "streaming"
            );
            Map<String, Object> body = Map.of(
                "type", "thinking-message",
                "content", "",
                "timestamp", System.currentTimeMillis(),
                "status", "streaming",
                "metadata", Map.of("userTurnId", userTurnId)
            );
            ToolArtifact artifact = new ToolArtifact();
            artifact.setId(id);
            artifact.setAccountId(accountId);
            artifact.setSessionId(sessionId);
            artifact.setHeader(Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(header)));
            artifact.setBody(Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(body)));
            artifact.setHeaderVersion(1);
            artifact.setBodyVersion(1);
            artifact.setSeq(0);
            artifact.setCreatedAt(Instant.now());
            artifact.setUpdatedAt(Instant.now());
            repository.save(artifact);
            return id;
        } catch (Exception e) {
            log.error("创建 thinking 消息失败", e);
            return null;
        }
    }

    private void createAssistantProgressMessage(String accountId, String sessionId, String content, String userTurnId) {
        createAssistantProgressMessage(
                accountId, sessionId, content, "assistant-progress-message", "Assistant Progress Message",
                Map.of("userTurnId", userTurnId));
    }

    private void createAssistantProgressMessage(
            String accountId,
            String sessionId,
            String content,
            String subtype,
            String title) {
        createAssistantProgressMessage(accountId, sessionId, content, subtype, title, Map.of());
    }

    private void createAssistantProgressMessage(
            String accountId,
            String sessionId,
            String content,
            String subtype,
            String title,
            Map<String, Object> metadata) {
        try {
            if (content == null || content.isBlank()) return;
            String id = "assistant-progress-" + System.currentTimeMillis();
            Map<String, Object> header = Map.of(
                "type", "message",
                "subtype", subtype,
                "title", title,
                "timestamp", System.currentTimeMillis(),
                "status", "completed"
            );
            Map<String, Object> body = Map.of(
                "type", subtype,
                "content", content,
                "timestamp", System.currentTimeMillis(),
                "status", "completed",
                "metadata", metadata == null ? Map.of() : metadata
            );
            ToolArtifact artifact = new ToolArtifact();
            artifact.setId(id);
            artifact.setAccountId(accountId);
            artifact.setSessionId(sessionId);
            artifact.setHeader(Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(header)));
            artifact.setBody(Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(body)));
            artifact.setHeaderVersion(1);
            artifact.setBodyVersion(1);
            artifact.setSeq(0);
            artifact.setCreatedAt(Instant.now());
            artifact.setUpdatedAt(Instant.now());
            repository.save(artifact);
        } catch (Exception e) {
            log.error("创建 assistant-progress 消息失败", e);
        }
    }

    /**
     * 创建"正在生成中"的助手消息 artifact
     */
    private String createStreamingAssistantMessage(String accountId, String sessionId, String userTurnId) {
        try {
            String id = "assistant-" + System.currentTimeMillis();

            Map<String, Object> header = Map.of(
                "type", "message",
                "subtype", "assistant-message",
                "title", "Assistant Message",
                "timestamp", System.currentTimeMillis(),
                "status", "streaming"
            );

            Map<String, Object> body = Map.of(
                "type", "assistant-message",
                "content", "",  // 初始内容为空
                "timestamp", System.currentTimeMillis(),
                "status", "streaming",
                "metadata", Map.of("userTurnId", userTurnId)
            );

            ToolArtifact artifact = new ToolArtifact();
            artifact.setId(id);
            artifact.setAccountId(accountId);
            artifact.setSessionId(sessionId);
            artifact.setHeader(Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsBytes(header)));
            artifact.setBody(Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsBytes(body)));
            artifact.setHeaderVersion(1);
            artifact.setBodyVersion(1);
            artifact.setSeq(0);
            artifact.setCreatedAt(Instant.now());
            artifact.setUpdatedAt(Instant.now());

            repository.save(artifact);
            log.info("创建流式助手消息：id={}, sessionId={}", id, sessionId);

            return id;

        } catch (Exception e) {
            log.error("创建流式助手消息失败", e);
            return null;
        }
    }

    /**
     * 更新助手消息内容（流式更新）
     */
    private void updateAssistantMessage(String artifactId, String content, boolean isFinal, String userTurnId) {
        if (artifactId == null) return;

        try {
            ToolArtifact artifact = repository.findById(artifactId).orElse(null);
            if (artifact == null) return;

            Map<String, Object> header = Map.of(
                "type", "message",
                "subtype", "assistant-message",
                "title", "Assistant Message",
                "timestamp", System.currentTimeMillis(),
                "status", isFinal ? "completed" : "streaming"
            );

            Map<String, Object> body = Map.of(
                "type", "assistant-message",
                "content", content != null ? content : "",
                "timestamp", System.currentTimeMillis(),
                "status", isFinal ? "completed" : "streaming",
                "metadata", Map.of("userTurnId", userTurnId)
            );

            artifact.setHeader(Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsBytes(header)));
            artifact.setBody(Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsBytes(body)));
            artifact.setBodyVersion(artifact.getBodyVersion() + 1);
            artifact.setUpdatedAt(Instant.now());

            repository.save(artifact);
            if (isFinal) {
                log.info("更新助手消息：id={}, contentLength={}, isFinal=true",
                        artifactId, content != null ? content.length() : 0);
            } else {
                log.debug("更新助手消息（流式增量）：id={}, contentLength={}",
                        artifactId, content != null ? content.length() : 0);
            }

        } catch (Exception e) {
            log.error("更新助手消息失败：artifactId={}", artifactId, e);
        }
    }

    private void updateThinkingMessage(String artifactId, String content, boolean isFinal, String userTurnId) {
        if (artifactId == null) return;
        try {
            ToolArtifact artifact = repository.findById(artifactId).orElse(null);
            if (artifact == null) return;
            Map<String, Object> header = Map.of(
                "type", "message",
                "subtype", "thinking-message",
                "title", "Thinking Message",
                "timestamp", System.currentTimeMillis(),
                "status", isFinal ? "completed" : "streaming"
            );
            Map<String, Object> body = Map.of(
                "type", "thinking-message",
                "content", content != null ? content : "",
                "timestamp", System.currentTimeMillis(),
                "status", isFinal ? "completed" : "streaming",
                "metadata", Map.of("userTurnId", userTurnId)
            );
            artifact.setHeader(Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(header)));
            artifact.setBody(Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(body)));
            artifact.setBodyVersion(artifact.getBodyVersion() + 1);
            artifact.setUpdatedAt(Instant.now());
            repository.save(artifact);
        } catch (Exception e) {
            log.error("更新 thinking 消息失败：artifactId={}", artifactId, e);
        }
    }

    private String callAIWithToolCallbacks(
            String accountId,
            String sessionId,
            String userTurnId,
            String content,
            String assistantArtifactId) {
        try {
            demo.k8s.agent.observability.tracing.TraceContext.setSessionId(sessionId);
            demo.k8s.agent.observability.tracing.TraceContext.setUserId(accountId);
            log.info("[DEBUG] TraceContext 已设置：sessionId={}, userId={}", sessionId, accountId);

            // 用于累积流式内容
            AtomicReference<StringBuilder> contentBuffer = new AtomicReference<>(new StringBuilder());

            log.info("[DEBUG] 开始调用 agenticQueryLoop.runWithCallbacks");
            AtomicReference<StringBuilder> thinkingBuffer = new AtomicReference<>(new StringBuilder());
            String thinkingArtifactId = createStreamingThinkingMessage(accountId, sessionId, userTurnId);
            AgenticTurnResult result = agenticQueryLoop.runWithCallbacks(
                content,
                (toolName, input) -> {
                    log.info("[DEBUG] 工具调用回调被触发：toolName={}", toolName);
                    log.info("工具调用：{} ({})", toolName, input);
                    // 注意：UnifiedToolExecutor 会自动创建 tool-call artifact，这里不需要重复创建
                },
                (delta) -> {
                    // 流式输出回调：累积内容并更新 artifact
                    if (delta != null && !delta.isEmpty()) {
                        contentBuffer.get().append(delta);
                        updateAssistantMessage(assistantArtifactId, contentBuffer.get().toString(), false, userTurnId);
                    }
                },
                (reasoningDelta) -> {
                    if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
                        thinkingBuffer.get().append(reasoningDelta);
                        updateThinkingMessage(thinkingArtifactId, thinkingBuffer.get().toString(), false, userTurnId);
                    }
                },
                (intermediateText) -> {
                    createAssistantProgressMessage(accountId, sessionId, intermediateText, userTurnId);
                },
                (stateEvent) -> {
                    String subtype = stateEvent != null && stateEvent.subtype() != null
                            ? stateEvent.subtype()
                            : "assistant-loop-state-message";
                    String title = "compact-boundary-message".equals(subtype) ? "Compact Boundary" : "Loop State";
                    String text = stateEvent != null ? stateEvent.content() : "";
                    Map<String, Object> metadata = stateEvent != null && stateEvent.metadata() != null
                            ? stateEvent.metadata()
                            : Map.of();
                    Map<String, Object> mergedMetadata = new HashMap<>(metadata);
                    mergedMetadata.put("userTurnId", userTurnId);
                    createAssistantProgressMessage(accountId, sessionId, text, subtype, title, mergedMetadata);
                }
            );
            updateThinkingMessage(thinkingArtifactId, thinkingBuffer.get().toString(), true, userTurnId);
            log.info("[DEBUG] agenticQueryLoop.runWithCallbacks 完成，replyText={}", result.replyText());
            return result.replyText();

        } catch (Exception e) {
            log.error("AI 处理失败：{}", content, e);
            return "抱歉，处理您的请求时出现错误：" + e.getMessage();
        } finally {
            demo.k8s.agent.observability.tracing.TraceContext.clear();
        }
    }
}
