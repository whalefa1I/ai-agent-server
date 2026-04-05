package demo.k8s.agent.toolsystem;

/**
 * 权限规则类型枚举
 */
public enum PermissionRuleType {
    /** 精确匹配 */
    EXACT,
    /** 前缀匹配 */
    PREFIX,
    /** 通配符匹配 */
    WILDCARD
}
