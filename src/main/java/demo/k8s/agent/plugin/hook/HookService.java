package demo.k8s.agent.plugin.hook;

import demo.k8s.agent.config.HookProperties;
import demo.k8s.agent.plugin.hook.Hook.HookContext;
import demo.k8s.agent.plugin.hook.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Hook 服务 - 统一的 Hook 执行入口
 * <p>
 * 提供便捷方法用于在各种事件点触发 Hook
 */
@Service
public class HookService {

    private static final Logger log = LoggerFactory.getLogger(HookService.class);

    private final HookRegistry hookRegistry;
    private final HookExecutor hookExecutor;
    private final HookProperties hookProperties;

    public HookService(HookRegistry hookRegistry, HookExecutor hookExecutor, HookProperties hookProperties) {
        this.hookRegistry = hookRegistry;
        this.hookExecutor = hookExecutor;
        this.hookProperties = hookProperties;
        log.info("HookService initialized (enabled={}, async={})",
                hookProperties.isEnabled(), hookProperties.isAsync());
    }

    /**
     * 检查 Hook 系统是否启用
     */
    public boolean isEnabled() {
        return hookProperties.isEnabled();
    }

    // ==================== TOOL_CALL Hooks ====================

    /**
     * 触发工具调用 BEFORE Hook
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param toolName  工具名称
     * @param input     输入参数
     * @return true=继续执行，false=阻止执行
     */
    public boolean beforeToolCall(String sessionId, String userId, String toolName, Object input) {
        if (!isEnabled()) {
            return true;
        }

        HookContext context = hookExecutor.createContext(
                HookType.TOOL_CALL,
                sessionId,
                userId,
                Map.of("toolName", toolName, "input", input)
        );

        return hookExecutor.executeBeforeHooks(HookType.TOOL_CALL, context);
    }

    /**
     * 触发工具调用 AFTER Hook
     *
     * @param sessionId  会话 ID
     * @param userId     用户 ID
     * @param toolName   工具名称
     * @param input      输入参数
     * @param output     输出结果
     * @param successful 是否成功
     * @param durationMs 执行时长
     */
    public void afterToolCall(String sessionId, String userId, String toolName, Object input,
                              String output, boolean successful, long durationMs) {
        if (!isEnabled()) {
            return;
        }

        ToolCallEvent event = new ToolCallEvent(toolName, null, output, successful, durationMs);
        HookContext context = hookExecutor.createContext(
                HookType.TOOL_CALL,
                sessionId,
                userId,
                Map.of("toolName", toolName, "input", input, "eventData", event)
        );
        context.setResult(output);

        hookExecutor.executeAfterHooks(HookType.TOOL_CALL, context);
    }

    // ==================== MODEL_CALL Hooks ====================

    /**
     * 触发模型调用 BEFORE Hook
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param modelName 模型名称
     * @param prompt    提示词
     * @return 可能被 Hook 修改后的提示词
     */
    public String beforeModelCall(String sessionId, String userId, String modelName, String prompt) {
        if (!isEnabled()) {
            return prompt;
        }

        HookContext context = hookExecutor.createContext(
                HookType.MODEL_CALL,
                sessionId,
                userId,
                Map.of("modelName", modelName, "prompt", prompt)
        );

        hookExecutor.executeBeforeHooks(HookType.MODEL_CALL, context);

        // Hook 可能修改了 prompt
        Object modifiedPrompt = context.getResult();
        return modifiedPrompt != null ? modifiedPrompt.toString() : prompt;
    }

    /**
     * 触发模型调用 AFTER Hook
     *
     * @param sessionId   会话 ID
     * @param userId      用户 ID
     * @param modelName   模型名称
     * @param prompt      提示词
     * @param response    响应内容
     * @param inputTokens 输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param durationMs  执行时长
     * @return 可能被 Hook 修改后的响应
     */
    public String afterModelCall(String sessionId, String userId, String modelName, String prompt,
                                 String response, int inputTokens, int outputTokens, long durationMs) {
        if (!isEnabled()) {
            return response;
        }

        ModelCallEvent event = new ModelCallEvent(modelName, prompt, response, inputTokens, outputTokens, durationMs);
        HookContext context = hookExecutor.createContext(
                HookType.MODEL_CALL,
                sessionId,
                userId,
                Map.of("modelName", modelName, "prompt", prompt, "eventData", event)
        );
        context.setResult(response);

        hookExecutor.executeAfterHooks(HookType.MODEL_CALL, context);

        Object modifiedResponse = context.getResult();
        return modifiedResponse != null ? modifiedResponse.toString() : response;
    }

    // ==================== MESSAGE_RECEIVED Hooks ====================

    /**
     * 触发消息接收 Hook
     *
     * @param channelId  频道 ID
     * @param messageId  消息 ID
     * @param content    消息内容
     * @param sessionId  会话 ID
     * @param userId     用户 ID
     * @return 可能被 Hook 修改后的消息内容
     */
    public String onMessageReceived(String channelId, String messageId, String content,
                                    String sessionId, String userId) {
        if (!isEnabled()) {
            return content;
        }

        MessageReceivedEvent event = new MessageReceivedEvent(channelId, messageId, content);
        HookContext context = hookExecutor.createContext(
                HookType.MESSAGE_RECEIVED,
                sessionId,
                userId,
                Map.of("channelId", channelId, "messageId", messageId, "eventData", event)
        );
        context.setResult(content);

        hookExecutor.executeBeforeHooks(HookType.MESSAGE_RECEIVED, context);

        Object modifiedContent = context.getResult();
        return modifiedContent != null ? modifiedContent.toString() : content;
    }

    // ==================== SESSION Hooks ====================

    /**
     * 触发会话开始 Hook
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     */
    public void onSessionStarted(String sessionId, String userId) {
        if (!isEnabled()) {
            return;
        }

        SessionStartedEvent event = new SessionStartedEvent(sessionId, userId);
        HookContext context = hookExecutor.createContext(
                HookType.SESSION_STARTED,
                sessionId,
                userId,
                Map.of("eventData", event)
        );

        hookExecutor.executeAfterHooks(HookType.SESSION_STARTED, context);
    }

    // ==================== AGENT_TURN Hooks ====================

    /**
     * 触发 Agent 回合 Hook
     *
     * @param sessionId   会话 ID
     * @param turnNumber  回合号
     * @param userMessage 用户消息
     * @param userId      用户 ID
     */
    public void onAgentTurn(String sessionId, int turnNumber, String userMessage, String userId) {
        if (!isEnabled()) {
            return;
        }

        AgentTurnEvent event = new AgentTurnEvent(sessionId, turnNumber, userMessage);
        HookContext context = hookExecutor.createContext(
                HookType.AGENT_TURN,
                sessionId,
                userId,
                Map.of("eventData", event)
        );

        hookExecutor.executeAfterHooks(HookType.AGENT_TURN, context);
    }

    /**
     * 获取 Hook 统计信息
     */
    public Map<HookType, Integer> getHookStats() {
        return hookRegistry.getHookStats();
    }

    /**
     * 注册一个 Hook
     */
    public void registerHook(Hook hook) {
        hookRegistry.register(hook);
    }

    /**
     * 注销一个 Hook
     */
    public void unregisterHook(String hookId) {
        hookRegistry.unregister(hookId);
    }
}
