package demo.k8s.agent.query;

import com.fasterxml.jackson.databind.JsonNode;
import demo.k8s.agent.config.AgentPrompts;
import demo.k8s.agent.config.DemoCoordinatorProperties;
import demo.k8s.agent.config.DemoDebugProperties;
import demo.k8s.agent.config.DemoQueryProperties;
import demo.k8s.agent.memory.search.MemorySearchService;
import demo.k8s.agent.plugin.hook.HookService;
import demo.k8s.agent.observability.ModelCallMetrics;
import demo.k8s.agent.observability.SessionStats;
import demo.k8s.agent.observability.TokenCounts;
import demo.k8s.agent.observability.ToolCallMetrics;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event;
import demo.k8s.agent.observability.events.Event.ToolCalledEvent;
import demo.k8s.agent.observability.events.Event.ModelCalledEvent;
import demo.k8s.agent.observability.events.Event.ErrorEvent;
import demo.k8s.agent.observability.logging.ModelRequestDebugLogger;
import demo.k8s.agent.observability.logging.StructuredLogger;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.observability.metrics.MetricsCollector;
import demo.k8s.agent.skills.SkillService;
import demo.k8s.agent.state.ChatMessage;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.state.MessageType;
import demo.k8s.agent.tools.UnifiedToolExecutor;
import demo.k8s.agent.tools.local.LocalToolExecutor;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.tools.local.StreamingToolExecutor;
import demo.k8s.agent.toolsystem.*;
import demo.k8s.agent.toolstate.ToolStateService;
import demo.k8s.agent.toolstate.ToolStatus;
import demo.k8s.agent.toolstate.ToolArtifact;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;

/**
 * 增强版 Agentic Query Loop，与 Claude Code {@code query.ts} / {@code QueryEngine.ts} 对齐。
 * <p>
 * TODO: 修复 Spring AI API 兼容性问题
 */
@Service
public class EnhancedAgenticQueryLoop {

    private static final Logger log = LoggerFactory.getLogger(EnhancedAgenticQueryLoop.class);
    private static final String COMPACT_BOUNDARY_PREFIX = "__COMPACT_BOUNDARY__:";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 三元消费者接口（用于 toolCallId, toolName, input/result）
     */
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    private final ChatModel chatModel;
    private final CompactionPipeline compactionPipeline;
    private final ModelCallRetryPolicy retryPolicy;
    private final DemoQueryProperties queryProperties;
    private final ToolRegistry toolRegistry;
    private final ToolPermissionContext toolPermissionContext;
    private final ToolFeatureFlags toolFeatureFlags;
    private final McpToolProvider mcpToolProvider;
    private final DemoCoordinatorProperties coordinatorProperties;
    private final PermissionManager permissionManager;
    private final SessionStats sessionStats;
    private final EventBus eventBus;
    private final MetricsCollector metricsCollector;
    private final MemorySearchService memorySearchService;
    private final HookService hookService;
    private final ToolStateService toolStateService;
    private final ToolCallingManager toolCallingManager;
    private final SkillService skillService;
    private final UnifiedToolExecutor unifiedToolExecutor;
    private final LocalToolExecutor localToolExecutor;
    private final StreamingToolExecutor streamingToolExecutor;
    private final DemoDebugProperties demoDebugProperties;
    private final ConversationManager conversationManager;

    // 权限确认回调（用于 WebSocket TUI）
    private Function<PermissionRequest, CompletableFuture<PermissionResult>> permissionCallback;
    // 当前 WebSocket 会话（用于 ToolState 广播跳过发送者）
    private WebSocketSession currentWebSocketSession;

    public EnhancedAgenticQueryLoop(
            ChatModel chatModel,
            @Qualifier("enhancedCompactionPipeline") CompactionPipeline compactionPipeline,
            ModelCallRetryPolicy retryPolicy,
            DemoQueryProperties queryProperties,
            ToolRegistry toolRegistry,
            ToolPermissionContext toolPermissionContext,
            ToolFeatureFlags toolFeatureFlags,
            McpToolProvider mcpToolProvider,
            DemoCoordinatorProperties coordinatorProperties,
            PermissionManager permissionManager,
            SessionStats sessionStats,
            EventBus eventBus,
            MetricsCollector metricsCollector,
            MemorySearchService memorySearchService,
            HookService hookService,
            ToolStateService toolStateService,
            ToolCallingManager toolCallingManager,
            SkillService skillService,
            UnifiedToolExecutor unifiedToolExecutor,
            LocalToolExecutor localToolExecutor,
            StreamingToolExecutor streamingToolExecutor,
            DemoDebugProperties demoDebugProperties,
            ConversationManager conversationManager) {
        this.chatModel = chatModel;
        this.compactionPipeline = compactionPipeline;
        this.retryPolicy = retryPolicy;
        this.queryProperties = queryProperties;
        this.toolRegistry = toolRegistry;
        this.toolPermissionContext = toolPermissionContext;
        this.toolFeatureFlags = toolFeatureFlags;
        this.mcpToolProvider = mcpToolProvider;
        this.coordinatorProperties = coordinatorProperties;
        this.permissionManager = permissionManager;
        this.sessionStats = sessionStats;
        this.eventBus = eventBus;
        this.metricsCollector = metricsCollector;
        this.memorySearchService = memorySearchService;
        this.hookService = hookService;
        this.toolStateService = toolStateService;
        this.toolCallingManager = toolCallingManager;
        this.skillService = skillService;
        this.unifiedToolExecutor = unifiedToolExecutor;
        this.localToolExecutor = localToolExecutor;
        this.streamingToolExecutor = streamingToolExecutor;
        this.demoDebugProperties = demoDebugProperties;
        this.conversationManager = conversationManager;

        // 订阅 ToolCalledEvent 事件，用于在工具执行时创建 artifact
        // 注意：这是在工具执行之后，但可以用来记录工具调用历史
        eventBus.subscribe(Event.ToolCalledEvent.class, event -> {
            log.debug("捕获 ToolCalledEvent: sessionId={}", event.sessionId());
        });
    }

    /**
     * 设置权限确认回调（用于 WebSocket TUI 实时交互）
     */
    public void setPermissionCallback(Function<PermissionRequest, CompletableFuture<PermissionResult>> callback) {
        this.permissionCallback = callback;
    }

    /**
     * 设置当前 WebSocket 会话（用于 ToolState 广播时跳过发送者）
     */
    public void setCurrentWebSocketSession(WebSocketSession session) {
        this.currentWebSocketSession = session;
    }

    /**
     * 运行一个完整的 agentic 回合（带权限检查和指标追踪）
     */
    public AgenticTurnResult run(String userMessage) {
        return runWithCallbacks(userMessage, null, null);
    }

    /**
     * 运行 agentic 回合（带回调支持）
     *
     * @param userMessage 用户输入
     * @param onToolCall 工具调用开始回调（toolCallId, input）
     * @param onTextDelta 文本增量回调（delta 文本）
     */
    public AgenticTurnResult runWithCallbacks(
            String userMessage,
            java.util.function.BiConsumer<String, JsonNode> onToolCall,
            java.util.function.Consumer<String> onTextDelta) {
        return runWithCallbacks(userMessage,
            onToolCall != null ? (id, name, input) -> onToolCall.accept(id, input) : null,
            onTextDelta, null, null, null, null);
    }

    /**
     * 运行 agentic 回合（带回调支持，含 reasoning/thinking 增量）
     */
    public AgenticTurnResult runWithCallbacks(
            String userMessage,
            java.util.function.BiConsumer<String, JsonNode> onToolCall,
            java.util.function.Consumer<String> onTextDelta,
            java.util.function.Consumer<String> onReasoningDelta) {
        return runWithCallbacks(userMessage,
            onToolCall != null ? (id, name, input) -> onToolCall.accept(id, input) : null,
            onTextDelta, onReasoningDelta, null, null, null);
    }

    /**
     * 运行 agentic 回合（带回调支持，含 reasoning 与中间 assistant 文本）
     */
    public AgenticTurnResult runWithCallbacks(
            String userMessage,
            java.util.function.BiConsumer<String, JsonNode> onToolCall,
            java.util.function.Consumer<String> onTextDelta,
            java.util.function.Consumer<String> onReasoningDelta,
            java.util.function.Consumer<String> onIntermediateAssistantText,
            java.util.function.Consumer<LoopStateEvent> onStateTransition) {
        return runWithCallbacks(userMessage,
            onToolCall != null ? (id, name, input) -> onToolCall.accept(id, input) : null,
            onTextDelta, onReasoningDelta, onIntermediateAssistantText, onStateTransition, null);
    }

    /**
     * 运行 agentic 回合（带回调支持，含 reasoning 与中间 assistant 文本，以及工具执行完成回调）
     * 使用 BiConsumer 适配旧代码（不传递 toolName）
     *
     * @param userMessage 用户输入
     * @param onToolCall 工具调用开始回调（toolCallId, input）
     * @param onTextDelta 文本增量回调（delta 文本）
     * @param onReasoningDelta reasoning/thinking 增量回调
     * @param onIntermediateAssistantText 中间 assistant 文本回调（工具执行前的说明）
     * @param onStateTransition 状态变迁回调
     * @param onToolResult 工具执行完成回调（toolCallId, result）
     */
    public AgenticTurnResult runWithCallbacks(
            String userMessage,
            java.util.function.BiConsumer<String, JsonNode> onToolCall,
            java.util.function.Consumer<String> onTextDelta,
            java.util.function.Consumer<String> onReasoningDelta,
            java.util.function.Consumer<String> onIntermediateAssistantText,
            java.util.function.Consumer<LoopStateEvent> onStateTransition,
            java.util.function.BiConsumer<String, LocalToolResult> onToolResult) {
        return runWithCallbacks(userMessage,
            onToolCall != null ? (id, name, input) -> onToolCall.accept(id, input) : null,
            onTextDelta, onReasoningDelta, onIntermediateAssistantText, onStateTransition,
            onToolResult != null ? (id, name, result) -> onToolResult.accept(id, result) : null);
    }

    /**
     * 运行 agentic 回合（带回调支持，含 reasoning 与中间 assistant 文本，以及工具执行完成回调）
     *
     * @param userMessage 用户输入
     * @param onToolCall 工具调用开始回调（toolCallId, toolName, input）
     * @param onTextDelta 文本增量回调（delta 文本）
     * @param onReasoningDelta reasoning/thinking 增量回调
     * @param onIntermediateAssistantText 中间 assistant 文本回调（工具执行前的说明）
     * @param onStateTransition 状态变迁回调
     * @param onToolResult 工具执行完成回调（toolCallId, toolName, result）
     */
    public AgenticTurnResult runWithCallbacks(
            String userMessage,
            TriConsumer<String, String, JsonNode> onToolCall,
            java.util.function.Consumer<String> onTextDelta,
            java.util.function.Consumer<String> onReasoningDelta,
            java.util.function.Consumer<String> onIntermediateAssistantText,
            java.util.function.Consumer<LoopStateEvent> onStateTransition,
            TriConsumer<String, String, LocalToolResult> onToolResult) {

        log.info("开始 agentic query loop: userMessage={}", truncate(userMessage, 100));

        // 记录用户输入（结构化日志）
        String sessionId = TraceContext.getSessionId() != null ? TraceContext.getSessionId() : "";
        String userId = TraceContext.getUserId() != null ? TraceContext.getUserId() : "";
        StructuredLogger.logUserInput(sessionId, userId, userMessage);

        // Token 计数累加器（累积所有模型调用的 token）
        // 使用数组包装以在 lambda 中使用（需要 final 引用）
        final TokenCounts[] totalTokenCountsRef = new TokenCounts[] { TokenCounts.ZERO };

        // 准备工具列表
        List<ToolCallback> tools =
                toolRegistry.filteredCallbacks(
                        toolPermissionContext, toolFeatureFlags, mcpToolProvider.loadMcpTools());

        log.debug("加载工具数量：{}", tools.size());

        ToolCallingChatOptions options =
                ToolCallingChatOptions.builder()
                        .toolCallbacks(tools)
                        // 关闭 Spring AI 内部工具执行，改为应用层显式调度，保证中间过程可观测。
                        .internalToolExecutionEnabled(false)
                        .build();

        // 构建系统提示，动态注入技能提示词（每次请求时检查版本变化）
        String baseSystem =
                coordinatorProperties.isEnabled()
                        ? AgentPrompts.COORDINATOR_ORCHESTRATOR_ONLY
                        : AgentPrompts.DEMO_COORDINATOR_SYSTEM;

        // 动态获取技能提示词（版本变化时自动重载）
        String skillsPrompt = skillService.buildSkillsPrompt();
        String system = baseSystem + (skillsPrompt.isEmpty() ? "" : "\n\n" + skillsPrompt);

        // 注入记忆上下文（语义搜索相关记忆）
        String memoryContext = memorySearchService != null
                ? memorySearchService.getRelevantMemoriesForContext(userMessage, sessionId)
                : "";

        if (!memoryContext.isEmpty()) {
            system = system + memoryContext;
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.addAll(buildConversationHistory(userMessage));
        messages.add(new UserMessage(userMessage));

        QueryLoopState state = QueryLoopState.initial();

        // Agentic 主循环
        while (true) {
            // 对齐 Claude Code 的 compact boundary 语义：后续回合仅使用最近边界之后的消息。
            messages = messagesAfterLastCompactBoundary(messages);
            String compactionParentId = "compact-chain-" + UUID.randomUUID();
            TrimOutcome trimOutcome = trimLargeToolResponses(messages, queryProperties.getMicrocompactMaxCharsPerToolResponse());
            messages = trimOutcome.messages();
            if (trimOutcome.trimmedResponses() > 0 && onStateTransition != null) {
                onStateTransition.accept(new LoopStateEvent(
                        "tool-result-trim-message",
                        String.format("[ToolBudget] 已裁剪 %d 条工具输出，累计裁剪 %d 字符",
                                trimOutcome.trimmedResponses(), trimOutcome.trimmedChars()),
                        Map.of(
                                "parentId", compactionParentId,
                                "phase", "trim",
                                "trimmedResponses", trimOutcome.trimmedResponses(),
                                "trimmedChars", trimOutcome.trimmedChars(),
                                "maxCharsPerResponse", queryProperties.getMicrocompactMaxCharsPerToolResponse())));
            }
            SnipOutcome snipOutcome = snipHistoryByBudget(messages, queryProperties.getFullCompactThresholdChars());
            messages = snipOutcome.messages();
            if (snipOutcome.snippedMessages() > 0 && onStateTransition != null) {
                onStateTransition.accept(new LoopStateEvent(
                        "snip-history-message",
                        String.format("[Snip] 已裁剪中段历史 %d 条，字符 %d -> %d",
                                snipOutcome.snippedMessages(), snipOutcome.beforeChars(), snipOutcome.afterChars()),
                        Map.of(
                                "parentId", compactionParentId,
                                "phase", "snip",
                                "snippedMessages", snipOutcome.snippedMessages(),
                                "beforeChars", snipOutcome.beforeChars(),
                                "afterChars", snipOutcome.afterChars(),
                                "snipBudgetChars", snipOutcome.budgetChars())));
            }
            // 检查最大轮次
            if (state.turnCount() >= queryProperties.getMaxTurns()) {
                log.warn("达到最大轮次限制：{}", state.turnCount());
                return new AgenticTurnResult(LoopTerminalReason.MAX_TURNS, "", state, totalTokenCountsRef[0]);
            }

            // 更新轮次状态
            ContinuationReason cont =
                    state.turnCount() == 0 ? ContinuationReason.INITIAL : ContinuationReason.TOOL_FOLLOW_UP;
            state = state.nextTurn(cont);
            String turnId = "turn-" + UUID.randomUUID();

            // 更新模型调用前压缩
            int preCompactCount = messages.size();
            int preCompactChars = MessageTextEstimator.estimateChars(messages);
            messages = compactionPipeline.compactBeforeModelCall(messages);
            int postCompactCount = messages.size();
            int postCompactChars = MessageTextEstimator.estimateChars(messages);
            if (containsSummaryCompactMarker(messages) && onStateTransition != null) {
                onStateTransition.accept(new LoopStateEvent(
                        "summary-compact-message",
                        String.format("[SummaryCompact] 摘要压缩已生效，字符 %d -> %d", preCompactChars, postCompactChars),
                        Map.of(
                                "parentId", compactionParentId,
                                "phase", "summary",
                                "turnId", turnId,
                                "beforeChars", preCompactChars,
                                "afterChars", postCompactChars,
                                "beforeCount", preCompactCount,
                                "afterCount", postCompactCount)));
            }
            if (postCompactCount < preCompactCount) {
                String compactId = "compact-" + UUID.randomUUID();
                // 写入 compact 边界锚点（下轮会通过 messagesAfterLastCompactBoundary 生效）。
                List<Message> bounded = new ArrayList<>();
                bounded.add(new SystemMessage(COMPACT_BOUNDARY_PREFIX + compactId));
                bounded.addAll(messages);
                messages = bounded;
                state = state.withCompactionIncrement(ContinuationReason.POST_COMPACTION_RETRY);
                if (onStateTransition != null) {
                    onStateTransition.accept(new LoopStateEvent(
                            "compact-boundary-message",
                            String.format("[Context] 已压缩：%d -> %d（累计 %d 次）",
                                    preCompactCount, postCompactCount, state.compactionCount()),
                            Map.of(
                                    "parentId", compactionParentId,
                                    "phase", "boundary",
                                    "compactId", compactId,
                                    "turnId", turnId,
                                    "preCompactCount", preCompactCount,
                                    "postCompactCount", postCompactCount,
                                    "compactionCount", state.compactionCount(),
                                    "turnCount", state.turnCount())));
                }
            }
            log.debug("Compaction 后消息数：{}", messages.size());

            // 开始模型调用指标追踪
            ModelCallMetrics modelCallMetrics = sessionStats.startModelCall("unknown");
            if (onStateTransition != null) {
                onStateTransition.accept(new LoopStateEvent(
                        "assistant-wait-message",
                        String.format("[Wait] 第 %d 轮正在等待模型返回...", state.turnCount()),
                        Map.of(
                                "turnId", turnId,
                                "turnCount", state.turnCount(),
                                "phase", "await_model")));
            }

            // 注意：Spring AI 会在内部自动执行工具调用
            // 为了在工具执行之前拦截，我们需要在调用 chatModel 之前设置回调
            Prompt prompt = new Prompt(messages, options);
            final int turnForDebugLog = state.turnCount();
            final List<Message> messagesForDebugLog = messages;
            final List<ToolCallback> toolsForDebugLog = tools;

            ChatResponse response;
            try {
                response = retryPolicy.call(() -> {
                    Instant start = Instant.now();
                    ModelRequestDebugLogger.logBeforeModelCall(
                            log, demoDebugProperties, toolsForDebugLog, messagesForDebugLog, turnForDebugLog);
                    // Spring AI 会在内部自动执行工具调用
                    ChatResponse resp = chatModel.call(prompt);
                    Instant end = Instant.now();

                    // 提取 Token 计数
                    TokenCounts counts = extractTokenCounts(resp);
                    // 累加 Token 计数
                    totalTokenCountsRef[0] = totalTokenCountsRef[0].add(counts);
                    sessionStats.completeModelCall(modelCallMetrics, counts);

                    // 记录模型调用（结构化日志和事件）
                    long latencyMs = Duration.between(start, end).toMillis();
                    StructuredLogger.logModelResponse(sessionId, userId, "unknown",
                        (int) counts.inputTokens(), (int) counts.outputTokens(), latencyMs, resp.getResult().getOutput().getText());
                    eventBus.publish(new ModelCalledEvent(sessionId, userId, "unknown",
                        (int) counts.inputTokens(), (int) counts.outputTokens(), latencyMs));

                    return resp;
                });

                // Spring AI 已经执行了工具，现在调用 onToolCall 回调
                // 注意：这是在工具执行之后调用，仅用于记录目的
                if (onToolCall != null) {
                    logToolCallsFromResponse(response, onToolCall);
                }
            } catch (Exception e) {
                sessionStats.failModelCall(modelCallMetrics, e.getMessage());
                log.error("模型调用失败", e);

                // 记录错误事件
                eventBus.publish(new ErrorEvent(sessionId, userId, "MODEL_ERROR",
                    e.getMessage(), e.getStackTrace() != null ? e.getStackTrace()[0].toString() : ""));

                return new AgenticTurnResult(LoopTerminalReason.ERROR, "模型调用失败：" + e.getMessage(), state, totalTokenCountsRef[0]);
            }

            // 检查是否有工具调用
            // 注意：Spring AI 会在内部自动执行工具，所以 hasToolCalls 可能返回 false
            // 但我们可以从响应中检查是否有工具执行结果
            boolean hasTools = hasToolCalls(response);
            String responseText = extractAssistantText(response);

            log.info("模型响应检查：hasToolCalls={}, responseText={}", hasTools,
                responseText != null ? (responseText.length() > 50 ? responseText.substring(0, 50) + "..." : responseText) : "null");

            // 若模型返回 reasoning/thinking 内容，透传给上层（研发阶段可视化）
            String reasoningText = extractReasoningText(response);
            if (onReasoningDelta != null && reasoningText != null && !reasoningText.isEmpty()) {
                streamText(reasoningText, onReasoningDelta);
            }

            // 仅在“将继续执行工具”的回合透传中间说明，避免与最终答复重复。
            if (hasTools && onIntermediateAssistantText != null
                    && responseText != null && !responseText.isBlank()) {
                onIntermediateAssistantText.accept(responseText);
            }

            // 如果没有工具调用但有响应文本，直接返回
            // 注意：Spring AI 可能已经执行了工具，但我们无法获取工具调用信息
            if (!hasTools) {
                state = new QueryLoopState(
                        state.turnCount(),
                        state.toolBatchCount(),
                        state.compactionCount(),
                        state.maxOutputTokensRecoveryCount(),
                        state.hasAttemptedReactiveCompact(),
                        ContinuationReason.TERMINAL_NO_TOOLS);
                log.info("Agentic loop 完成：turns={}, responseLength={}", state.turnCount(), responseText.length());

                // 如果有文本增量回调，流式发送文本
                if (onTextDelta != null && !responseText.isEmpty()) {
                    streamText(responseText, onTextDelta);
                }

                return new AgenticTurnResult(LoopTerminalReason.COMPLETED, responseText, state, totalTokenCountsRef[0]);
            }

            // 处理工具调用（带权限检查）
            List<ExecutedToolCall> executedToolCalls;
            try {
                log.info("开始执行工具调用检查，工具数量：{}", response.getResult().getOutput().getToolCalls() != null ? response.getResult().getOutput().getToolCalls().size() : 0);
                executedToolCalls = executeToolsWithPermissionCheck(response, prompt, options, onToolCall, onToolResult);
            } catch (PermissionDeniedException e) {
                log.warn("工具调用被拒绝：{}", e.getMessage());
                return new AgenticTurnResult(
                        LoopTerminalReason.COMPLETED,
                        "工具调用被用户拒绝：" + e.getToolName(),
                        state,
                        totalTokenCountsRef[0]);
            }

            // 显式回写本轮 assistant(toolCalls) + tool_response，进入下一轮
            // 直接复用模型原始 assistant 输出（包含 toolCalls），避免依赖不可见构造器。
            messages.add(response.getResult().getOutput());

            List<ToolResponseMessage.ToolResponse> toolResponses = executedToolCalls.stream()
                    .map(c -> new ToolResponseMessage.ToolResponse(
                            c.toolCall().id(),
                            c.toolCall().name(),
                            c.output() != null ? c.output() : ""))
                    .toList();
            if (!toolResponses.isEmpty()) {
                messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
            }
            state = state.withToolBatchIncrement(ContinuationReason.NEXT_TURN);
            if (onStateTransition != null) {
                String toolBatchId = "tool-batch-" + UUID.randomUUID();
                onStateTransition.accept(new LoopStateEvent(
                        "assistant-loop-state-message",
                        String.format("[Loop] 第 %d 轮执行工具批次 #%d（工具 %d 个）",
                                state.turnCount(), state.toolBatchCount(), executedToolCalls.size()),
                        Map.of(
                                "toolBatchId", toolBatchId,
                                "turnId", turnId,
                                "turnCount", state.turnCount(),
                                "toolBatchCount", state.toolBatchCount(),
                                "toolCallCount", executedToolCalls.size(),
                                "continuation", state.lastContinuation().name())));
            }
            log.debug("执行了 {} 个工具调用，并已回写 {} 条 tool_response", executedToolCalls.size(), toolResponses.size());
        }
    }

    /**
     * 执行工具调用并进行权限检查
     * TODO: 修复 Spring AI API 兼容性
     */
    private List<ExecutedToolCall> executeToolsWithPermissionCheck(
            ChatResponse response,
            Prompt prompt,
            ToolCallingChatOptions options,
            TriConsumer<String, String, JsonNode> onToolCall,
            TriConsumer<String, String, LocalToolResult> onToolResult) {

        AssistantMessage output = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        if (CollectionUtils.isEmpty(toolCalls)) {
            return List.of();
        }

        // 高亮显示批量工具调用（特别是 spawn_subagent）
        long spawnCount = toolCalls.stream().filter(tc -> "spawn_subagent".equals(tc.name())).count();
        if (spawnCount > 0) {
            log.info("[BATCH-TOOL-CALLS] Total tools: {}, spawn_subagent count: {}, all tools: {}",
                    toolCalls.size(),
                    spawnCount,
                    toolCalls.stream().map(AssistantMessage.ToolCall::name).toList());
        } else {
            log.info("[TOOL-CALLS] Tools to execute: {}",
                    toolCalls.stream().map(AssistantMessage.ToolCall::name).toList());
        }

        // 使用并行执行器执行所有工具
        return executeToolsInParallel(toolCalls, onToolCall, onToolResult);
    }

    /**
     * 使用 StreamingToolExecutor 并行执行多个工具调用
     * <p>
     * 参考 Claude Code 的 StreamingToolExecutor 实现：
     * - 并发安全的工具可以并行执行
     * - 非并发工具独占执行（exclusive access）
     * - Bash 工具错误会级联取消兄弟工具
     *
     * @param toolCalls 工具调用列表
     * @param onToolCall 工具调用通知回调（toolCallId, toolName, input）
     * @param onToolResult 工具执行完成回调（toolCallId, toolName, result）
     * @return 执行后的工具调用结果
     */
    private List<ExecutedToolCall> executeToolsInParallel(
            List<AssistantMessage.ToolCall> toolCalls,
            TriConsumer<String, String, JsonNode> onToolCall,
            TriConsumer<String, String, LocalToolResult> onToolResult) {

        String sessionId = TraceContext.getSessionId();
        String userId = TraceContext.getUserId();
        List<ExecutedToolCall> results = new ArrayList<>();

        // 1. 预处理：对所有工具进行权限检查和 Hook 检查
        List<PreparedToolCall> preparedCalls = new ArrayList<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            // 包装 onToolCall 回调，传递工具调用 ID 和工具名称
            TriConsumer<String, String, JsonNode> wrappedOnToolCall = onToolCall != null
                ? (toolCallId, toolName, input) -> onToolCall.accept(tc.id(), tc.name(), input)
                : null;
            PreparedToolCall prepared = prepareToolCall(tc, wrappedOnToolCall, sessionId, userId);
            if (prepared != null) {
                preparedCalls.add(prepared);
            }
        }

        if (preparedCalls.isEmpty()) {
            return results;
        }

        log.info("[ParallelExecutor] 准备执行 {} 个工具", preparedCalls.size());

        // 2. 使用 StreamingToolExecutor 并行执行
        for (PreparedToolCall prepared : preparedCalls) {
            streamingToolExecutor.addTool(
                    prepared.toolCall.id(),
                    prepared.toolCall.name(),
                    prepared.input,
                    prepared.tool
            );
        }

        // 3. 等待所有工具完成
        List<StreamingToolExecutor.ToolResultWithId> toolResults =
                streamingToolExecutor.getAllResults();

        // 4. 处理结果（包装 onToolResult 回调，传递工具调用 ID 和工具名称）
        // 需要从 preparedCalls 中获取工具名称和 artifactId
        Map<String, String> toolIdToName = new HashMap<>();
        Map<String, String> toolIdToArtifactId = new HashMap<>();
        for (PreparedToolCall prepared : preparedCalls) {
            toolIdToName.put(prepared.toolCall.id(), prepared.toolCall.name());
            if (prepared.artifactId != null) {
                toolIdToArtifactId.put(prepared.toolCall.id(), prepared.artifactId);
            }
        }

        for (StreamingToolExecutor.ToolResultWithId toolResult : toolResults) {
            String toolName = toolIdToName.getOrDefault(toolResult.toolUseId, "unknown");
            String artifactId = toolIdToArtifactId.get(toolResult.toolUseId);
            TriConsumer<String, String, LocalToolResult> wrappedOnToolResult = onToolResult != null
                ? (tId, tName, result) -> onToolResult.accept(toolResult.toolUseId, toolName, result)
                : null;
            ExecutedToolCall executed = processToolResult(
                    toolResult.toolUseId,
                    toolName,
                    artifactId,
                    toolResult.result,
                    sessionId,
                    userId,
                    wrappedOnToolResult
            );
            if (executed != null) {
                results.add(executed);
            }
        }

        return results;
    }

    /**
     * 准备单个工具调用（权限检查、Hook 检查）
     */
    private PreparedToolCall prepareToolCall(
            AssistantMessage.ToolCall tc,
            TriConsumer<String, String, JsonNode> onToolCall,
            String sessionId,
            String userId) {

        // 查找工具定义
        ClaudeLikeTool tool = findToolByName(tc.name());
        if (tool == null) {
            log.warn("未找到工具定义：{}", tc.name());
            return null;
        }

        // 解析输入 JSON
        JsonNode input = parseInput(tc.arguments());

        // 通知工具调用开始（传递 toolCallId 和 toolName）
        if (onToolCall != null) {
            onToolCall.accept(tc.id(), tc.name(), input);
        }

        // 创建 ToolArtifact
        String artifactId = null;
        if (toolStateService != null && sessionId != null && userId != null) {
            try {
                Map<String, Object> initialBody = new HashMap<>();
                initialBody.put("todo", "执行工具：" + tc.name());
                initialBody.put("input", objectMapper.valueToTree(input));
                initialBody.put("version", 1);

                ToolArtifact artifact = toolStateService.createToolArtifact(
                    sessionId, userId, tc.name(), tc.name(),
                    ToolStatus.TODO, initialBody, currentWebSocketSession);
                artifactId = artifact.getId();
                log.debug("创建 ToolArtifact: artifactId={}, toolName={}", artifactId, tc.name());
            } catch (Exception e) {
                log.debug("创建 ToolArtifact 失败：{}", e.getMessage());
            }
        }

        // 执行 BEFORE Hook
        if (hookService != null && !hookService.beforeToolCall(sessionId, userId, tc.name(), input)) {
            log.info("Hook blocked tool call: {}", tc.name());
            updateToolArtifactFailed(toolStateService, artifactId, userId, "被 Before Hook 阻止");
            return null;
        }

        // 权限检查
        PermissionRequest request = permissionManager.requiresPermission(tool, input, toolPermissionContext);
        if (request != null) {
            PermissionResult result = waitForUserConfirmation(request);
            if (result.isDenied() || result.needsConfirmation()) {
                updateToolArtifactFailed(toolStateService, artifactId, userId,
                        result.isDenied() ? "用户拒绝：" + result.getDenyReason() : "等待用户确认超时");
                throw new PermissionDeniedException(tc.name(),
                        result.isDenied() ? result.getDenyReason() : "等待用户确认超时");
            }
        }

        updateToolArtifactExecuting(toolStateService, artifactId, userId);

        return new PreparedToolCall(tc, tool, input, artifactId);
    }

    /**
     * 处理工具执行结果
     */
    private ExecutedToolCall processToolResult(
            String toolUseId,
            String toolName,
            String artifactId,
            LocalToolResult result,
            String sessionId,
            String userId,
            TriConsumer<String, String, LocalToolResult> onToolResult) {

        String toolOutput;
        boolean success = result.isSuccess();

        if (success) {
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("content", result.getContent());
            outputData.put("location", result.getExecutionLocation());
            outputData.put("durationMs", result.getDurationMs());
            if (result.getMetadata() != null) {
                outputData.put("metadata", result.getMetadata());
            }
            try {
                toolOutput = objectMapper.writeValueAsString(outputData);
            } catch (Exception e) {
                toolOutput = result.getContent();
            }
        } else {
            String toolErr = result.getError();
            if (toolErr == null || toolErr.isBlank()) {
                toolErr = result.getContent();
            }
            if (toolErr == null || toolErr.isBlank()) {
                toolErr = "UNKNOWN_TOOL_ERROR";
            }
            try {
                Map<String, Object> errPayload = new HashMap<>();
                errPayload.put("error", toolErr);
                errPayload.put("success", false);
                toolOutput = objectMapper.writeValueAsString(errPayload);
            } catch (Exception e) {
                toolOutput = "{\"error\": \"" + toolErr + "\"}";
            }
        }

        // 记录工具调用日志
        long toolLatencyMs = result.getDurationMs();
        StructuredLogger.logToolCall(sessionId, userId, toolName,
                "{}", toolOutput, toolLatencyMs, success);

        eventBus.publish(new ToolCalledEvent(sessionId, userId, toolName,
                "{}", toolOutput, toolLatencyMs, success));

        // 更新 ToolArtifact 为 COMPLETED 或 FAILED
        if (toolStateService != null && artifactId != null) {
            try {
                Map<String, Object> resultBody = new HashMap<>();
                resultBody.put("output", toolOutput);
                resultBody.put("progress", success ? "已完成" : "执行失败");
                resultBody.put("durationMs", result.getDurationMs());
                enrichToolArtifactBodyFromToolOutputJson(resultBody, toolOutput);

                ToolStatus status = success ? ToolStatus.COMPLETED : ToolStatus.FAILED;
                toolStateService.updateToolArtifact(
                    artifactId, userId, status, resultBody, 2, currentWebSocketSession);

                log.info("更新 ToolArtifact：artifactId={}, status={}", artifactId, status);
            } catch (Exception e) {
                log.warn("更新 ToolArtifact 失败：{}", e.getMessage());
            }
        }

        // 调用工具完成回调（通知前端工具执行完成）
        if (onToolResult != null) {
            try {
                onToolResult.accept(toolUseId, toolName, result);
            } catch (Exception e) {
                log.warn("onToolResult 回调执行失败：{}", e.getMessage());
            }
        }

        // 构造返回结果
        AssistantMessage.ToolCall resultCall = new AssistantMessage.ToolCall(
                toolUseId, toolName, "{}", toolOutput);

        return new ExecutedToolCall(resultCall, toolOutput);
    }

    /**
     * 从工具调用 ID 中提取工具名称
     * 格式：toolName_randomId（如：bash_tc_123456）
     * @deprecated 已废弃，直接使用 toolName 参数
     */
    @Deprecated
    private String extractToolNameFromId(String toolUseId) {
        if (toolUseId == null || toolUseId.isEmpty()) {
            return "unknown";
        }
        return "unknown";
    }

    private void updateToolArtifactFailed(ToolStateService service, String artifactId,
                                           String userId, String error) {
        if (service != null && artifactId != null) {
            try {
                Map<String, Object> failedBody = new HashMap<>();
                failedBody.put("error", error);
                failedBody.put("version", 2);
                service.updateToolArtifact(artifactId, userId, ToolStatus.FAILED, failedBody, 1, currentWebSocketSession);
            } catch (Exception e) {
                log.debug("更新 ToolArtifact 失败：{}", e.getMessage());
            }
        }
    }

    private void updateToolArtifactExecuting(ToolStateService service, String artifactId, String userId) {
        if (service != null && artifactId != null) {
            try {
                Map<String, Object> executingBody = new HashMap<>();
                executingBody.put("progress", "执行中...");
                executingBody.put("version", 2);
                service.updateToolArtifact(artifactId, userId, ToolStatus.EXECUTING, executingBody, 1, currentWebSocketSession);
            } catch (Exception e) {
                log.debug("更新 ToolArtifact 失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 预准备的工具调用（已通过权限检查）
     */
    private static class PreparedToolCall {
        final AssistantMessage.ToolCall toolCall;
        final ClaudeLikeTool tool;
        final JsonNode input;
        final String artifactId;

        PreparedToolCall(AssistantMessage.ToolCall toolCall, ClaudeLikeTool tool,
                        JsonNode input, String artifactId) {
            this.toolCall = toolCall;
            this.tool = tool;
            this.input = input;
            this.artifactId = artifactId;
        }
    }

    private List<ExecutedToolCall> executeToolsWithPermissionCheckOld(
            ChatResponse response,
            Prompt prompt,
            ToolCallingChatOptions options,
            java.util.function.BiConsumer<String, JsonNode> onToolCall) {

        AssistantMessage output = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        if (CollectionUtils.isEmpty(toolCalls)) {
            return List.of();
        }

        List<ExecutedToolCall> approvedCalls = new ArrayList<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            // 查找工具定义
            ClaudeLikeTool tool = findToolByName(tc.name());

            if (tool == null) {
                log.warn("未找到工具定义：{}", tc.name());
                continue;
            }

            // 解析输入 JSON
            JsonNode input = parseInput(tc.arguments());

            // 通知工具调用开始
            if (onToolCall != null) {
                onToolCall.accept(tc.name(), input);
            }

            // 获取会话信息用于 Hook
            String sessionId = TraceContext.getSessionId();
            String userId = TraceContext.getUserId();

            // 创建 ToolArtifact（TODO 状态）
            String artifactId = null;
            if (toolStateService != null && sessionId != null && userId != null) {
                try {
                    Map<String, Object> initialBody = new HashMap<>();
                    initialBody.put("todo", "执行工具：" + tc.name());
                    initialBody.put("input", objectMapper.valueToTree(input));
                    initialBody.put("version", 1);

                    ToolArtifact artifact = toolStateService.createToolArtifact(
                        sessionId, userId, tc.name(), tc.name(),
                        ToolStatus.TODO, initialBody, currentWebSocketSession);
                    artifactId = artifact.getId();
                    log.info("创建 ToolArtifact: artifactId={}, toolName={}", artifactId, tc.name());
                } catch (Exception e) {
                    log.warn("创建 ToolArtifact 失败：{}", e.getMessage());
                }
            }

            // 执行 BEFORE Hook（Hook 可以阻止工具调用）
            if (hookService != null && !hookService.beforeToolCall(sessionId, userId, tc.name(), input)) {
                log.info("Hook blocked tool call: {}", tc.name());

                // 更新 ToolArtifact 为 FAILED
                if (toolStateService != null && artifactId != null) {
                    try {
                        Map<String, Object> failedBody = new HashMap<>();
                        failedBody.put("error", "被 Before Hook 阻止");
                        failedBody.put("version", 2);
                        toolStateService.updateToolArtifact(
                            artifactId, userId, ToolStatus.FAILED, failedBody, 1, currentWebSocketSession);
                    } catch (Exception e) {
                        log.warn("更新 ToolArtifact 失败：{}", e.getMessage());
                    }
                }
                continue;
            }

            // 权限检查
            PermissionRequest request = permissionManager.requiresPermission(tool, input, toolPermissionContext);

            if (request != null) {
                // 需要用户确认
                log.info("工具 {} 需要用户确认", tc.name());

                // 更新 ToolArtifact 为 PENDING_CONFIRMATION
                if (toolStateService != null && artifactId != null) {
                    try {
                        Map<String, Object> pendingBody = new HashMap<>();
                        pendingBody.put("confirmation", Map.of("requested", true));
                        pendingBody.put("version", 2);
                        toolStateService.updateToolArtifact(
                            artifactId, userId, ToolStatus.PENDING_CONFIRMATION, pendingBody, 1, currentWebSocketSession);
                    } catch (Exception e) {
                        log.warn("更新 ToolArtifact 失败：{}", e.getMessage());
                    }
                }

                // 同步等待用户确认
                PermissionResult result = waitForUserConfirmation(request);

                if (result.isDenied()) {
                    // 更新 ToolArtifact 为 FAILED
                    if (toolStateService != null && artifactId != null) {
                        try {
                            Map<String, Object> failedBody = new HashMap<>();
                            failedBody.put("error", "用户拒绝：" + result.getDenyReason());
                            failedBody.put("version", 3);
                            toolStateService.updateToolArtifact(
                                artifactId, userId, ToolStatus.FAILED, failedBody, 2, currentWebSocketSession);
                        } catch (Exception e) {
                            log.warn("更新 ToolArtifact 失败：{}", e.getMessage());
                        }
                    }
                    throw new PermissionDeniedException(tc.name(), result.getDenyReason());
                }

                if (result.needsConfirmation()) {
                    // 超时未确认
                    if (toolStateService != null && artifactId != null) {
                        try {
                            Map<String, Object> failedBody = new HashMap<>();
                            failedBody.put("error", "等待用户确认超时");
                            failedBody.put("version", 3);
                            toolStateService.updateToolArtifact(
                                artifactId, userId, ToolStatus.FAILED, failedBody, 2, currentWebSocketSession);
                        } catch (Exception e) {
                            log.warn("更新 ToolArtifact 失败：{}", e.getMessage());
                        }
                    }
                    throw new PermissionDeniedException(tc.name(), "等待用户确认超时");
                }
            }

            // 更新 ToolArtifact 为 EXECUTING
            if (toolStateService != null && artifactId != null) {
                try {
                    Map<String, Object> executingBody = new HashMap<>();
                    executingBody.put("progress", "执行中...");
                    executingBody.put("version", 3);
                    toolStateService.updateToolArtifact(
                        artifactId, userId, ToolStatus.EXECUTING, executingBody, 2, currentWebSocketSession);
                } catch (Exception e) {
                    log.warn("更新 ToolArtifact 失败：{}", e.getMessage());
                }
            }

            // 开始工具调用指标追踪
            ToolCallMetrics toolMetrics = sessionStats.startToolCall(
                    tc.name(),
                    truncate(tc.arguments(), 100));

            // 特别高亮 spawn_subagent 调用开始
            if ("spawn_subagent".equals(tc.name())) {
                log.info("[SPAWN-START] spawn_subagent executing: sessionId={}, toolCallId={}, args={}",
                        sessionId, tc.id(), truncate(tc.arguments(), 200));
            }

            try {
                // 使用 UnifiedToolExecutor 执行工具
                Map<String, Object> inputMap = objectMapper.convertValue(input, Map.class);
                LocalToolResult result = unifiedToolExecutor.execute(tool, inputMap, toolPermissionContext);
                String toolOutput;

                if (result.isSuccess()) {
                    // 构建结构化输出
                    Map<String, Object> outputData = new HashMap<>();
                    outputData.put("content", result.getContent());
                    outputData.put("location", result.getExecutionLocation());
                    outputData.put("durationMs", result.getDurationMs());
                    if (result.getMetadata() != null) {
                        outputData.put("metadata", result.getMetadata());
                    }
                    toolOutput = objectMapper.writeValueAsString(outputData);
                } else {
                    String toolErr = result.getError();
                    if (toolErr == null || toolErr.isBlank()) {
                        toolErr = result.getContent();
                    }
                    if (toolErr == null || toolErr.isBlank()) {
                        toolErr = "UNKNOWN_TOOL_ERROR";
                    }
                    Map<String, Object> errPayload = new HashMap<>();
                    errPayload.put("error", toolErr);
                    errPayload.put("success", false);
                    errPayload.put("location", result.getExecutionLocation());
                    errPayload.put("durationMs", result.getDurationMs());
                    toolOutput = objectMapper.writeValueAsString(errPayload);
                }

                // 记录成功
                sessionStats.completeToolCall(toolMetrics, toolOutput);
                approvedCalls.add(new ExecutedToolCall(
                        new AssistantMessage.ToolCall(tc.id(), tc.name(), tc.arguments(), toolOutput),
                        toolOutput));

                // 记录工具调用（结构化日志和事件）
                long toolLatencyMs = toolMetrics.getLatencyMs();
                StructuredLogger.logToolCall(sessionId, userId, tc.name(),
                    parseInput(tc.arguments()).toString(), toolOutput, toolLatencyMs, true);
                eventBus.publish(new ToolCalledEvent(sessionId, userId, tc.name(),
                    tc.arguments(), toolOutput, toolLatencyMs, true));
                metricsCollector.recordToolCall(userId, tc.name(), true, toolMetrics.latency());

                // 执行 AFTER Hook
                if (hookService != null) {
                    hookService.afterToolCall(sessionId, userId, tc.name(), input, toolOutput, true, toolLatencyMs);
                }

                // 更新 ToolArtifact 为 COMPLETED（与 UnifiedToolExecutor 一致：顶层附带 metadata，供前端渲染 Task 等工具）
                if (toolStateService != null && artifactId != null) {
                    try {
                        Map<String, Object> completedBody = new HashMap<>();
                        completedBody.put("output", toolOutput);
                        completedBody.put("progress", "已完成");
                        completedBody.put("version", 4);
                        enrichToolArtifactBodyFromToolOutputJson(completedBody, toolOutput);
                        toolStateService.updateToolArtifact(
                            artifactId, userId, ToolStatus.COMPLETED, completedBody, 3, currentWebSocketSession);
                    } catch (Exception e) {
                        log.warn("更新 ToolArtifact 失败：{}", e.getMessage());
                    }
                }

                log.debug("工具调用成功：{} ({} chars)", tc.name(), toolOutput.length());

                // 特别高亮 spawn_subagent 调用（便于排查并行执行问题）
                if ("spawn_subagent".equals(tc.name())) {
                    log.info("[SPAWN-SUCCESS] spawn_subagent completed: sessionId={}, toolCallId={}, outputLength={}",
                            sessionId, tc.id(), toolOutput.length());
                }

            } catch (Exception e) {
                sessionStats.failToolCall(toolMetrics, e.getMessage());
                String traceId = TraceContext.getTraceId();
                String safeErr = e.getMessage();
                if (safeErr == null || safeErr.isBlank()) {
                    safeErr = e.getClass().getSimpleName();
                }
                log.error("工具调用失败：tool={}, sessionId={}, userId={}, traceId={}, toolCallId={}, err={}",
                        tc.name(), sessionId, userId, traceId, tc.id(), safeErr, e);

                // 记录工具调用失败
                long toolLatencyMs = toolMetrics.getLatencyMs();
                StructuredLogger.logToolCall(sessionId, userId, tc.name(),
                    parseInput(tc.arguments()).toString(), safeErr, toolLatencyMs, false);
                eventBus.publish(new ToolCalledEvent(sessionId, userId, tc.name(),
                    tc.arguments(), safeErr, toolLatencyMs, false));
                metricsCollector.recordToolCall(userId, tc.name(), false, toolMetrics.latency());

                // 更新 ToolArtifact 为 FAILED
                if (toolStateService != null && artifactId != null) {
                    try {
                        Map<String, Object> failedBody = new HashMap<>();
                        failedBody.put("error", safeErr);
                        failedBody.put("version", 4);
                        toolStateService.updateToolArtifact(
                            artifactId, userId, ToolStatus.FAILED, failedBody, 3, currentWebSocketSession);
                    } catch (Exception ex) {
                        log.warn("更新 ToolArtifact 失败：{}", ex.getMessage());
                    }
                }

                // 执行 AFTER Hook（错误情况）
                if (hookService != null) {
                    hookService.afterToolCall(sessionId, userId, tc.name(), input, safeErr, false, toolLatencyMs);
                }

                // 添加错误响应到消息
                String errorOutput = "Error: " + safeErr;
                approvedCalls.add(new ExecutedToolCall(
                        new AssistantMessage.ToolCall(
                                tc.id(),
                                tc.name(),
                                tc.arguments(),
                                errorOutput),
                        errorOutput));
            }
        }

        return approvedCalls;
    }

    private record ExecutedToolCall(AssistantMessage.ToolCall toolCall, String output) {}
    public record LoopStateEvent(String subtype, String content, Map<String, Object> metadata) {}
    private record TrimOutcome(List<Message> messages, int trimmedResponses, int trimmedChars) {}
    private record SnipOutcome(List<Message> messages, int snippedMessages, int beforeChars, int afterChars, int budgetChars) {}

    private List<Message> messagesAfterLastCompactBoundary(List<Message> input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        int boundaryIdx = -1;
        for (int i = input.size() - 1; i >= 0; i--) {
            Message m = input.get(i);
            if (m instanceof SystemMessage sm
                    && sm.getText() != null
                    && sm.getText().startsWith(COMPACT_BOUNDARY_PREFIX)) {
                boundaryIdx = i;
                break;
            }
        }
        if (boundaryIdx < 0 || boundaryIdx + 1 >= input.size()) {
            return input;
        }
        return new ArrayList<>(input.subList(boundaryIdx + 1, input.size()));
    }

    private List<Message> buildConversationHistory(String currentUserMessage) {
        int limit = Math.max(0, queryProperties.getHistoryWindowMessages());
        if (limit <= 0 || conversationManager == null) {
            return List.of();
        }
        List<ChatMessage> recent = conversationManager.getHistory(limit);
        if (recent == null || recent.isEmpty()) {
            return List.of();
        }
        List<Message> history = new ArrayList<>();
        for (ChatMessage msg : recent) {
            if (msg == null || msg.content() == null || msg.content().isBlank()) {
                continue;
            }
            if (msg.type() == MessageType.USER) {
                history.add(new UserMessage(msg.content()));
            } else if (msg.type() == MessageType.ASSISTANT) {
                history.add(new AssistantMessage(msg.content()));
            }
        }
        // 某些入口会先调用 ConversationManager.startTurn(currentUserMessage)，这里去掉尾部重复用户消息。
        if (!history.isEmpty()) {
            Message last = history.get(history.size() - 1);
            if (last instanceof UserMessage um
                    && um.getText() != null
                    && um.getText().trim().equals(currentUserMessage == null ? "" : currentUserMessage.trim())) {
                history.remove(history.size() - 1);
            }
        }
        return history;
    }

    private TrimOutcome trimLargeToolResponses(List<Message> input, int maxCharsPerResponse) {
        if (input == null || input.isEmpty() || maxCharsPerResponse <= 0) {
            return new TrimOutcome(input, 0, 0);
        }
        List<Message> result = new ArrayList<>();
        int trimmedResponses = 0;
        int trimmedChars = 0;
        for (Message m : input) {
            if (m instanceof ToolResponseMessage trm) {
                List<ToolResponseMessage.ToolResponse> rs = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    String data = r.responseData();
                    if (data != null && data.length() > maxCharsPerResponse) {
                        int trimmed = data.length() - maxCharsPerResponse;
                        String d = data.substring(0, maxCharsPerResponse) + "\n...[tool-result-trimmed]";
                        rs.add(new ToolResponseMessage.ToolResponse(r.name(), r.id(), d));
                        trimmedResponses += 1;
                        trimmedChars += Math.max(trimmed, 0);
                    } else {
                        rs.add(r);
                    }
                }
                result.add(ToolResponseMessage.builder().responses(rs).build());
            } else {
                result.add(m);
            }
        }
        return new TrimOutcome(result, trimmedResponses, trimmedChars);
    }

    private SnipOutcome snipHistoryByBudget(List<Message> input, int thresholdChars) {
        if (input == null || input.size() <= 4 || thresholdChars <= 0) {
            int current = MessageTextEstimator.estimateChars(input);
            return new SnipOutcome(input, 0, current, current, thresholdChars);
        }
        int before = MessageTextEstimator.estimateChars(input);
        int budget = Math.max((int) (thresholdChars * 0.7d), 20_000);
        if (before <= budget) {
            return new SnipOutcome(input, 0, before, before, budget);
        }
        Message first = input.get(0);
        List<Message> tail = new ArrayList<>();
        int tailChars = 0;
        for (int i = input.size() - 1; i >= 1; i--) {
            Message m = input.get(i);
            int c = MessageTextEstimator.estimateChars(List.of(m));
            if (tailChars + c > budget) {
                break;
            }
            tail.add(0, m);
            tailChars += c;
        }
        List<Message> output = new ArrayList<>();
        output.add(first);
        output.add(new SystemMessage("[snip] 中段历史已按预算裁剪，保留系统语义与最近上下文。"));
        output.addAll(tail);
        int after = MessageTextEstimator.estimateChars(output);
        int snipped = Math.max(input.size() - output.size(), 0);
        return new SnipOutcome(output, snipped, before, after, budget);
    }

    private boolean containsSummaryCompactMarker(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (Message message : messages) {
            if (message instanceof SystemMessage sm) {
                String text = sm.getText();
                if (text != null && text.contains("[上下文已压缩]")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 同步等待用户确认（支持回调和轮询两种方式）
     */
    private PermissionResult waitForUserConfirmation(PermissionRequest request) {
        // 优先使用回调方式（用于 WebSocket TUI）
        if (permissionCallback != null) {
            try {
                CompletableFuture<PermissionResult> future = permissionCallback.apply(request);
                return future.get(5, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                return PermissionResult.deny("等待用户确认超时");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PermissionResult.deny("等待被中断");
            } catch (Exception e) {
                log.error("权限确认回调失败", e);
                return PermissionResult.deny("权限确认失败：" + e.getMessage());
            }
        }

        // 降级到轮询方式（用于 HTTP/SSE）
        return pollForPermission(request);
    }

    /**
     * 轮询方式等待权限响应
     */
    private PermissionResult pollForPermission(PermissionRequest request) {
        long timeoutMs = 300_000L; // 5 分钟
        long intervalMs = 500L;
        long elapsed = 0L;

        while (elapsed < timeoutMs) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PermissionResult.deny("等待被中断");
            }

            // 检查是否有响应
            PermissionResult result = checkPermissionResponse(request);
            if (result != null) {
                return result;
            }

            elapsed += intervalMs;
        }

        // 超时
        return PermissionResult.deny("等待用户确认超时");
    }

    /**
     * 检查是否有权限响应（轮询方式）
     */
    private PermissionResult checkPermissionResponse(PermissionRequest request) {
        // 检查 pending 队列
        List<PermissionRequest> pending = permissionManager.getPendingRequests();
        boolean stillPending = pending.stream().anyMatch(r -> r.id().equals(request.id()));

        if (!stillPending) {
            // 请求已被处理，返回允许（简化处理）
            return PermissionResult.allow();
        }

        return null; // 仍在等待
    }

    /**
     * 查找工具定义
     */
    private ClaudeLikeTool findToolByName(String name) {
        // 从 ToolRegistry 中查找
        for (ToolModule module : toolRegistry.modules()) {
            if (module.spec().name().equals(name)) {
                return module.spec();
            }
        }
        // 从 MCP 工具中查找
        for (ToolModule module : mcpToolProvider.loadMcpTools()) {
            if (module.spec().name().equals(name)) {
                return module.spec();
            }
        }
        return null;
    }

    /**
     * 解析工具输入 JSON
     */
    private JsonNode parseInput(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree(json != null ? json : "{}");
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid tool JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 从 ChatResponse 提取 Token 计数
     */
    private TokenCounts extractTokenCounts(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return TokenCounts.ZERO;
        }

        // Spring AI / OpenAI 的 usage 通常在 metadata 中
        var metadata = response.getMetadata();
        if (metadata == null) {
            return TokenCounts.ZERO;
        }

        // 尝试从 OpenAI 特定字段提取
        Object usageObj = metadata.get("usage");
        if (usageObj != null) {
            // OpenAI Usage 对象
            try {
                long inputTokens = (Long) reflectGet(usageObj, "promptTokens");
                long outputTokens = (Long) reflectGet(usageObj, "completionTokens");
                return new TokenCounts(inputTokens, outputTokens, 0, 0);
            } catch (Exception e) {
                log.debug("提取 token 计数失败：{}", e.getMessage());
            }
        }

        return TokenCounts.ZERO;
    }

    private Object reflectGet(Object obj, String field) throws Exception {
        var method = obj.getClass().getMethod(field);
        return method.invoke(obj);
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
        var output = response.getResult().getOutput();
        String t = output.getText();
        if (t != null && !t.isBlank()) {
            return t;
        }

        // 兼容部分 OpenAI-compatible 实现：中间回合文字只在 rawContent 中可见
        try {
            Object raw = output.getClass().getMethod("getRawContent").invoke(output);
            if (raw != null) {
                String s = String.valueOf(raw).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                    return s;
                }
            }
        } catch (Exception ignored) {
        }

        // 兜底：某些实现会把文本放在 metadata 的 content/message 字段
        try {
            Object md = output.getClass().getMethod("getMetadata").invoke(output);
            if (md instanceof Map<?, ?> map) {
                Object content = map.get("content");
                if (content == null) {
                    content = map.get("message");
                }
                if (content != null) {
                    String s = String.valueOf(content).trim();
                    if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                        return s;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String extractReasoningText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        Object output = response.getResult().getOutput();
        // 优先尝试 AssistantMessage#getReasoningContent（若底层实现支持）
        try {
            Object rc = output.getClass().getMethod("getReasoningContent").invoke(output);
            if (rc != null) {
                String s = String.valueOf(rc).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                    return s;
                }
            }
        } catch (Exception ignored) {
        }

        // 兜底：从 metadata 中尝试常见键
        try {
            Object md = response.getMetadata();
            if (md != null) {
                Object v = null;
                try {
                    v = md.getClass().getMethod("get", String.class).invoke(md, "reasoningContent");
                    if (v == null) v = md.getClass().getMethod("get", String.class).invoke(md, "reasoning");
                    if (v == null) v = md.getClass().getMethod("get", String.class).invoke(md, "thinking");
                } catch (Exception ignored) {
                }
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 将 {@link LocalToolResult} 序列化后的 JSON 字符串中的结构化字段合并进 artifact body，
     * 与 {@link demo.k8s.agent.tools.UnifiedToolExecutor} 顶层 {@code metadata} 对齐，供前端 Task 等组件使用。
     */
    private void enrichToolArtifactBodyFromToolOutputJson(Map<String, Object> body, String toolOutputJson) {
        if (toolOutputJson == null || toolOutputJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(toolOutputJson);
            if (root.has("metadata") && !root.get("metadata").isNull()) {
                body.put("metadata", objectMapper.convertValue(root.get("metadata"), Map.class));
            }
            if (root.has("content") && root.get("content").isTextual()) {
                body.put("content", root.get("content").asText());
            }
        } catch (Exception e) {
            log.debug("无法从 toolOutput 解析结构化字段: {}", e.getMessage());
        }
    }

    /**
     * 从响应中提取工具调用信息并调用 onToolCall 回调
     * 注意：这是在工具执行之后调用，仅用于记录目的
     */
    private void logToolCallsFromResponse(ChatResponse response, TriConsumer<String, String, JsonNode> onToolCall) {
        // Spring AI 在内部执行工具后，响应中可能包含 TOOL 角色的消息
        // 但这些消息不包含原始的工具调用参数
        // 我们只能从日志中获取工具调用信息
        log.info("检查响应中的工具调用信息 (Spring AI 已执行工具)");

        // 注意：由于 Spring AI 已经在内部执行了工具，我们无法获取原始的工具调用参数
        // 这里只是一个占位实现，用于记录工具执行的事实
    }

    /**
     * 流式输出文本（逐字符发送）
     */
    private void streamText(String text, java.util.function.Consumer<String> onTextDelta) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // 逐字符发送（模拟真正的流式输出）
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String delta = String.valueOf(c);
            onTextDelta.accept(delta);

            // 短暂延迟以模拟真实流式体验（5ms）
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 权限拒绝异常
     */
    public static class PermissionDeniedException extends RuntimeException {
        private final String toolName;

        public PermissionDeniedException(String toolName, String reason) {
            super(reason);
            this.toolName = toolName;
        }

        public String getToolName() {
            return toolName;
        }
    }
}
