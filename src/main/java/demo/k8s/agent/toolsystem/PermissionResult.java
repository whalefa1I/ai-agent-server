package demo.k8s.agent.toolsystem;

import java.util.Objects;

/**
 * 对应 Claude Code {@code checkPermissions} 的返回：允许、拒绝或需要用户确认。
 * <p>
 * 三种结果：
 * <ul>
 *   <li>{@link Allow} - 允许执行，可附带更新后的输入 JSON</li>
 *   <li>{@link Deny} - 拒绝执行，附带拒绝原因</li>
 *   <li>{@link NeedsConfirmation} - 需要用户确认，附带权限请求详情</li>
 * </ul>
 */
public sealed interface PermissionResult permits PermissionResult.Allow, PermissionResult.Deny, PermissionResult.NeedsConfirmation {

    /**
     * 允许执行
     */
    record Allow(String updatedInputJson) implements PermissionResult {
        public Allow {
            Objects.requireNonNull(updatedInputJson, "updatedInputJson");
        }

        public static Allow of() {
            return new Allow("{}");
        }
    }

    /**
     * 拒绝执行
     */
    record Deny(String reason, PermissionLevel level) implements PermissionResult {
        public Deny {
            Objects.requireNonNull(reason, "reason");
        }

        public Deny(String reason) {
            this(reason, PermissionLevel.DESTRUCTIVE);
        }
    }

    /**
     * 需要用户确认
     */
    record NeedsConfirmation(PermissionRequest request) implements PermissionResult {
        public NeedsConfirmation {
            Objects.requireNonNull(request, "request");
        }
    }

    // ===== 静态工厂方法 =====

    static Allow allow(String updatedInputJson) {
        return new Allow(updatedInputJson);
    }

    static Allow allow() {
        return Allow.of();
    }

    static Deny deny(String reason) {
        return new Deny(reason);
    }

    static Deny deny(String reason, PermissionLevel level) {
        return new Deny(reason, level);
    }

    static NeedsConfirmation needsConfirmation(PermissionRequest request) {
        return new NeedsConfirmation(request);
    }

    // ===== 辅助方法 =====

    /**
     * 是否允许执行
     */
    default boolean isAllowed() {
        return this instanceof Allow;
    }

    /**
     * 是否被拒绝
     */
    default boolean isDenied() {
        return this instanceof Deny;
    }

    /**
     * 是否需要用户确认
     */
    default boolean needsConfirmation() {
        return this instanceof NeedsConfirmation;
    }

    /**
     * 获取更新后的输入 JSON（仅 Allow 类型）
     */
    default String getUpdatedInput() {
        return this instanceof Allow a ? a.updatedInputJson() : null;
    }

    /**
     * 获取拒绝原因（仅 Deny 类型）
     */
    default String getDenyReason() {
        return this instanceof Deny d ? d.reason() : null;
    }

    /**
     * 获取权限请求（仅 NeedsConfirmation 类型）
     */
    default PermissionRequest getPermissionRequest() {
        return this instanceof NeedsConfirmation n ? n.request() : null;
    }
}
