package demo.k8s.agent.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.query.AgenticTurnResult;
import demo.k8s.agent.query.EnhancedAgenticQueryLoop;
import demo.k8s.agent.query.LoopTerminalReason;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.toolsystem.PermissionChoice;
import demo.k8s.agent.toolsystem.PermissionManager;
import demo.k8s.agent.toolsystem.PermissionRequest;
import demo.k8s.agent.toolsystem.PermissionResponse;
import demo.k8s.agent.toolsystem.PermissionResult;
import demo.k8s.agent.ws.protocol.WsProtocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * HTTP REST API 控制器 - 兼容非 WebSocket 前端（如 TypeScript/JavaScript 客户端）。
 * <p>
 * 提供以下功能：
 * <ul>
 *   <li>{@code POST /api/v2/chat} - 发送消息并获取响应（支持轮询和 SSE）</li>
 *   <li>{@code GET /api/v2/permissions} - 获取待确认的权限请求</li>
 *   <li>{@code POST /api/v2/permissions/respond} - 提交权限响应</li>
 *   <li>{@code GET /api/v2/permissions/stream} - SSE 推送权限请求</li>
 *   <li>{@code GET /api/v2/messages} - 获取历史消息</li>
 *   <li>{@code GET /api/v2/stats} - 获取会话统计</li>
 * </ul>
 *
 * <h3>TypeScript 集成示例</h3>
 * <pre>{@code
 * // 发送消息（轮询模式）
 * const response = await fetch('/api/v2/chat', {
 *   method: 'POST',
 *   headers: { 'Content-Type': 'application/json' },
 *   body: JSON.stringify({ message: 'hello' })
 * });
 * const data = await response.json();
 * console.log(data.content);  // 助手回复
 *
 * // 获取权限请求
 * const permissions = await fetch('/api/v2/permissions');
 * const pending = await permissions.json();
 *
 * // 提交权限响应
 * await fetch('/api/v2/permissions/respond', {
 *   method: 'POST',
 *   headers: { 'Content-Type': 'application/json' },
 *   body: JSON.stringify({
 *     requestId: 'perm_xxx',
 *     choice: 'ALLOW_ONCE'
 *   })
 * });
 * }</pre>
 */
@RestController
@RequestMapping("/api/v2")
@CrossOrigin(origins = "*")  // 允许前端跨域访问
public class HttpApiV2Controller {

    private static final Logger log = LoggerFactory.getLogger(HttpApiV2Controller.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EnhancedAgenticQueryLoop queryLoop;
    private final PermissionManager permissionManager;
    private final ConversationManager conversationManager;
    private final PermissionBroadcastService broadcastService;
    private final ChatClient chatClient;

    // 会话级 SSE 发射器管理
    private final ConcurrentHashMap<String, List<SseEmitter>> permissionEmitters = new ConcurrentHashMap<>();

    public HttpApiV2Controller(
            EnhancedAgenticQueryLoop queryLoop,
            PermissionManager permissionManager,
            ConversationManager conversationManager,
            PermissionBroadcastService broadcastService,
            ChatClient chatClient) {
        this.queryLoop = queryLoop;
        this.permissionManager = permissionManager;
        this.conversationManager = conversationManager;
        this.broadcastService = broadcastService;
        this.chatClient = chatClient;
    }

    // ==================== 聊天 API ====================

    /**
     * 发送消息并获取响应（阻塞模式，适用于简单客户端）
     */
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("收到聊天请求：sessionId={}, message={}",
                request.sessionId, truncate(request.message, 50));

        try {
            // 执行 query loop
            AgenticTurnResult result = queryLoop.runWithCallbacks(
                    request.message,
                    (toolName, input) -> {
                        // 工具调用回调 - 记录但不推送（阻塞模式）
                        log.info("工具调用：{} {}", toolName, input);
                    },
                    delta -> {
                        // 文本增量回调 - 累积到 StringBuilder
                    }
            );

            if (result.reason() == LoopTerminalReason.COMPLETED) {
                return ResponseEntity.ok(new ChatResponse(
                        result.replyText(),
                        0,  // inputTokens 需要从会话统计中获取
                        0,  // outputTokens
                        0   // toolCallCount
                ));
            } else {
                return ResponseEntity.internalServerError()
                        .body(new ChatResponse("Error: " + result.reason(), 0, 0, 0));
            }

        } catch (Exception e) {
            log.error("处理聊天请求失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("Error: " + e.getMessage(), 0, 0, 0));
        }
    }

    /**
     * 发送消息并获取流式响应（SSE 模式）
     * <p>
     * 前端使用 EventSource 接收增量内容：
     * <pre>{@code
     * const eventSource = new EventSource('/api/v2/chat/stream?message=hello');
     * eventSource.onmessage = (event) => {
     *   const data = JSON.parse(event.data);
     *   if (data.type === 'TEXT_DELTA') {
     *     appendToChat(data.delta);
     *   } else if (data.type === 'RESPONSE_COMPLETE') {
     *     eventSource.close();
     *   }
     * };
     * }</pre>
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String message) {
        log.info("收到流式聊天请求：message={}", truncate(message, 50));

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 分钟超时

        // 发送开始事件
        try {
            emitter.send(SseEmitter.event()
                    .name("response_start")
                    .data(Map.of("type", "RESPONSE_START", "turnId", "stream_" + System.currentTimeMillis())));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // 使用 ChatClient.stream() 实现真正的流式输出
        reactor.core.publisher.Flux<ChatResponse> flux = chatClient.prompt()
                .user(message)
                .stream()
                .chatResponse();

        StringBuilder fullContent = new StringBuilder();
        long startTime = System.currentTimeMillis();
        int[] toolCallCount = {0};

        flux.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        response -> {
                            // 每次回调收到一个流式 chunk
                            try {
                                Generation gen = response.getResult();
                                if (gen != null && gen.getOutput() != null) {
                                    String text = gen.getOutput().getText();
                                    if (text != null && !text.isEmpty()) {
                                        // 发送 TEXT_DELTA 事件
                                        emitter.send(SseEmitter.event()
                                                .name("text_delta")
                                                .data(Map.of("type", "TEXT_DELTA", "delta", text)));
                                        fullContent.append(text);
                                    }

                                    // 检查是否有工具调用
                                    var toolCalls = gen.getOutput().getToolCalls();
                                    if (toolCalls != null && !toolCalls.isEmpty()) {
                                        for (var tc : toolCalls) {
                                            emitter.send(SseEmitter.event()
                                                    .name("tool_call")
                                                    .data(Map.of(
                                                            "type", "TOOL_CALL",
                                                            "toolName", tc.name(),
                                                            "input", tc.arguments(),
                                                            "status", "started"
                                                    )));
                                            toolCallCount[0]++;
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                log.debug("发送流式数据失败", e);
                            }
                        },
                        error -> {
                            log.error("流式聊天出错", error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(Map.of("type", "ERROR", "message", error.getMessage())));
                                emitter.completeWithError(error);
                            } catch (IOException ex) {
                                log.debug("发送错误事件失败", ex);
                            }
                        },
                        () -> {
                            // 流式完成
                            try {
                                long durationMs = System.currentTimeMillis() - startTime;
                                emitter.send(SseEmitter.event()
                                        .name("response_complete")
                                        .data(Map.of(
                                                "type", "RESPONSE_COMPLETE",
                                                "content", fullContent.toString(),
                                                "inputTokens", 0,
                                                "outputTokens", 0,
                                                "durationMs", durationMs,
                                                "toolCalls", toolCallCount[0]
                                        )));
                                emitter.complete();
                            } catch (IOException e) {
                                log.debug("发送完成事件失败", e);
                            }
                        }
                );

        return emitter;
    }

    // ==================== 权限 API ====================

    /**
     * 获取待确认的权限请求列表
     */
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionRequest>> getPendingPermissions() {
        List<PermissionRequest> pending = permissionManager.getPendingRequests();
        return ResponseEntity.ok(pending);
    }

    /**
     * 提交权限响应
     */
    @PostMapping(value = "/permissions/respond", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PermissionResponseResult> respondPermission(
            @RequestBody PermissionRespondRequest request) {
        log.info("收到权限响应：requestId={}, choice={}", request.requestId, request.choice);

        try {
            // 创建权限响应对象
            PermissionResponse response = new PermissionResponse(
                    request.requestId,
                    PermissionChoice.valueOf(request.choice),
                    request.sessionDurationMinutes != null ? request.sessionDurationMinutes : 30,
                    null
            );

            // 处理响应
            PermissionResult result = permissionManager.handlePermissionResponse(response);

            return ResponseEntity.ok(new PermissionResponseResult(
                    true,
                    result.isAllowed() ? "ALLOWED" : "DENIED",
                    result.isAllowed() ? "Permission granted" : result.getDenyReason()
            ));

        } catch (Exception e) {
            log.error("处理权限响应失败", e);
            return ResponseEntity.badRequest()
                    .body(new PermissionResponseResult(false, "ERROR", e.getMessage()));
        }
    }

    /**
     * SSE 推送权限请求（实时通知）
     * <p>
     * 前端使用 EventSource 监听：
     * <pre>{@code
     * const eventSource = new EventSource('/api/v2/permissions/stream');
     * eventSource.onmessage = (event) => {
     *   const permission = JSON.parse(event.data);
     *   showPermissionDialog(permission);
     * };
     * }</pre>
     */
    @GetMapping(value = "/permissions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPermissions() {
        log.info("新的权限 SSE 订阅");

        SseEmitter emitter = new SseEmitter(0L); // 永不过期

        // 存储 emitter 用于后续广播
        String emitterId = java.util.UUID.randomUUID().toString();

        // 发送心跳保持连接（使用 ScheduledExecutorService）
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("heartbeat").data("{}"));
                    } catch (IOException e) {
                        // 连接已断开，完成 emitter
                        emitter.complete();
                    }
                },
                0, 30, java.util.concurrent.TimeUnit.SECONDS
        );

        emitter.onCompletion(() -> {
            log.info("权限 SSE 连接关闭：emitterId={}", emitterId);
            scheduler.shutdown();
        });

        emitter.onTimeout(() -> {
            log.info("权限 SSE 连接超时：emitterId={}", emitterId);
            scheduler.shutdown();
        });

        return emitter;
    }

    // ==================== 消息历史 API ====================

    /**
     * 获取历史消息
     */
    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(
            @RequestParam(defaultValue = "20") Integer limit) {
        List<demo.k8s.agent.state.ChatMessage> history = conversationManager.getHistory(limit);
        List<ChatMessage> messages = history.stream()
                .map(ChatMessage::from)
                .toList();
        return ResponseEntity.ok(messages);
    }

    // ==================== 统计 API ====================

    /**
     * 获取会话统计
     */
    @GetMapping("/stats")
    public ResponseEntity<SessionStats> getStats() {
        demo.k8s.agent.state.ConversationSession.SessionSnapshot snapshot =
                conversationManager.getSessionSnapshot();

        if (snapshot != null) {
            return ResponseEntity.ok(SessionStats.from(snapshot));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 健康检查 ====================

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "version", "2.0.0"
        ));
    }

    // ==================== 数据传输对象 ====================

    /**
     * 聊天请求
     */
    public static class ChatRequest {
        public String sessionId;
        public String message;
    }

    /**
     * 聊天响应
     */
    public static class ChatResponse {
        public String content;
        public long inputTokens;
        public long outputTokens;
        public int toolCalls;

        public ChatResponse() {}

        public ChatResponse(String content, long inputTokens, long outputTokens, int toolCalls) {
            this.content = content;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.toolCalls = toolCalls;
        }
    }

    /**
     * 权限响应请求
     */
    public static class PermissionRespondRequest {
        public String requestId;
        public String choice;  // ALLOW_ONCE, ALLOW_SESSION, ALLOW_ALWAYS, DENY
        public Integer sessionDurationMinutes;
    }

    /**
     * 权限响应结果
     */
    public static class PermissionResponseResult {
        public boolean success;
        public String status;  // ALLOWED, DENIED, ERROR
        public String message;

        public PermissionResponseResult() {}

        public PermissionResponseResult(boolean success, String status, String message) {
            this.success = success;
            this.status = status;
            this.message = message;
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
