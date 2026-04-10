package demo.k8s.agent.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.query.AgenticTurnResult;
import demo.k8s.agent.query.EnhancedAgenticQueryLoop;
import demo.k8s.agent.query.LoopTerminalReason;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.state.ConversationSession;
import demo.k8s.agent.toolsystem.PermissionChoice;
import demo.k8s.agent.toolsystem.PermissionManager;
import demo.k8s.agent.toolsystem.PermissionRequest;
import demo.k8s.agent.toolsystem.PermissionResponse;
import demo.k8s.agent.toolsystem.PermissionResult;
import demo.k8s.agent.ws.protocol.WsProtocol.ChatMessage;
import demo.k8s.agent.ws.protocol.WsProtocol.SessionStats;
import demo.k8s.agent.ws.protocol.WsProtocol.*;
import static demo.k8s.agent.ws.protocol.WsProtocol.PROTOCOL_VERSION;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * WebSocket 处理器，为 TUI 客户端提供实时双向通信。
 * <p>
 * 每个用户连接会创建独立的会话，多用户之间互不干扰。
 * 支持 Token 认证（生产环境）。
 */
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 会话管理：sessionId -> WebSocketSession
    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<>();

    // 依赖服务
    private final EnhancedAgenticQueryLoop queryLoop;
    private final PermissionManager permissionManager;
    private final ConversationManager conversationManager;
    private final WebSocketTokenService tokenService;
    private final PermissionBroadcastService broadcastService;

    // 线程池（用于异步执行 query loop）
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("ws-agent-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    // 流式输出线程池（独立线程池用于文本增量推送）
    private final ExecutorService streamingExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r);
                t.setName("ws-stream-" + t.getId());
                t.setDaemon(true);
                return t;
            }
    );

    public AgentWebSocketHandler(
            EnhancedAgenticQueryLoop queryLoop,
            PermissionManager permissionManager,
            ConversationManager conversationManager,
            WebSocketTokenService tokenService,
            PermissionBroadcastService broadcastService) {
        this.queryLoop = queryLoop;
        this.permissionManager = permissionManager;
        this.conversationManager = conversationManager;
        this.tokenService = tokenService;
        this.broadcastService = broadcastService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();

        // 从 URL 路径中提取 Token
        String token = extractTokenFromSession(session);
        log.info("【WebSocket】客户端连接：sessionId={}, remote={}, uri={}, tokenPresent={}",
                sessionId, session.getRemoteAddress(), session.getUri(), token != null && !token.isEmpty());

        // Token 验证（如果启用）
        if (tokenService != null && tokenService.isAuthEnabled()) {
            if (token == null || token.isEmpty()) {
                log.warn("【WebSocket】连接被拒绝：未提供 Token");
                session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Missing token"));
                return;
            }

            if (!tokenService.validateToken(token)) {
                log.warn("【WebSocket】连接被拒绝：Token 无效或已过期");
                session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Invalid or expired token"));
                return;
            }

            log.info("【WebSocket】Token 验证通过：hash={}", tokenService.hashToken(token));
        }

        // 创建会话上下文
        SessionContext ctx = new SessionContext(session, token != null ? tokenService.hashToken(token) : "none");
        sessions.put(sessionId, ctx);

        // 订阅权限广播
        broadcastService.subscribe(sessionId, session);

        // 设置权限确认回调（使用广播服务）
        queryLoop.setPermissionCallback(request -> {
            CompletableFuture<PermissionResult> future = new CompletableFuture<>();
            ctx.pendingPermissionFuture = future;
            ctx.pendingPermissionRequest = request;

            // 通过广播服务推送权限请求到所有订阅的客户端
            broadcastService.broadcastPermissionRequest(sessionId, request)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.info("【WebSocket】权限请求超时或取消：requestId={}", request.id());
                        future.complete(PermissionResult.deny("Timeout or cancelled"));
                    } else {
                        // 处理响应
                        PermissionResult result = permissionManager.handlePermissionResponse(response);
                        future.complete(result);
                    }
                });

            return future;
        });

        // 发送连接确认（包含协议版本）
        ConnectedMessage connected = new ConnectedMessage(sessionId);
        connected.serverVersion = PROTOCOL_VERSION;
        sendMessage(session, connected);

        log.info("【WebSocket】客户端连接成功：sessionId={}, protocolVersion={}", sessionId, PROTOCOL_VERSION);
    }

    /**
     * 从 WebSocket 会话中提取 Token
     */
    private String extractTokenFromSession(WebSocketSession session) {
        try {
            String path = session.getUri().getPath();
            // 路径格式：/ws/agent/{token}
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                if ("agent".equals(segments[i]) && i + 1 < segments.length) {
                    String token = segments[i + 1];
                    if (token != null && !token.isEmpty()) {
                        return token;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取 Token 失败：{}", e.getMessage());
        }
        return null;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        log.info("客户端断开：sessionId={}, status={}", sessionId, status);

        SessionContext ctx = sessions.remove(sessionId);
        if (ctx != null) {
            ctx.closed = true;
            // 清理待确认的权限请求
            if (ctx.pendingPermissionRequest != null) {
                permissionManager.clearPendingRequest(ctx.pendingPermissionRequest.id());
            }
            // 取消订阅广播
            broadcastService.unsubscribe(sessionId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        SessionContext ctx = sessions.get(sessionId);

        if (ctx == null) {
            log.warn("【WebSocket】会话不存在：{}", sessionId);
            return;
        }

        try {
            // 解析客户端消息
            ClientMessage clientMsg = objectMapper.readValue(message.getPayload(), ClientMessage.class);
            log.info("【WebSocket】收到消息：sessionId={}, type={}", sessionId, clientMsg.getType());

            if (clientMsg instanceof UserMessage) {
                log.info("【WebSocket】用户消息：sessionId={}, content={}", sessionId, truncate(((UserMessage) clientMsg).content, 50));
                handleUserMessage(ctx, (UserMessage) clientMsg);
            } else if (clientMsg instanceof PermissionResponseMessage) {
                handlePermissionResponse(ctx, (PermissionResponseMessage) clientMsg);
            } else if (clientMsg instanceof PingMessage) {
                handlePing(ctx);
            } else if (clientMsg instanceof GetHistoryMessage) {
                handleGetHistory(ctx, (GetHistoryMessage) clientMsg);
            } else if (clientMsg instanceof GetStatsMessage) {
                handleGetStats(ctx, (GetStatsMessage) clientMsg);
            } else {
                log.warn("【WebSocket】未知消息类型：{}", clientMsg.getClass());
            }

        } catch (Exception e) {
            log.error("【WebSocket】处理消息失败", e);
            ErrorMessage error = new ErrorMessage("INTERNAL_ERROR", e.getMessage());
            sendMessage(session, error);
        }
    }

    /**
     * 处理用户输入消息
     */
    private void handleUserMessage(SessionContext ctx, UserMessage userMsg) {
        log.info("收到用户消息：sessionId={}, content={}", ctx.sessionId, truncate(userMsg.content, 50));

        // 异步执行 query loop
        executor.submit(() -> {
            try {
                executeQueryLoop(ctx, userMsg);
            } catch (Exception e) {
                log.error("执行 query loop 失败", e);
                ErrorMessage error = new ErrorMessage("QUERY_ERROR", e.getMessage());
                try {
                    sendMessage(ctx.session, error);
                } catch (Exception ex) {
                    log.error("发送错误消息失败", ex);
                }
            }
        });
    }

    /**
     * 执行 query loop（异步）
     */
    private void executeQueryLoop(SessionContext ctx, UserMessage userMsg) throws Exception {
        log.info("【WebSocket】开始执行 query loop：sessionId={}, requestId={}", ctx.sessionId, userMsg.requestId);

        // 开始回合
        ConversationManager.TurnContext turnCtx = conversationManager.startTurn(userMsg.content);

        // 发送响应开始
        ResponseStartMessage startMsg = new ResponseStartMessage(userMsg.requestId, turnCtx.turnId());
        sendMessage(ctx.session, startMsg);
        log.info("【WebSocket】已发送 RESPONSE_START：turnId={}", turnCtx.turnId());

        Instant startTime = Instant.now();
        int toolCallCount = 0;
        StringBuilder fullResponse = new StringBuilder();

        try {
            // 执行 enhanced query loop（带权限检查和回调）
            AgenticTurnResult result = queryLoop.runWithCallbacks(
                    userMsg.content,
                    // 工具调用回调（工具开始执行）
                    (toolCallId, toolName, input) -> {
                        try {
                            log.info("【WebSocket】工具开始：toolCallId={}, toolName={}", toolCallId, toolName);
                            ToolCallMessage toolMsg = ToolCallMessage.create(toolName, "started");
                            toolMsg.toolCallId = toolCallId;
                            toolMsg.input = input != null ? objectMapper.convertValue(input, Map.class) : null;
                            sendMessage(ctx.session, toolMsg);
                            log.info("【WebSocket】已发送 TOOL_CALL(started)：toolCallId={}, toolName={}", toolCallId, toolName);
                        } catch (Exception e) {
                            log.error("【WebSocket】发送工具调用通知失败", e);
                        }
                    },
                    // 文本增量回调（流式输出）
                    delta -> {
                        if (delta != null && !delta.isEmpty()) {
                            try {
                                TextDeltaMessage deltaMsg = new TextDeltaMessage(delta);
                                sendMessage(ctx.session, deltaMsg);
                            } catch (Exception e) {
                                log.error("【WebSocket】发送文本增量失败", e);
                            }
                        }
                    },
                    // reasoning/thinking 增量回调
                    null,
                    // 中间 assistant 文本回调
                    null,
                    // 状态变迁回调
                    null,
                    // 工具执行完成回调
                    (toolCallId, toolName, toolResult) -> {
                        try {
                            // 提取工具输出内容
                            String output = toolResult.getContent();
                            String error = toolResult.isSuccess() ? null : toolResult.getError();
                            Long durationMs = toolResult.getDurationMs();

                            log.info("【WebSocket】工具完成：toolCallId={}, toolName={}, success={}, output={}",
                                    toolCallId, toolName, toolResult.isSuccess(), truncate(output, 100));

                            ToolCallMessage toolMsg = ToolCallMessage.create(toolName, toolResult.isSuccess() ? "completed" : "failed");
                            toolMsg.toolCallId = toolCallId;
                            toolMsg.output = output;
                            toolMsg.error = error;
                            toolMsg.durationMs = durationMs;

                            // 设置输出类型（简单判断）
                            if (output != null && output.trim().startsWith("{")) {
                                toolMsg.outputType = "json";
                            } else {
                                toolMsg.outputType = "text";
                            }

                            sendMessage(ctx.session, toolMsg);
                            log.info("【WebSocket】已发送 TOOL_CALL({}): toolCallId={}, toolName={}",
                                    toolResult.isSuccess() ? "completed" : "failed", toolCallId, toolName);
                        } catch (Exception e) {
                            log.error("【WebSocket】发送工具完成通知失败", e);
                        }
                    }
            );

            if (result.reason() == LoopTerminalReason.COMPLETED) {
                fullResponse.append(result.replyText());

                // 如果已经通过回调发送了增量，这里只需要发送完成消息
                // 否则发送完整响应
                long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
                // 从 result 中提取 token 计数
                long inputTokens = result.tokenCounts() != null ? result.tokenCounts().inputTokens() : 0;
                long outputTokens = result.tokenCounts() != null ? result.tokenCounts().outputTokens() : 0;
                ResponseCompleteMessage complete = new ResponseCompleteMessage(
                        result.replyText(), inputTokens, outputTokens, durationMs, toolCallCount);
                sendMessage(ctx.session, complete);
            } else {
                ErrorMessage error = new ErrorMessage(
                        result.reason().name(),
                        "Query loop terminated: " + result.reason());
                sendMessage(ctx.session, error);
            }

        } catch (EnhancedAgenticQueryLoop.PermissionDeniedException e) {
            // 权限被拒绝
            ErrorMessage error = new ErrorMessage(
                    "PERMISSION_DENIED",
                    "工具调用被拒绝：" + e.getToolName());
            sendMessage(ctx.session, error);
        } catch (Exception e) {
            log.error("执行 query loop 失败", e);
            ErrorMessage error = new ErrorMessage("QUERY_ERROR", e.getMessage());
            sendMessage(ctx.session, error);
        }
    }

    /**
     * 从工具调用 ID 中提取工具名称
     * 注意：当前 toolCallId 直接使用 Spring AI 生成的 UUID，无法提取工具名称
     * 这里返回一个默认值，前端可以通过 toolCallId 关联状态更新
     */
    private String extractToolNameFromId(String toolCallId) {
        // 由于现在传递的是纯 UUID，无法提取工具名称
        // 返回一个通用的名称，前端主要通过 toolCallId 来关联
        return "tool";
    }

    /**
     * 处理权限响应
     */
    private void handlePermissionResponse(SessionContext ctx, PermissionResponseMessage msg) {
        log.info("收到权限响应：sessionId={}, requestId={}, choice={}",
                ctx.sessionId, msg.requestId, msg.choice);

        // 创建权限响应
        PermissionResponse response = new PermissionResponse(
                msg.requestId,
                PermissionChoice.valueOf(msg.choice),
                msg.sessionDurationMinutes != null ? msg.sessionDurationMinutes : 30,
                null
        );

        // 通过广播服务处理响应（会通知所有订阅的客户端）
        boolean handled = broadcastService.handlePermissionResponse(ctx.sessionId, response);

        if (!handled) {
            log.warn("权限响应未被处理，可能是超时或重复响应：requestId={}", msg.requestId);
        }
    }

    /**
     * 处理心跳
     */
    private void handlePing(SessionContext ctx) throws Exception {
        PongMessage pong = new PongMessage(Instant.now());
        sendMessage(ctx.session, pong);
    }

    /**
     * 处理获取历史消息请求
     */
    private void handleGetHistory(SessionContext ctx, GetHistoryMessage msg) throws Exception {
        log.info("收到获取历史消息请求：sessionId={}, limit={}", ctx.sessionId, msg.limit);

        int limit = msg.limit != null ? msg.limit : 20;

        // 从 ConversationManager 获取历史消息
        List<demo.k8s.agent.state.ChatMessage> history = conversationManager.getHistory(limit);

        // 转换为协议 DTO
        List<ChatMessage> chatMessages = history.stream()
                .map(ChatMessage::from)
                .toList();

        HistoryMessage response = new HistoryMessage(chatMessages);
        response.requestId = msg.requestId;
        sendMessage(ctx.session, response);
    }

    /**
     * 处理获取统计信息请求
     */
    private void handleGetStats(SessionContext ctx, GetStatsMessage msg) throws Exception {
        log.info("收到获取统计信息请求：sessionId={}", ctx.sessionId);

        // 从 ConversationManager 获取统计信息
        demo.k8s.agent.state.ConversationSession.SessionSnapshot snapshot =
                conversationManager.getSessionSnapshot();

        if (snapshot != null) {
            SessionStats stats = SessionStats.from(snapshot);
            StatsMessage response = new StatsMessage(stats);
            response.requestId = msg.requestId;
            sendMessage(ctx.session, response);
        } else {
            ErrorMessage error = new ErrorMessage("NOT_FOUND", "未找到会话统计信息");
            error.requestId = msg.requestId;
            sendMessage(ctx.session, error);
        }
    }

    /**
     * 发送消息到客户端
     */
    private void sendMessage(WebSocketSession session, ServerMessage msg) throws Exception {
        if (!session.isOpen()) {
            log.warn("会话已关闭，无法发送消息：{}", msg.getType());
            return;
        }

        String json = objectMapper.writeValueAsString(msg);
        session.sendMessage(new TextMessage(json));
        log.debug("发送消息：type={}", msg.getType());
    }

    /**
     * 会话上下文
     */
    static class SessionContext {
        final WebSocketSession session;
        final String sessionId;
        final String tokenHash;  // Token 哈希（用于日志）
        volatile boolean closed = false;

        // 待确认的权限请求
        volatile PermissionRequest pendingPermissionRequest;
        volatile CompletableFuture<PermissionResult> pendingPermissionFuture;

        SessionContext(WebSocketSession session, String tokenHash) {
            this.session = session;
            this.sessionId = session.getId();
            this.tokenHash = tokenHash;
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
