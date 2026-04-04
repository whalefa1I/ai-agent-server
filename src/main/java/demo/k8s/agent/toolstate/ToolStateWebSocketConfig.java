package demo.k8s.agent.toolstate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocketConfig - 工具状态 WebSocket 配置
 *
 * 注册 WebSocket 处理器和拦截器
 */
@Configuration
@EnableWebSocket
public class ToolStateWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ToolStateWebSocketHandler toolStateHandler;

    @Autowired
    private ToolStateHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(toolStateHandler, "/ws/tool-state")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");  // 允许跨域（生产环境应限制）
    }
}
