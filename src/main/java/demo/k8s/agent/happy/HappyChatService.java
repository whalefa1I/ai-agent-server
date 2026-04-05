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

/**
 * Happy Chat Service - 处理 Happy 格式消息的自动回复
 *
 * 监听 user-message artifact 的创建，自动调用 AI 并创建 assistant-message artifact
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理 user-message artifact，触发 AI 回复
     */
    @Async
    public void processUserMessage(ToolArtifact artifact) {
        try {
            // 解析 header，确认是 user-message
            String headerJson = new String(Base64.getDecoder().decode(artifact.getHeader()));
            var header = objectMapper.readTree(headerJson);

            String type = header.has("type") ? header.get("type").asText() : "";
            String subtype = header.has("subtype") ? header.get("subtype").asText() : "";

            if (!"message".equals(type) || !"user-message".equals(subtype)) {
                return; // 不是用户消息，跳过
            }

            // 解析 body，获取用户消息内容
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

            log.info("收到用户消息：sessionId={}, content={}", artifact.getSessionId(), content);

            // 调用 AI 获取回复
            String replyText;
            try {
                AgenticTurnResult result = agenticQueryLoop.run(content);
                replyText = result.replyText();
            } catch (Exception e) {
                log.error("AI 处理失败：{}", content, e);
                replyText = "抱歉，处理您的请求时出现错误：" + e.getMessage();
            }

            // 创建 assistant-message artifact
            createAssistantMessage(artifact, replyText);

            log.info("已创建 AI 回复：sessionId={}", artifact.getSessionId());

        } catch (Exception e) {
            log.error("处理用户消息失败：artifactId={}", artifact.getId(), e);
        }
    }

    /**
     * 创建助手回复 artifact
     */
    private void createAssistantMessage(ToolArtifact userArtifact, String replyText) {
        try {
            String id = "assistant-" + System.currentTimeMillis();
            String accountId = userArtifact.getAccountId();
            String sessionId = userArtifact.getSessionId();

            // 构建 header
            var header = objectMapper.createObjectNode()
                .put("type", "message")
                .put("subtype", "assistant-message")
                .put("title", "Assistant Message")
                .put("timestamp", System.currentTimeMillis());

            // 构建 body
            var body = objectMapper.createObjectNode()
                .put("type", "assistant-message")
                .put("content", replyText)
                .put("timestamp", System.currentTimeMillis());

            // 创建 artifact
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
            log.info("创建助手消息成功：id={}, sessionId={}", id, sessionId);

        } catch (Exception e) {
            log.error("创建助手消息失败", e);
        }
    }
}
