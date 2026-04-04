package demo.k8s.agent.toolsystem;

import java.time.Instant;
import java.time.Duration;
import java.util.Objects;

/**
 * 权限授权记录，表示用户已授予的权限。
 *
 * @param id 授权记录 ID
 * @param toolName 工具名称
 * @param choice 用户选择的授权类型
 * @param level 授权时的风险等级
 * @param grantedAt 授权时间
 * @param expiresAt 过期时间（null = 永不过期）
 */
public record PermissionGrant(
        String id,
        String toolName,
        PermissionChoice choice,
        PermissionLevel level,
        Instant grantedAt,
        Instant expiresAt
) {
    public PermissionGrant {
        Objects.requireNonNull(id);
        Objects.requireNonNull(toolName);
        Objects.requireNonNull(choice);
        Objects.requireNonNull(level);
        Objects.requireNonNull(grantedAt);
    }

    public static PermissionGrant create(
            String toolName,
            PermissionChoice choice,
            PermissionLevel level) {

        Instant now = Instant.now();
        Instant expiresAt = computeExpiry(choice, now);

        return new PermissionGrant(
            generateId(),
            toolName,
            choice,
            level,
            now,
            expiresAt
        );
    }

    private static Instant computeExpiry(PermissionChoice choice, Instant now) {
        return switch (choice) {
            case ALLOW_ONCE -> now.plus(Duration.ofMinutes(5)); // 5 分钟内有效
            case ALLOW_SESSION -> now.plus(Duration.ofMinutes(30)); // 30 分钟
            case ALLOW_ALWAYS -> null; // 永不过期
            case DENY -> null;
        };
    }

    private static String generateId() {
        return "grant_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 检查授权是否已过期
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // null = 永不过期
        }
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * 检查授权是否仍然有效（未过期且为允许类型）
     */
    public boolean isValid() {
        return choice.isAllowed() && !isExpired();
    }

    /**
     * 检查是否适用于指定工具
     */
    public boolean matchesTool(String toolName) {
        return this.toolName.equals(toolName);
    }

    /**
     * 检查是否适用于指定风险等级（允许同级或更低风险）
     */
    public boolean coversLevel(PermissionLevel other) {
        // 只有完全匹配或更低风险才覆盖
        return level.compareTo(other) <= 0;
    }
}
