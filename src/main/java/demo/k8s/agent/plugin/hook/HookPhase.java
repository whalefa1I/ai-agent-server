package demo.k8s.agent.plugin.hook;

/**
 * Hook 执行阶段
 */
public enum HookPhase {
    /**
     * 在目标操作之前执行
     * <p>
     * BEFORE Hook 可以：
     * - 修改输入参数
     * - 记录日志/指标
     * - 阻止操作执行（通过返回 false）
     * - 执行前置验证
     */
    BEFORE,

    /**
     * 在目标操作之后执行
     * <p>
     * AFTER Hook 可以：
     * - 修改输出结果
     * - 记录日志/指标
     * - 触发后续操作
     * - 执行清理工作
     */
    AFTER,

    /**
     * 环绕目标操作执行
     * <p>
     * AROUND Hook 可以：
     * - 完全控制是否执行目标操作
     * - 修改输入和输出
     * - 添加异常处理
     * - 测量执行时间
     */
    AROUND
}
