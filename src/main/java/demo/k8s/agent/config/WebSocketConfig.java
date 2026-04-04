package demo.k8s.agent.config;

import demo.k8s.agent.query.EnhancedAgenticQueryLoop;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.toolsystem.PermissionManager;
import demo.k8s.agent.ws.AgentWebSocketHandler;
import demo.k8s.agent.ws.PermissionBroadcastService;
import demo.k8s.agent.ws.WebSocketTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置，支持多前端客户端连接（TUI + Web + 其他）。
 * <p>
 * 端点：
 * <ul>
 *   <li>{@code /ws/agent/{token}} - 主 Agent 连接端点</li>
 * </ul>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EnhancedAgenticQueryLoop queryLoop;
    private final PermissionManager permissionManager;
    private final ConversationManager conversationManager;
    private final WebSocketTokenService tokenService;
    private final PermissionBroadcastService broadcastService;

    public WebSocketConfig(
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
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // WebSocket 端点：/ws/agent/{token}
        // 支持 Token 认证（生产环境）
        registry.addHandler(
                agentWebSocketHandler(),
                "/ws/agent/{token}"
        ).setAllowedOrigins("*");
    }

    @Bean
    public WebSocketHandler agentWebSocketHandler() {
        return new AgentWebSocketHandler(queryLoop, permissionManager, conversationManager, tokenService, broadcastService);
    }
}
