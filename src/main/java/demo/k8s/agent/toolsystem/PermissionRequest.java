package demo.k8s.agent.toolsystem;

import java.time.Instant;
import java.util.Objects;

/**
 * 发送给用户的权限确认请求，对应 Claude Code PermissionDialog 的输入数据。
 *
 * @param id 请求唯一 ID
 * @param toolName 工具名称
 * @param toolDescription 工具描述
 * @param level 风险等级
 * @param inputSummary 参数摘要（非完整 JSON，用于展示）
 * @param riskExplanation 风险说明文本
 * @param createdAt 创建时间
 */
public record PermissionRequest(
        String id,
        String toolName,
        String toolDescription,
        PermissionLevel level,
        String inputSummary,
        String riskExplanation,
        Instant createdAt
) {
    public PermissionRequest {
        Objects.requireNonNull(id);
        Objects.requireNonNull(toolName);
        Objects.requireNonNull(toolDescription);
        Objects.requireNonNull(level);
        Objects.requireNonNull(inputSummary);
        Objects.requireNonNull(riskExplanation);
        Objects.requireNonNull(createdAt);
    }

    public static PermissionRequest create(
            String toolName,
            String toolDescription,
            PermissionLevel level,
            String inputSummary,
            String riskExplanation) {
        return new PermissionRequest(
            generateId(),
            toolName,
            toolDescription,
            level,
            inputSummary,
            riskExplanation,
            Instant.now()
        );
    }

    private static String generateId() {
        return "perm_" + System.currentTimeMillis() + "_" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取展示的图标
     */
    public String getLevelIcon() {
        return level.getIcon();
    }

    /**
     * 获取等级的展示文本
     */
    public String getLevelLabel() {
        return level.getLabel();
    }
}
