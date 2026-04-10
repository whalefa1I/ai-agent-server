package demo.k8s.agent.subagent;

import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.state.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 主 Agent 唤醒监听器：监听批次完成事件，将结果注入主会话。
 * <p>
 * 当批次所有子任务完成后，将汇总结果作为 {@link demo.k8s.agent.state.MessageType#SYSTEM} 消息追加到
 * {@link demo.k8s.agent.state.ConversationManager} 绑定的当前 {@link demo.k8s.agent.state.ConversationSession}。
 * 这与阻塞式、同轮次内的 {@code EnhancedAgenticQueryLoop} resume 不同：模型仅在后续轮次看到该上下文。
 */
@Component
public class MainAgentResumeListener {

    private static final Logger log = LoggerFactory.getLogger(MainAgentResumeListener.class);

    private final EventBus eventBus;
    private final ConversationManager conversationManager;

    public MainAgentResumeListener(EventBus eventBus, ConversationManager conversationManager) {
        this.eventBus = eventBus;
        this.conversationManager = conversationManager;

        // 订阅批次完成事件
        eventBus.subscribe(BatchCompletionListener.BatchCompletedEvent.class, this::onBatchCompleted);
        log.info("[MainAgentResumeListener] Registered for BatchCompletedEvent");
    }

    /**
     * 批次完成事件处理器。
     * <p>
     * 将批次结果作为系统消息注入主 Agent 的对话历史，唤醒主 Agent 继续执行。
     */
    private void onBatchCompleted(BatchCompletionListener.BatchCompletedEvent event) {
        String sessionId = event.sessionId();
        String mainRunId = event.getMainRunId();

        log.info("[MainAgentResumeListener] Batch completed, resuming main agent: sessionId={}, mainRunId={}",
                sessionId, mainRunId);

        // 将批次结果作为系统消息添加到对话历史
        // 这会触发主 Agent 在下一轮处理时看到这些结果
        String resumeMessage = buildResumeMessage(event);

        // 添加到对话历史（作为系统消息）
        conversationManager.addMessage(sessionId, MessageType.SYSTEM, resumeMessage);

        log.info("[MainAgentResumeListener] Main agent resumed with batch results: sessionId={}, batchId={}",
                sessionId, event.getBatchId());
    }

    /**
     * 构建唤醒消息。
     * <p>
     * 此消息将作为系统消息注入主 Agent 的上下文，指示其进行结果汇总。
     */
    private String buildResumeMessage(BatchCompletionListener.BatchCompletedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SUBAGENT BATCH COMPLETED ===\n\n");
        sb.append("All ").append(event.getTotalTasks()).append(" subagents have finished execution.\n\n");
        sb.append("BATCH SUMMARY:\n");
        sb.append(event.getResultsSummary());
        sb.append("\n\n=== ACTION REQUIRED ===\n");
        sb.append("Please review the subagent results above and provide a consolidated response to the user's original request.\n");
        sb.append("Synthesize the findings, identify patterns, and deliver a comprehensive answer.\n");
        sb.append("=== END OF BATCH RESULTS ===");
        return sb.toString();
    }
}
