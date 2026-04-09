package demo.k8s.agent.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 顶层到客户端：SSE 推送主模型流式 chunk；在出现工具调用时推送 orchestration 事件。
 * 子 Agent（Task 内由 {@link org.springaicommunity.agent.tools.task.claude.ClaudeSubagentExecutor} 执行）
 * 在库中当前为同步 {@code call().content()}，子层 token 不会逐字外流；Task 执行期间流会停顿直到子调用结束。
 */
@RestController
@RequestMapping("/api")
public class StreamingChatController {

    private static final long SSE_TIMEOUT_MS = 600_000L;

    private final ChatClient demoChatClient;

    public StreamingChatController(ChatClient demoChatClient) {
        this.demoChatClient = demoChatClient;
    }

    /**
     * 流式对话：每行 SSE data 为 JSON，字段含 {@code layer}（main / orchestration）、{@code type} 等。
     */
    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String userMessage = request.message() == null ? "" : request.message();

        Flux<ChatResponse> flux = demoChatClient.prompt()
                .user(userMessage)
                .stream()
                .chatResponse();

        flux.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        response -> {
                            try {
                                Generation gen = response.getResult();
                                if (gen != null && gen.getOutput() != null) {
                                    String text = gen.getOutput().getText();
                                    System.out.println("[StreamingChatController] Flux callback: text length = " + (text != null ? text.length() : 0) + ", text = " + (text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text));
                                }
                                emitMainDeltas(emitter, response);
                                emitOrchestration(emitter, response);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete);

        return emitter;
    }

    private static void emitMainDeltas(SseEmitter emitter, ChatResponse response) throws IOException {
        Generation gen = response.getResult();
        if (gen == null || gen.getOutput() == null) {
            return;
        }
        AssistantMessage out = gen.getOutput();
        String text = out.getText();
        if (!StringUtils.hasText(text)) {
            return;
        }
        // 记录日志用于调试：查看每次收到的文本长度
        System.out.println("[StreamingChatController] emitMainDeltas: text length = " + text.length());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("layer", "main");
        payload.put("type", "delta");
        payload.put("text", text);
        emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
    }

    private static void emitOrchestration(SseEmitter emitter, ChatResponse response) throws IOException {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return;
        }
        var toolCalls = response.getResult().getOutput().getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        for (var tc : toolCalls) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("layer", "orchestration");
            payload.put("type", "tool_call");
            payload.put("id", tc.id());
            payload.put("name", tc.name());
            payload.put("note", "Task 表示子 Agent 委派；执行完成前主通道可能暂停。");
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        }
    }
}
