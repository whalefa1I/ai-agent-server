package demo.k8s.agent.sandbox;

/**
 * AgentScope 沙盒会话信息。
 *
 * @param sessionId   会话 ID
 * @param userId      用户 ID
 * @param sandboxType 沙盒类型
 * @param status      会话状态
 * @param baseUrl     远程服务地址
 * @param createdAt   创建时间戳
 */
public record SandboxSessionInfo(
        String sessionId,
        String userId,
        String sandboxType,
        String status,
        String baseUrl,
        long createdAt
) {
    /**
     * 创建会话信息。
     */
    public SandboxSessionInfo {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
    }

    /**
     * 创建会话信息实例。
     *
     * @param sessionId   会话 ID
     * @param userId      用户 ID
     * @param sandboxType 沙盒类型
     * @param baseUrl     远程服务地址
     * @return 会话信息
     */
    public static SandboxSessionInfo of(String sessionId, String userId, String sandboxType, String baseUrl) {
        return new SandboxSessionInfo(sessionId, userId, sandboxType, "running", baseUrl, System.currentTimeMillis());
    }
}
