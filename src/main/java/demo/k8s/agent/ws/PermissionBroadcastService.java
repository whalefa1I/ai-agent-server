package demo.k8s.agent.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.toolsystem.PermissionRequest;
import demo.k8s.agent.toolsystem.PermissionResponse;
import demo.k8s.agent.ws.protocol.WsProtocol.PermissionRequestMessage;
import demo.k8s.agent.ws.protocol.WsProtocol.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 权限请求广播服务 - 支持多前端同时接收权限确认请求。
 * <p>
 * 使用场景：
 * <ul>
 *   <li>用户同时打开网页端和 TUI 客户端，两个前端都能收到权限请求</li>
 *   <li>任意一个前端的用户确认后，其他前端自动关闭对话框</li>
 *   <li>支持多个前端订阅同一个会话或用户</li>
 * </ul>
 *
 * <h3>前端集成示例 (TypeScript)</h3>
 * <pre>{@code
 * // 连接 WebSocket
 * const ws = new WebSocket('ws://localhost:8080/ws/agent');
 *
 * // 监听权限请求
 * ws.onmessage = (event) => {
 *   const msg = JSON.parse(event.data);
 *   if (msg.type === 'PERMISSION_REQUEST') {
 *     // 显示确认对话框
 *     const dialog = showPermissionDialog(msg);
 *
 *     // 用户点击"允许"按钮
 *     dialog.onAllow = (choice) => {
 *       ws.send(JSON.stringify({
 *         type: 'PERMISSION_RESPONSE',
 *         requestId: msg.id,
 *         choice: choice  // ALLOW_ONCE, ALLOW_SESSION, ALLOW_ALWAYS, DENY
 *       }));
 *     };
 *   }
 * };
 * }</pre>
 */
@Service
public class PermissionBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(PermissionBroadcastService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 订阅者管理：sessionId -> WebSocket 会话列表
     */
    private final ConcurrentHashMap<String, List<WebSocketSession>> sessionSubscribers = new ConcurrentHashMap<>();

    /**
     * 待确认的权限请求 -> 等待响应的 Future
     */
    private final ConcurrentHashMap<String, CompletableFuture<PermissionResponse>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 前端回调注册表：用于将权限响应路由到正确的等待者
     */
    private final Map<String, Consumer<PermissionResponse>> responseCallbacks = new ConcurrentHashMap<>();

    /**
     * 订阅会话的权限广播
     *
     * @param sessionId 会话 ID
     * @param session WebSocket 会话
     */
    public void subscribe(String sessionId, WebSocketSession session) {
        sessionSubscribers
            .computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
            .add(session);
        log.info("订阅权限广播：sessionId={}, 当前订阅数={}", sessionId, sessionSubscribers.get(sessionId).size());
    }

    /**
     * 取消订阅
     *
     * @param sessionId 会话 ID
     * @param session WebSocket 会话
     */
    public void unsubscribe(String sessionId, WebSocketSession session) {
        List<WebSocketSession> sessions = sessionSubscribers.get(sessionId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionSubscribers.remove(sessionId);
            }
            log.info("取消订阅：sessionId={}, 剩余订阅数={}", sessionId, sessions != null ? sessions.size() : 0);
        }
    }

    /**
     * 广播权限请求到所有订阅的客户端
     *
     * @param sessionId 会话 ID
     * @param request 权限请求
     * @return Future，当任意客户端响应时完成
     */
    public CompletableFuture<PermissionResponse> broadcastPermissionRequest(
            String sessionId,
            PermissionRequest request) {

        CompletableFuture<PermissionResponse> future = new CompletableFuture();
        pendingRequests.put(request.id(), future);

        // 创建前端友好的消息
        PermissionRequestMessage msg = new PermissionRequestMessage(request);

        // 广播到所有订阅的客户端
        List<WebSocketSession> sessions = sessionSubscribers.get(sessionId);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("没有订阅者，权限请求将无法接收响应：sessionId={}", sessionId);
        } else {
            int sentCount = 0;
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, msg);
                        sentCount++;
                    } catch (IOException e) {
                        log.error("发送权限请求失败：sessionId={}", sessionId, e);
                    }
                }
            }
            log.info("已广播权限请求：requestId={}, 发送到{}个客户端", request.id(), sentCount);
        }

        // 设置超时（5 分钟无响应自动拒绝）
        future.orTimeout(5 * 60 * 1000, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 当响应到达时，通知所有客户端关闭对话框
        future.whenComplete((response, error) -> {
            pendingRequests.remove(request.id());
            if (error != null) {
                log.info("权限请求超时或取消：requestId={}", request.id());
            } else {
                // 广播取消消息到其他客户端
                broadcastPermissionCancelled(sessionId, request.id());
            }
        });

        return future;
    }

    /**
     * 处理客户端返回的权限响应
     *
     * @param sessionId 会话 ID
     * @param response 权限响应
     * @return 是否成功处理
     */
    public boolean handlePermissionResponse(String sessionId, PermissionResponse response) {
        log.info("收到权限响应：sessionId={}, requestId={}, choice={}",
                sessionId, response.requestId(), response.choice());

        CompletableFuture<PermissionResponse> future = pendingRequests.remove(response.requestId());
        if (future == null) {
            log.warn("未找到待处理的权限请求：{}", response.requestId());
            return false;
        }

        // 完成 Future，等待者将收到响应
        future.complete(response);
        return true;
    }

    /**
     * 广播权限请求取消消息（当某个客户端已响应时）
     *
     * @param sessionId 会话 ID
     * @param requestId 权限请求 ID
     */
    public void broadcastPermissionCancelled(String sessionId, String requestId) {
        Map<String, Object> cancelMsg = Map.of(
            "type", "PERMISSION_CANCELLED",
            "requestId", requestId,
            "timestamp", java.time.Instant.now().toString()
        );

        List<WebSocketSession> sessions = sessionSubscribers.get(sessionId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, cancelMsg);
                    } catch (IOException e) {
                        log.debug("发送取消消息失败", e);
                    }
                }
            }
        }
    }

    /**
     * 获取待确认的权限请求列表
     */
    public List<String> getPendingRequestIds() {
        return List.copyOf(pendingRequests.keySet());
    }

    /**
     * 获取会话的订阅者数量
     */
    public int getSubscriberCount(String sessionId) {
        List<WebSocketSession> sessions = sessionSubscribers.get(sessionId);
        return sessions != null ? sessions.size() : 0;
    }

    private void sendMessage(WebSocketSession session, Object msg) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        String json = objectMapper.writeValueAsString(msg);
        session.sendMessage(new TextMessage(json));
    }
}
