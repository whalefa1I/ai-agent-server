package demo.k8s.agent.toolsystem;

import java.time.Duration;
import java.util.Objects;

/**
 * 用户对权限请求的响应，用于 HTTP 请求体。
 *
 * @param requestId 请求 ID（来自 {@link PermissionRequest#id()}）
 * @param choice 用户选择
 * @param sessionDurationMinutes 会话时长（分钟），仅当 {@link PermissionChoice#ALLOW_SESSION} 时使用
 * @param comment 用户备注（可选）
 */
public record PermissionResponse(
        String requestId,
        PermissionChoice choice,
        Integer sessionDurationMinutes,
        String comment
) {
    public PermissionResponse {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(choice);
    }

    /**
     * 获取授权的过期时间（分钟）
     */
    public Duration getSessionDuration() {
        if (sessionDurationMinutes != null && sessionDurationMinutes > 0) {
            return Duration.ofMinutes(sessionDurationMinutes);
        }
        // 默认使用 choice 的定义
        Long defaultMs = choice.getSessionDurationMs();
        if (defaultMs != null) {
            return Duration.ofMillis(defaultMs);
        }
        return Duration.ofMinutes(30); // 默认 30 分钟
    }
}
