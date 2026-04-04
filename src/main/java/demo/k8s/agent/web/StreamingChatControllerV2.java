package demo.k8s.agent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.query.EnhancedAgenticQueryLoop;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.toolsystem.PermissionManager;
import demo.k8s.agent.ws.AgentWebSocketHandler;
import demo.k8s.agent.ws.WebSocketTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式聊天控制器，支持逐 token 流式输出
 */
@RestController
@RequestMapping("/api/chat")
public class StreamingChatControllerV2 {

    private static final Logger log = LoggerFactory.getLogger(StreamingChatControllerV2.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StreamingChatControllerV2(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 流式聊天接口（SSE）
     * 返回格式：
     * data: {"type": "start"}
     * data: {"type": "token", "content": "你"}
     * data: {"type": "token", "content": "好"}
     * data: {"type": "end", "inputTokens": 10, "outputTokens": 20}
     */
    @PostMapping(value = "/stream-v2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isEmpty()) {
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(objectMapper.writeValueAsString(
                        Map.of("type", "error", "message", "Missing message")));
            } catch (IOException e) {
                log.error("发送错误消息失败", e);
            }
            errorEmitter.complete();
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(60_000L); // 60 秒超时

        // 异步执行流式请求
        CompletableFuture.runAsync(() -> {
            try {
                // 发送开始事件
                emitter.send(objectMapper.writeValueAsString(Map.of("type", "start")));

                // 创建 Prompt
                Prompt prompt = new Prompt(message);

                // 流式调用
                Flux<ChatResponse> flux = chatModel.stream(prompt);

                flux.doOnComplete(() -> {
                    try {
                        emitter.send(objectMapper.writeValueAsString(
                                Map.of("type", "end")));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("发送完成事件失败", e);
                    }
                }).doOnError(throwable -> {
                    try {
                        emitter.send(objectMapper.writeValueAsString(
                                Map.of("type", "error", "message", throwable.getMessage())));
                        emitter.completeWithError(throwable);
                    } catch (IOException e) {
                        log.error("发送错误事件失败", e);
                    }
                }).subscribe(chatResponse -> {
                    try {
                        String content = chatResponse.getResult().getOutput().getText();
                        if (content != null && !content.isEmpty()) {
                            emitter.send(objectMapper.writeValueAsString(
                                    Map.of("type", "token", "content", content)));
                        }
                    } catch (IOException e) {
                        log.error("发送 token 失败", e);
                        emitter.completeWithError(e);
                    }
                });

            } catch (Exception e) {
                log.error("流式聊天失败", e);
                try {
                    emitter.send(objectMapper.writeValueAsString(
                            Map.of("type", "error", "message", e.getMessage())));
                } catch (IOException ex) {
                    log.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
