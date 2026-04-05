package demo.k8s.agent.toolsystem;

/**
 * 权限模式，与 Claude Code 的 {@code PermissionMode} 对齐。
 */
public enum ToolPermissionMode {

    /** 默认模式：敏感工具需要用户确认 */
    DEFAULT,

    /** 只读模式：仅允许标记为只读的工具，禁止写操作 */
    READ_ONLY,

    /** 跳过所有权限确认，开发调试使用 */
    BYPASS,

    /** 自动接受编辑类工具（file_edit, file_write 等） */
    ACCEPT_EDITS,

    /** 永久记住用户选择，不再询问 */
    DONT_ASK,

    /** 仅计划模式：不执行工具，仅做计划 */
    PLAN,

    /** AI 自动决策模式：由分类器决定 */
    AUTO
}
