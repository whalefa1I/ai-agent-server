package demo.k8s.agent.user;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户实体
 */
public record User(
        /**
         * 用户唯一 ID
         */
        String id,

        /**
         * 用户名（唯一）
         */
        String username,

        /**
         * 邮箱
         */
        String email,

        /**
         * 密码哈希（BCrypt）
         */
        String passwordHash,

        /**
         * 用户角色
         */
        UserRole role,

        /**
         * 创建时间
         */
        Instant createdAt,

        /**
         * 最后登录时间
         */
        Instant lastLoginAt,

        /**
         * 元数据（存储扩展信息）
         */
        Map<String, Object> metadata,

        /**
         * API Key（用于认证）
         */
        String apiKey,

        /**
         * 账户是否激活
         */
        boolean active
) {
    public User {
        Objects.requireNonNull(id);
        Objects.requireNonNull(username);
        Objects.requireNonNull(email);
        Objects.requireNonNull(role);
        Objects.requireNonNull(createdAt);
    }

    /**
     * 创建新用户
     */
    public static User create(String username, String email, String passwordHash, UserRole role) {
        return new User(
                generateId(),
                username,
                email,
                passwordHash,
                role,
                Instant.now(),
                null,
                Map.of(),
                null,
                true
        );
    }

    /**
     * 更新最后登录时间
     */
    public User withLastLogin(Instant lastLoginAt) {
        return new User(
                this.id,
                this.username,
                this.email,
                this.passwordHash,
                this.role,
                this.createdAt,
                lastLoginAt,
                this.metadata,
                this.apiKey,
                this.active
        );
    }

    /**
     * 更新角色
     */
    public User withRole(UserRole role) {
        return new User(
                this.id,
                this.username,
                this.email,
                this.passwordHash,
                role,
                this.createdAt,
                this.lastLoginAt,
                this.metadata,
                this.apiKey,
                this.active
        );
    }

    /**
     * 更新 API Key
     */
    public User withApiKey(String apiKey) {
        return new User(
                this.id,
                this.username,
                this.email,
                this.passwordHash,
                this.role,
                this.createdAt,
                this.lastLoginAt,
                this.metadata,
                apiKey,
                this.active
        );
    }

    /**
     * 更新元数据
     */
    public User withMetadata(Map<String, Object> metadata) {
        return new User(
                this.id,
                this.username,
                this.email,
                this.passwordHash,
                this.role,
                this.createdAt,
                this.lastLoginAt,
                metadata,
                this.apiKey,
                this.active
        );
    }

    /**
     * 设置激活状态
     */
    public User withActive(boolean active) {
        return new User(
                this.id,
                this.username,
                this.email,
                this.passwordHash,
                this.role,
                this.createdAt,
                this.lastLoginAt,
                this.metadata,
                this.apiKey,
                active
        );
    }

    private static String generateId() {
        return "user_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }
}
