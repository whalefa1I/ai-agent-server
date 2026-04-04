package demo.k8s.agent.toolstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ToolEventRouter - 工具状态事件路由器
 *
 * 参考 happy-server 的 eventRouter 设计：
 * - 管理 WebSocket 连接（按用户分组）
 * - 广播工具状态更新给订阅的客户端
 * - 支持跳过发送者（避免 echo）
 */
@Component
public class ToolEventRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolEventRouter.class);

    // 按用户 ID 分组的 WebSocket 连接
    private final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 连接管理 ====================

    /**
     * 添加用户 WebSocket 连接
     */
    public void addConnection(String userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("添加工具状态 WebSocket 连接：userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * 移除用户 WebSocket 连接
     */
    public void removeConnection(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
            log.debug("移除工具状态 WebSocket 连接：userId={}, sessionId={}", userId, session.getId());
        }
    }

    /**
     * 获取用户的所有 WebSocket 连接
     */
    public Set<WebSocketSession> getConnections(String userId) {
        return userSessions.getOrDefault(userId, Collections.emptySet());
    }

    // ==================== 事件发送 ====================

    /**
     * 发送工具状态更新给所有订阅的用户连接
     *
     * @param userId 用户 ID
     * @param artifact 工具状态
     * @param skipSession 跳过的会话（避免发送给发送者）
     */
    public void emitToolStateUpdate(String userId, ToolArtifact artifact, WebSocketSession skipSession) {
        ToolStateUpdateEvent event = new ToolStateUpdateEvent(artifact);
        emitEvent(userId, event, skipSession);
    }

    /**
     * 发送工具状态创建事件
     */
    public void emitToolStateCreated(String userId, ToolArtifact artifact, WebSocketSession skipSession) {
        Map<String, Object> payload = Map.of(
            "type", "new-tool-artifact",
            "artifactId", artifact.getId(),
            "header", parseHeaderJson(artifact.getHeader()),
            "body", parseBodyJson(artifact.getBody()),
            "version", artifact.getBodyVersion(),
            "timestamp", System.currentTimeMillis()
        );
        emitPayload(userId, payload, skipSession);
    }

    /**
     * 发送工具状态删除事件
     */
    public void emitToolStateDeleted(String userId, String artifactId, WebSocketSession skipSession) {
        Map<String, Object> payload = Map.of(
            "type", "delete-tool-artifact",
            "artifactId", artifactId,
            "timestamp", System.currentTimeMillis()
        );
        emitPayload(userId, payload, skipSession);
    }

    private void emitEvent(String userId, ToolStateUpdateEvent event, WebSocketSession skipSession) {
        emitPayload(userId, Map.of(
            "type", event.getType(),
            "artifactId", event.getArtifactId(),
            "header", event.getHeader(),
            "body", event.getBody(),
            "timestamp", event.getTimestamp()
        ), skipSession);
    }

    private void emitPayload(String userId, Map<String, Object> payload, WebSocketSession skipSession) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("没有用户 {} 的 WebSocket 连接，跳过事件广播", userId);
            return;
        }

        for (WebSocketSession session : sessions) {
            if (session == skipSession) {
                log.debug("跳过发送者会话：{}", session.getId());
                continue;
            }

            if (!session.isOpen()) {
                log.debug("会话已关闭，跳过：{}", session.getId());
                continue;
            }

            try {
                String json = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(json));
                log.debug("发送工具状态更新到会话 {}: {}", session.getId(), payload.get("type"));
            } catch (IOException e) {
                log.warn("发送工具状态更新失败：sessionId={}, error={}", session.getId(), e.getMessage());
                // 连接已断开，清理
                removeConnection(userId, session);
            }
        }
    }

    // ==================== 工具方法 ====================

    private Object parseHeaderJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Object parseBodyJson(String json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
