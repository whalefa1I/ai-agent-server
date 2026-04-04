package demo.k8s.agent.toolsystem;

/**
 * 工具风险等级分类，与 Claude Code 的 permission level 对齐。
 * <p>
 * 等级从低到高：
 * <ul>
 *   <li>{@link #READ_ONLY} - 读操作：读取文件、搜索、查看配置</li>
 *   <li>{@link #MODIFY_STATE} - 写操作：修改文件、创建/删除</li>
 *   <li>{@link #NETWORK} - 网络操作：HTTP 请求、外部 API 调用</li>
 *   <li>{@link #DESTRUCTIVE} - 破坏性操作：删除文件、覆盖、执行系统命令</li>
 *   <li>{@link #AGENT_SPAWN} - 代理操作：启动子 Agent</li>
 * </ul>
 */
public enum PermissionLevel {

    /**
     * 读操作：不修改任何外部状态
     * <p>
     * 示例工具：Read, Grep, Glob, Bash (只读命令)
     */
    READ_ONLY("只读", "\uD83D\uDCD6"),

    /**
     * 写操作：修改文件系统状态
     * <p>
     * 示例工具：Write, Edit, Bash (文件修改命令)
     */
    MODIFY_STATE("修改状态", "\u270F\uFE0F"),

    /**
     * 网络操作：发起外部网络请求
     * <p>
     * 示例工具：Fetch, BraveSearch
     */
    NETWORK("网络访问", "\uD83C\uDF10"),

    /**
     * 破坏性操作：不可逆的修改或删除
     * <p>
     * 示例工具：Bash (危险命令), DeleteFile
     */
    DESTRUCTIVE("破坏性", "\u26A0\uFE0F"),

    /**
     * 代理操作：启动或委派给子 Agent
     * <p>
     * 示例工具：Task, run_in_background
     */
    AGENT_SPAWN("子代理", "\uD83E\uDD16");

    private final String label;
    private final String icon;

    PermissionLevel(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * 是否需要用户确认（只读操作通常不需要）
     */
    public boolean requiresConfirmation() {
        return this != READ_ONLY;
    }
}
