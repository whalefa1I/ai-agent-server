package demo.k8s.agent.plugin.hook;

/**
 * Hook 类型 - 定义可以拦截的事件点
 */
public enum HookType {
    /**
     * 工具调用前/后
     * <p>
     * 事件数据：ToolCallEvent
     * <p>
     * 用途：日志记录、权限增强、输入/输出转换、指标收集
     */
    TOOL_CALL,

    /**
     * 模型调用前/后
     * <p>
     * 事件数据：ModelCallEvent
     * <p>
     * 用途：提示词注入、响应过滤、Token 计数、缓存
     */
    MODEL_CALL,

    /**
     * 消息接收时
     * <p>
     * 事件数据：MessageReceivedEvent
     * <p>
     * 用途：消息预处理、命令解析、身份验证
     */
    MESSAGE_RECEIVED,

    /**
     * 消息发送时
     * <p>
     * 事件数据：MessageSentEvent
     * <p>
     * 用途：消息后处理、格式化、渠道适配
     */
    MESSAGE_SENT,

    /**
     * 会话开始时
     * <p>
     * 事件数据：SessionStartedEvent
     * <p>
     * 用途：会话初始化、上下文加载、用户偏好应用
     */
    SESSION_STARTED,

    /**
     * 会话结束时
     * <p>
     * 事件数据：SessionEndedEvent
     * <p>
     * 用途：会话持久化、清理工作、总结生成
     */
    SESSION_ENDED,

    /**
     * Agent 回合开始/结束
     * <p>
     * 事件数据：AgentTurnEvent
     * <p>
     * 用途：回合计数、状态管理、进度追踪
     */
    AGENT_TURN,

    /**
     * 用户命令执行前/后（Slash 命令）
     * <p>
     * 事件数据：CommandExecutionEvent
     * <p>
     * 用途：命令别名、权限检查、命令历史
     */
    COMMAND_EXECUTION
}
