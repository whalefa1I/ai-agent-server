package demo.k8s.agent.query;

import java.util.ArrayList;
import java.util.List;

import demo.k8s.agent.config.AgentPrompts;
import demo.k8s.agent.config.DemoCoordinatorProperties;
import demo.k8s.agent.config.DemoDebugProperties;
import demo.k8s.agent.config.DemoQueryProperties;
import demo.k8s.agent.observability.logging.ModelRequestDebugLogger;
import demo.k8s.agent.skills.SkillService;
import demo.k8s.agent.toolsystem.McpToolProvider;
import demo.k8s.agent.toolsystem.ToolFeatureFlags;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import demo.k8s.agent.toolsystem.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * 显式实现与 {@code query.ts} / {@code QueryEngine.ts} 对齐的<strong>内层 agentic 循环</strong>：
 * compaction → {@link ChatModel#call} → 若有 tool_use 则 {@link ToolCallingManager#executeToolCalls} → 拼回消息再进入下一轮。
 * <p>
 * 与直接使用 {@link org.springframework.ai.chat.client.ChatClient} 单次 {@code call()} 不同：此处可观测轮次、压缩与重试策略。
 */
@Service
public class AgenticQueryLoop {

    private static final Logger log = LoggerFactory.getLogger(AgenticQueryLoop.class);

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final CompactionPipeline compactionPipeline;
    private final ModelCallRetryPolicy retryPolicy;
    private final DemoQueryProperties queryProperties;
    private final ToolRegistry toolRegistry;
    private final ToolPermissionContext toolPermissionContext;
    private final ToolFeatureFlags toolFeatureFlags;
    private final McpToolProvider mcpToolProvider;
    private final DemoCoordinatorProperties coordinatorProperties;
    private final SkillService skillService;
    private final DemoDebugProperties demoDebugProperties;

    public AgenticQueryLoop(
            ChatModel chatModel,
            ToolCallingManager toolCallingManager,
            @Qualifier("enhancedCompactionPipeline") CompactionPipeline compactionPipeline,
            ModelCallRetryPolicy retryPolicy,
            DemoQueryProperties queryProperties,
            ToolRegistry toolRegistry,
            ToolPermissionContext toolPermissionContext,
            ToolFeatureFlags toolFeatureFlags,
            McpToolProvider mcpToolProvider,
            DemoCoordinatorProperties coordinatorProperties,
            SkillService skillService,
            DemoDebugProperties demoDebugProperties) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.compactionPipeline = compactionPipeline;
        this.retryPolicy = retryPolicy;
        this.queryProperties = queryProperties;
        this.toolRegistry = toolRegistry;
        this.toolPermissionContext = toolPermissionContext;
        this.toolFeatureFlags = toolFeatureFlags;
        this.mcpToolProvider = mcpToolProvider;
        this.coordinatorProperties = coordinatorProperties;
        this.skillService = skillService;
        this.demoDebugProperties = demoDebugProperties;
    }

    public AgenticTurnResult run(String userMessage) {
        List<ToolCallback> tools =
                toolRegistry.filteredCallbacks(
                        toolPermissionContext, toolFeatureFlags, mcpToolProvider.loadMcpTools());

        ToolCallingChatOptions options =
                ToolCallingChatOptions.builder().toolCallbacks(tools).build();

        // 构建系统提示词，动态注入技能提示词（每次请求时检查版本变化）
        String baseSystem =
                coordinatorProperties.isEnabled()
                        ? AgentPrompts.COORDINATOR_ORCHESTRATOR_ONLY
                        : AgentPrompts.DEMO_COORDINATOR_SYSTEM;

        // 动态获取技能提示词（版本变化时自动重载）
        String skillsPrompt = skillService.buildSkillsPrompt();
        String system = baseSystem + (skillsPrompt.isEmpty() ? "" : "\n\n" + skillsPrompt);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.add(new UserMessage(userMessage));

        QueryLoopState state = QueryLoopState.initial();

        while (true) {
            if (state.turnCount() >= queryProperties.getMaxTurns()) {
                return new AgenticTurnResult(LoopTerminalReason.MAX_TURNS, "", state);
            }
            ContinuationReason cont =
                    state.turnCount() == 0 ? ContinuationReason.INITIAL : ContinuationReason.TOOL_FOLLOW_UP;
            state = state.nextTurn(cont);

            messages = compactionPipeline.compactBeforeModelCall(messages);

            Prompt prompt = new Prompt(messages, options);
            final int turnForDebugLog = state.turnCount();
            final List<Message> messagesForDebugLog = messages;
            ChatResponse response =
                    retryPolicy.call(
                            () -> {
                                ModelRequestDebugLogger.logBeforeModelCall(
                                        log, demoDebugProperties, tools, messagesForDebugLog, turnForDebugLog);
                                return chatModel.call(prompt);
                            });

            if (!hasToolCalls(response)) {
                String text = extractAssistantText(response);
                return new AgenticTurnResult(LoopTerminalReason.COMPLETED, text, state);
            }

            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, response);
            if (toolResult.returnDirect()) {
                String text = extractAssistantText(response);
                if (text == null || text.isBlank()) {
                    text = "[returnDirect]";
                }
                return new AgenticTurnResult(LoopTerminalReason.COMPLETED, text, state);
            }
            messages = toolResult.conversationHistory();
            log.debug("Agentic loop turn {}: {} messages in history", state.turnCount(), messages.size());
        }
    }

    private static boolean hasToolCalls(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return false;
        }
        List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
        return !CollectionUtils.isEmpty(calls);
    }

    private static String extractAssistantText(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String t = response.getResult().getOutput().getText();
        return t != null ? t : "";
    }
}
