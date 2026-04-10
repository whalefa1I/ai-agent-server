package demo.k8s.agent.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 子 Agent 产品 API 的会话归属校验（与 {@link SubagentV2Controller} / {@link SubagentSseController} 共用）。
 * <p>
 * 生产环境应在 {@link #validateSessionOwnership} 中接入真实身份与会话绑定；当前默认仅校验非空。
 */
@Component
public class SubagentSessionAuthHelper {

    public SubagentSessionAuthHelper() {
    }

    /**
     * @return null 表示通过；否则为需直接返回的 HTTP 响应
     */
    public ResponseEntity<?> validateSessionOwnership(HttpServletRequest request, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "sessionId is required"
            ));
        }
        // TODO: 从 SecurityContext / Token 解析用户并校验 session 归属
        return null;
    }

    public String getCurrentUserPrincipal(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        return userId != null ? userId : "anonymous";
    }
}
