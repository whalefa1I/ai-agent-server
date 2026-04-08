package demo.k8s.agent.web;

import demo.k8s.agent.query.EnhancedAgenticQueryLoop;
import demo.k8s.agent.query.AgenticTurnResult;
import demo.k8s.agent.state.*;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event.ErrorEvent;
import demo.k8s.agent.observability.logging.StructuredLogger;
import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 有状态对话控制器，集成 ConversationManager 和 EnhancedAgenticQueryLoop。
 */
@RestController
@RequestMapping(path = "/api/chat", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "*")
public class StatefulChatController {

    private static final Logger log = LoggerFactory.getLogger(StatefulChatController.class);

    private final ConversationManager conversationManager;
    private final EnhancedAgenticQueryLoop agenticQueryLoop;
    private final EventBus eventBus;

    public StatefulChatController(
            ConversationManager conversationManager,
            EnhancedAgenticQueryLoop agenticQueryLoop,
            EventBus eventBus) {
        this.conversationManager = conversationManager;
        this.agenticQueryLoop = agenticQueryLoop;
        this.eventBus = eventBus;
    }

    /**
     * 有状态对话接口
     */
    @PostMapping
    public ChatResponse chat(@RequestBody Map<String, Object> request) {
        String message = request != null && request.get("message") != null
                ? String.valueOf(request.get("message"))
                : "";
        if (message.isBlank()) {
            return new ChatResponse(
                    "错误：message 不能为空",
                    conversationManager.getSessionId(),
                    conversationManager.getSessionSnapshot()
            );
        }
        log.info("收到对话请求：sessionId={}", conversationManager.getSessionId());

        // 开始新回合
        ConversationManager.TurnContext ctx = conversationManager.startTurn(message);

        try {
            // 执行 agentic loop
            AgenticTurnResult result = agenticQueryLoop.run(message);

            // 保存助手响应
            conversationManager.completeTurn(ctx, result.replyText(), List.of());

            // 返回响应
            return new ChatResponse(
                    result.replyText(),
                    conversationManager.getSessionId(),
                    conversationManager.getSessionSnapshot()
            );

        } catch (Exception e) {
            log.error("对话处理失败", e);

            // 记录错误日志和事件
            String sessionId = conversationManager.getSessionId();
            String userId = conversationManager.getMetadata("userId");
            StructuredLogger.logError(sessionId, userId, "CHAT_ERROR",
                e.getMessage(), e.getStackTrace() != null ? e.getStackTrace()[0].toString() : "");
            eventBus.publish(new ErrorEvent(sessionId, userId, "CHAT_ERROR",
                e.getMessage(), e.getStackTrace() != null ? e.getStackTrace()[0].toString() : ""));

            return new ChatResponse(
                    "错误：" + e.getMessage(),
                    conversationManager.getSessionId(),
                    conversationManager.getSessionSnapshot()
            );
        }
    }

    /**
     * 获取历史消息
     */
    @GetMapping("/history")
    public List<ChatMessage> getHistory(
            @RequestParam(defaultValue = "50") int limit) {
        return conversationManager.getHistory(limit);
    }

    /**
     * 获取完整历史消息
     */
    @GetMapping("/history/full")
    public List<ChatMessage> getFullHistory() {
        return conversationManager.getFullHistory();
    }

    /**
     * 获取文件历史
     */
    @GetMapping("/files/{path:.+}/history")
    public List<FileSnapshot> getFileHistory(@PathVariable String path) {
        return conversationManager.getFileHistory(path);
    }

    /**
     * 获取会话统计
     */
    @GetMapping("/stats")
    public ConversationSession.SessionSnapshot getSessionStats() {
        return conversationManager.getSessionSnapshot();
    }

    /**
     * 获取当前会话 ID
     */
    @GetMapping("/session-id")
    public Map<String, String> getSessionId() {
        return Map.of("sessionId", conversationManager.getSessionId());
    }

    /**
     * 对话响应
     */
    public record ChatResponse(
            String content,
            String sessionId,
            ConversationSession.SessionSnapshot stats
    ) {}
}
