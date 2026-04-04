package demo.k8s.agent.toolstate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * ToolStateWebSocketHandler - 工具状态 WebSocket 处理器
 *
 * 处理客户端连接，将连接注册到 ToolEventRouter
 */
@Component
public class ToolStateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ToolStateWebSocketHandler.class);

    @Autowired
    private ToolEventRouter eventRouter;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从 session 属性中获取 userId（由握手拦截器设置）
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            eventRouter.addConnection(userId, session);
            log.info("工具状态 WebSocket 连接建立：sessionId={}, userId={}", session.getId(), userId);
        } else {
            log.warn("工具状态 WebSocket 连接建立但未找到 userId: sessionId={}", session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 目前不支持客户端发送消息，仅用于接收服务端推送
        log.debug("收到工具状态 WebSocket 消息（忽略）：sessionId={}, payload={}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            eventRouter.removeConnection(userId, session);
            log.info("工具状态 WebSocket 连接关闭：sessionId={}, userId={}", session.getId(), userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        log.error("工具状态 WebSocket 传输错误：sessionId={}, userId={}, error={}",
            session.getId(), userId, exception.getMessage());
    }
}
