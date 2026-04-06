package demo.k8s.agent.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 用户上下文工具类
 */
public class UserContext {

    /**
     * 从 JWT 中提取用户 ID
     */
    public static String getUserIdFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        // 优先使用 user_id claim，如果没有则使用 sub
        String userId = jwt.getClaimAsString("user_id");
        if (userId == null || userId.isEmpty()) {
            userId = jwt.getSubject();
        }
        return userId;
    }

    /**
     * 从 JWT 中提取 Session ID
     */
    public static String getSessionIdFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return jwt.getClaimAsString("session_id");
    }

    /**
     * 从 JWT 中提取用户名
     */
    public static String getUsernameFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return jwt.getClaimAsString("name");
    }
}
