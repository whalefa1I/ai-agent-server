package demo.k8s.agent.state;

import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event.SessionCreatedEvent;
import demo.k8s.agent.observability.events.Event.SessionEndedEvent;
import demo.k8s.agent.observability.logging.StructuredLogger;
import demo.k8s.agent.observability.metrics.MetricsCollector;
import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 对话会话管理器，与 Claude Code 的 QueryEngine 状态管理对齐。
 */
@Service
public class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    private final ConversationSession currentSession;
    private final ConversationRepository repository;
    private final EventBus eventBus;
    private final MetricsCollector metricsCollector;

    public ConversationManager(
            ConversationSession currentSession,
            ConversationRepository repository,
            EventBus eventBus,
            MetricsCollector metricsCollector) {
        this.currentSession = currentSession;
        this.repository = repository;
        this.eventBus = eventBus;
        this.metricsCollector = metricsCollector;

        // 尝试加载之前的会话状态
        loadSessionState();

        // 记录 Session 创建事件
        String sessionId = currentSession.getSessionId();
        String userId = currentSession.getUserId();
        eventBus.publish(new SessionCreatedEvent(sessionId, userId));
        metricsCollector.incrementActiveSessions();
    }

    /**
     * 开始新的对话回合
     */
    public TurnContext startTurn(String userInput) {
        log.debug("开始新回合：userInput={}", truncate(userInput, 50));

        // 设置追踪上下文
        TraceContext.setSessionId(currentSession.getSessionId());
        TraceContext.setUserId(currentSession.getUserId());

        // 记录用户输入
        StructuredLogger.logUserInput(
            currentSession.getSessionId(),
            currentSession.getUserId(),
            userInput);

        // 添加用户消息
        ChatMessage userMsg = ChatMessage.user(userInput);
        currentSession.addMessage(userMsg);

        return new TurnContext(
                userMsg.id(),
                currentSession.getMessageCount(),
                java.time.Instant.now()
        );
    }

    /**
     * 完成回合，保存助手响应
     */
    public void completeTurn(TurnContext ctx, String assistantResponse, List<ToolCall> toolCalls) {
        log.debug("完成回合：turnId={}, responseLength={}, toolCalls={}",
                ctx.turnId(), assistantResponse != null ? assistantResponse.length() : 0,
                toolCalls != null ? toolCalls.size() : 0);

        // 添加助手消息
        ChatMessage assistantMsg = ChatMessage.assistant(assistantResponse, toolCalls);
        currentSession.addMessage(assistantMsg);

        // 保存到仓库
        repository.saveMessage(currentSession.getSessionId(), assistantMsg);
    }

    /**
     * 向当前会话追加一条消息（如子 Agent 批次完成时的系统提示）。
     * <p>
     * 仅当 {@code sessionId} 与当前会话 ID 一致时写入，避免多会话部署下误注入。
     */
    public void addMessage(String sessionId, MessageType type, String content) {
        if (sessionId == null || !sessionId.equals(currentSession.getSessionId())) {
            log.warn("addMessage 已跳过：会话不匹配，currentSessionId={}，requested={}",
                    currentSession.getSessionId(), sessionId);
            return;
        }
        if (type != MessageType.SYSTEM) {
            log.debug("addMessage: 非 SYSTEM 类型按 SYSTEM 落库，type={}", type);
        }
        ChatMessage msg = ChatMessage.system(content != null ? content : "");
        currentSession.addMessage(msg);
        repository.saveMessage(currentSession.getSessionId(), msg);
    }

    /**
     * 添加工具响应消息
     */
    public void addToolResponse(String toolResponseId, String content) {
        log.debug("添加工具响应：toolResponseId={}, contentLength={}",
                toolResponseId, content != null ? content.length() : 0);

        ChatMessage toolMsg = ChatMessage.tool(content, toolResponseId);
        currentSession.addMessage(toolMsg);
        repository.saveMessage(currentSession.getSessionId(), toolMsg);
    }

    /**
     * 记录文件修改
     */
    public void recordFileChange(String filePath, String content, String messageId, FileSnapshot.Operation operation) {
        log.debug("记录文件变更：filePath={}, operation={}", filePath, operation);

        switch (operation) {
            case CREATE -> currentSession.snapshotFile(filePath, content, messageId);
            case MODIFY -> currentSession.recordFileModification(filePath, content, messageId);
            case DELETE -> currentSession.recordFileDeletion(filePath, messageId);
        }

        // 异步保存到仓库
        repository.saveFileSnapshot(currentSession.getSessionId(), filePath,
                currentSession.getFileHistory(filePath));
    }

    /**
     * 添加归因记录
     */
    public void addAttribution(String messageId, String toolCallId, String toolName) {
        Attribution attribution = Attribution.create(messageId, toolCallId, toolName);
        currentSession.addAttribution(attribution);
    }

    /**
     * 获取历史消息
     */
    public List<ChatMessage> getHistory(int limit) {
        return currentSession.getRecentMessages(limit);
    }

    /**
     * 获取所有历史消息
     */
    public List<ChatMessage> getFullHistory() {
        return currentSession.getMessages();
    }

    /**
     * 获取文件历史
     */
    public List<FileSnapshot> getFileHistory(String filePath) {
        return currentSession.getFileHistory(filePath);
    }

    /**
     * 获取会话统计
     */
    public ConversationSession.SessionSnapshot getSessionSnapshot() {
        return currentSession.getSnapshot();
    }

    /**
     * 获取当前会话 ID
     */
    public String getSessionId() {
        return currentSession.getSessionId();
    }

    /**
     * 设置会话元数据
     */
    public void setMetadata(String key, Object value) {
        currentSession.setMetadata(key, value);
    }

    /**
     * 获取会话元数据
     */
    public <T> T getMetadata(String key) {
        return currentSession.getMetadata(key);
    }

    // ===== 内部方法 =====

    private void loadSessionState() {
        // 尝试从持久化存储加载之前的会话状态
        Optional<ConversationSession> loaded = repository.loadSession(currentSession.getSessionId());
        if (loaded.isPresent()) {
            // 这里可以合并加载的状态
            log.info("从持久化存储加载了会话状态");
        } else {
            log.info("新的会话，无需加载");
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 回合上下文
     */
    public record TurnContext(
            String turnId,
            int messageCount,
            Instant startedAt
    ) {}

    /**
     * 回合结果
     */
    public record TurnResult(
            String turnId,
            int finalMessageCount,
            Duration duration,
            long inputTokens,
            long outputTokens
    ) {
        public static TurnResult from(TurnContext ctx, ConversationSession session) {
            return new TurnResult(
                    ctx.turnId(),
                    session.getMessageCount(),
                    Duration.between(ctx.startedAt(), Instant.now()),
                    session.getTotalInputTokens(),
                    session.getTotalOutputTokens()
            );
        }
    }
}
