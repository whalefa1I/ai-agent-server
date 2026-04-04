package demo.k8s.agent.toolsystem;

/**
 * 简化版权限模式；完整版可参考 Claude Code 的 {@code PermissionMode}。
 */
public enum ToolPermissionMode {

    /** 常规：走注册表 + 每工具 checkPermissions */
    DEFAULT,

    /** 仅允许标记为只读的工具（结合 {@link ClaudeLikeTool#isReadOnly(com.fasterxml.jackson.databind.JsonNode)}） */
    READ_ONLY,

    /** 开发调试用：跳过部分检查（仍建议在生产关闭） */
    BYPASS
}
