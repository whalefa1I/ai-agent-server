package demo.k8s.agent.toolstate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

/**
 * ToolStateHandshakeInterceptor - WebSocket 握手拦截器
 *
 * 从请求参数中提取 userId 并放入 session 属性
 */
@Component
public class ToolStateHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * 握手前处理 - 提取 userId
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String userId = servletRequest.getServletRequest().getParameter("userId");
            if (userId != null && !userId.isEmpty()) {
                attributes.put("userId", userId);
                return true;
            } else {
                // 拒绝没有 userId 的连接
                response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        return false;
    }

    /**
     * 握手后处理 - 无操作
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // 无操作
    }
}
