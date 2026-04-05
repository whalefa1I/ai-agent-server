package demo.k8s.agent.toolsystem;

/**
 * 权限规则记录类
 *
 * @param type 规则类型
 * @param rule 原始规则字符串
 * @param value 用于匹配的值（精确匹配时为命令，通配符匹配时为模式）
 * @param prefix 前缀（仅前缀匹配时使用）
 */
public record PermissionRule(
        PermissionRuleType type,
        String rule,
        String value,
        String prefix
) {
    /**
     * 简化构造函数（不带 prefix）
     */
    public PermissionRule(PermissionRuleType type, String rule, String value) {
        this(type, rule, value, null);
    }

    /**
     * 检查命令是否匹配此规则
     */
    public boolean matches(String command) {
        return ShellRuleMatcher.matches(command, this);
    }
}
