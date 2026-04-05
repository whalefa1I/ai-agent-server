package demo.k8s.agent.happy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.query.EnhancedAgenticQueryLoop;
import demo.k8s.agent.query.AgenticTurnResult;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.toolstate.ToolArtifact;
import demo.k8s.agent.toolstate.ToolArtifactRepository;
import demo.k8s.agent.toolstate.ToolStateService;
import demo.k8s.agent.toolstate.ToolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

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
    private ToolStateService toolStateService;

    @Autowired
    private demo.k8s.agent.privacykit.PrivacyKitService privacyKitService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 工具图标映射
    private static final Map<String, String> TOOL_ICONS = Map.of(
        "file_read", "📄", "file_write", "✏️", "file_edit", "📝",
        "bash", "💻", "glob", "🔍", "grep", "🔎",
        "Task", "🤖", "k8s_sandbox_run", "☸️"
    );

    // 工具显示名称映射
    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.of(
        "file_read", "读取文件", "file_write", "写入文件", "file_edit", "编辑文件",
        "bash", "执行命令", "glob", "文件搜索", "grep", "文本搜索",
        "Task", "委派任务", "k8s_sandbox_run", "K8s 沙盒"
    );

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

            log.info("收到用户消息：sessionId={}, content={}", sessionId, content);

            String replyText = callAIWithToolCallbacks(accountId, sessionId, content);
            createAssistantMessage(accountId, sessionId, replyText);

            log.info("已创建 AI 回复：sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("处理用户消息失败：artifactId={}", artifact.getId(), e);
        }
    }

    private String callAIWithToolCallbacks(String accountId, String sessionId, String content) {
        try {
            demo.k8s.agent.observability.tracing.TraceContext.setSessionId(sessionId);
            demo.k8s.agent.observability.tracing.TraceContext.setUserId(accountId);

            AgenticTurnResult result = agenticQueryLoop.runWithCallbacks(
                content,
                (toolName, input) -> {
                    log.info("工具调用：{} ({})", toolName, input);
                    createToolCallArtifact(accountId, sessionId, toolName, input);
                },
                (delta) -> {}
            );
            return result.replyText();

        } catch (Exception e) {
            log.error("AI 处理失败：{}", content, e);
            return "抱歉，处理您的请求时出现错误：" + e.getMessage();
        } finally {
            demo.k8s.agent.observability.tracing.TraceContext.clear();
        }
    }

    private void createToolCallArtifact(String accountId, String sessionId, String toolName, JsonNode input) {
        try {
            String icon = TOOL_ICONS.getOrDefault(toolName, "🔧");
            String displayName = TOOL_DISPLAY_NAMES.getOrDefault(toolName, toolName);

            Map<String, Object> body = new HashMap<>();
            body.put("status", "started");
            body.put("timestamp", System.currentTimeMillis());
            if (input != null) {
                body.put("input", input);
            }

            ToolArtifact artifact = toolStateService.createToolArtifact(
                sessionId, accountId, toolName, "tool-call",
                ToolStatus.TODO, body, null
            );

            Map<String, Object> header = new HashMap<>();
            header.put("type", "tool-call");
            header.put("subtype", toolName);
            header.put("toolName", toolName);
            header.put("toolDisplayName", displayName);
            header.put("icon", icon);
            header.put("status", "started");
            header.put("inputSummary", input != null ? input.toString() : "");
            header.put("timestamp", System.currentTimeMillis());

            // 使用 base64 编码 header（与 Happy 兼容）
            String headerJson = objectMapper.writeValueAsString(header);
            String encryptedHeader = privacyKitService.encodeBase64(headerJson.getBytes());
            artifact.setHeader(encryptedHeader);
            repository.save(artifact);

            log.info("创建工具调用 artifact: id={}, tool={}", artifact.getId(), toolName);

        } catch (Exception e) {
            log.error("创建工具调用 artifact 失败：tool={}", toolName, e);
        }
    }

    private void createAssistantMessage(String accountId, String sessionId, String replyText) {
        try {
            String id = "assistant-" + System.currentTimeMillis();

            Map<String, Object> header = Map.of(
                "type", "message",
                "subtype", "assistant-message",
                "title", "Assistant Message",
                "timestamp", System.currentTimeMillis()
            );

            Map<String, Object> body = Map.of(
                "type", "assistant-message",
                "content", replyText,
                "timestamp", System.currentTimeMillis()
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
            log.info("创建助手消息成功：id={}, sessionId={}", id, sessionId);

        } catch (Exception e) {
            log.error("创建助手消息失败", e);
        }
    }
}
