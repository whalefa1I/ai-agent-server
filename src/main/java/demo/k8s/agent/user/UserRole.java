package demo.k8s.agent.user;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户角色枚举
 */
public enum UserRole {
    /**
     * 普通用户 - 基础配额和功能
     */
    USER("user", "普通用户"),

    /**
     * 高级用户 - 更高配额和优先支持
     */
    PREMIUM("premium", "高级用户"),

    /**
     * 管理员 - 全部权限
     */
    ADMIN("admin", "管理员"),

    /**
     * 服务账户 - 用于 API 调用
     */
    SERVICE("service", "服务账户");

    private final String code;
    private final String displayName;

    UserRole(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据 code 获取角色
     */
    public static UserRole fromCode(String code) {
        for (UserRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return USER;
    }
}
