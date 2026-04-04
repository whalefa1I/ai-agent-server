package demo.k8s.agent.query;

import com.fasterxml.jackson.databind.JsonNode;
import demo.k8s.agent.config.AgentPrompts;
import demo.k8s.agent.config.DemoCoordinatorProperties;
import demo.k8s.agent.config.DemoQueryProperties;
import demo.k8s.agent.memory.search.MemorySearchService;
import demo.k8s.agent.plugin.hook.HookService;
import demo.k8s.agent.observability.ModelCallMetrics;
import demo.k8s.agent.observability.SessionStats;
import demo.k8s.agent.observability.TokenCounts;
import demo.k8s.agent.observability.ToolCallMetrics;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event.ToolCalledEvent;
import demo.k8s.agent.observability.events.Event.ModelCalledEvent;
import demo.k8s.agent.observability.events.Event.ErrorEvent;
import demo.k8s.agent.observability.logging.StructuredLogger;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.observability.metrics.MetricsCollector;
import demo.k8s.agent.toolsystem.*;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 增强版 Agentic Query Loop，与 Claude Code {@code query.ts} / {@code QueryEngine.ts} 对齐。
 * <p>
 * TODO: 修复 Spring AI API 兼容性问题
 */
@Service
public class EnhancedAgenticQueryLoop {

    private static final Logger log = LoggerFactory.getLogger(EnhancedAgenticQueryLoop.class);

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

    // 权限确认回调（用于 WebSocket TUI）
    private Function<PermissionRequest, CompletableFuture<PermissionResult>> permissionCallback;

    public EnhancedAgenticQueryLoop(
            ChatModel chatModel,
            CompactionPipeline compactionPipeline,
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
            HookService hookService) {
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
    }

    /**
     * 设置权限确认回调（用于 WebSocket TUI 实时交互）
     */
    public void setPermissionCallback(Function<PermissionRequest, CompletableFuture<PermissionResult>> callback) {
        this.permissionCallback = callback;
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
     * @param onToolCall 工具调用回调（工具名，输入）
     * @param onTextDelta 文本增量回调（delta 文本）
     */
    public AgenticTurnResult runWithCallbacks(
            String userMessage,
            java.util.function.BiConsumer<String, JsonNode> onToolCall,
            java.util.function.Consumer<String> onTextDelta) {

        log.info("开始 agentic query loop: userMessage={}", truncate(userMessage, 100));

        // 记录用户输入（结构化日志）
        String sessionId = TraceContext.getSessionId() != null ? TraceContext.getSessionId() : "";
        String userId = TraceContext.getUserId() != null ? TraceContext.getUserId() : "";
        StructuredLogger.logUserInput(sessionId, userId, userMessage);

        // 准备工具列表
        List<ToolCallback> tools =
                toolRegistry.filteredCallbacks(
                        toolPermissionContext, toolFeatureFlags, mcpToolProvider.loadMcpTools());

        log.debug("加载工具数量：{}", tools.size());

        ToolCallingChatOptions options =
                ToolCallingChatOptions.builder().toolCallbacks(tools).build();

        // 构建系统提示
        String system =
                coordinatorProperties.isEnabled()
                        ? AgentPrompts.COORDINATOR_ORCHESTRATOR_ONLY
                        : AgentPrompts.DEMO_COORDINATOR_SYSTEM;

        // 注入记忆上下文（语义搜索相关记忆）
        String memoryContext = memorySearchService != null
                ? memorySearchService.getRelevantMemoriesForContext(userMessage, sessionId)
                : "";

        if (!memoryContext.isEmpty()) {
            system = system + memoryContext;
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.add(new UserMessage(userMessage));

        QueryLoopState state = QueryLoopState.initial();

        // Agentic 主循环
        while (true) {
            // 检查最大轮次
            if (state.turnCount() >= queryProperties.getMaxTurns()) {
                log.warn("达到最大轮次限制：{}", state.turnCount());
                return new AgenticTurnResult(LoopTerminalReason.MAX_TURNS, "", state);
            }

            // 更新轮次状态
            ContinuationReason cont =
                    state.turnCount() == 0 ? ContinuationReason.INITIAL : ContinuationReason.TOOL_FOLLOW_UP;
            state = state.nextTurn(cont);

            // 更新模型调用前压缩
            messages = compactionPipeline.compactBeforeModelCall(messages);
            log.debug("Compaction 后消息数：{}", messages.size());

            // 开始模型调用指标追踪
            ModelCallMetrics modelCallMetrics = sessionStats.startModelCall("unknown");

            Prompt prompt = new Prompt(messages, options);

            ChatResponse response;
            try {
                response = retryPolicy.call(() -> {
                    Instant start = Instant.now();
                    ChatResponse resp = chatModel.call(prompt);
                    Instant end = Instant.now();

                    // 提取 Token 计数
                    TokenCounts counts = extractTokenCounts(resp);
                    sessionStats.completeModelCall(modelCallMetrics, counts);

                    // 记录模型调用（结构化日志和事件）
                    long latencyMs = Duration.between(start, end).toMillis();
                    StructuredLogger.logModelResponse(sessionId, userId, "unknown",
                        (int) counts.inputTokens(), (int) counts.outputTokens(), latencyMs, resp.getResult().getOutput().getText());
                    eventBus.publish(new ModelCalledEvent(sessionId, userId, "unknown",
                        (int) counts.inputTokens(), (int) counts.outputTokens(), latencyMs));

                    return resp;
                });
            } catch (Exception e) {
                sessionStats.failModelCall(modelCallMetrics, e.getMessage());
                log.error("模型调用失败", e);

                // 记录错误事件
                eventBus.publish(new ErrorEvent(sessionId, userId, "MODEL_ERROR",
                    e.getMessage(), e.getStackTrace() != null ? e.getStackTrace()[0].toString() : ""));

                return new AgenticTurnResult(LoopTerminalReason.ERROR, "模型调用失败：" + e.getMessage(), state);
            }

            // 检查是否有工具调用
            if (!hasToolCalls(response)) {
                String text = extractAssistantText(response);
                log.info("Agentic loop 完成：turns={}, responseLength={}", state.turnCount(), text.length());

                // 如果有文本增量回调，流式发送文本
                if (onTextDelta != null && !text.isEmpty()) {
                    streamText(text, onTextDelta);
                }

                return new AgenticTurnResult(LoopTerminalReason.COMPLETED, text, state);
            }

            // 处理工具调用（带权限检查）
            List<AssistantMessage.ToolCall> executedToolCalls;
            try {
                executedToolCalls = executeToolsWithPermissionCheck(response, prompt, options, onToolCall);
            } catch (PermissionDeniedException e) {
                log.warn("工具调用被拒绝：{}", e.getMessage());
                return new AgenticTurnResult(
                        LoopTerminalReason.COMPLETED,
                        "工具调用被用户拒绝：" + e.getToolName(),
                        state);
            }

            // 简化处理：工具执行后继续循环
            log.debug("执行了 {} 个工具调用", executedToolCalls.size());
        }
    }

    /**
     * 执行工具调用并进行权限检查
     * TODO: 修复 Spring AI API 兼容性
     */
    private List<AssistantMessage.ToolCall> executeToolsWithPermissionCheck(
            ChatResponse response,
            Prompt prompt,
            ToolCallingChatOptions options,
            java.util.function.BiConsumer<String, JsonNode> onToolCall) {

        AssistantMessage output = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        if (CollectionUtils.isEmpty(toolCalls)) {
            return List.of();
        }

        List<AssistantMessage.ToolCall> approvedCalls = new ArrayList<>();

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

            // 执行 BEFORE Hook（ Hook 可以阻止工具调用）
            if (hookService != null && !hookService.beforeToolCall(sessionId, userId, tc.name(), input)) {
                log.info("Hook blocked tool call: {}", tc.name());
                continue; // 跳过此工具调用
            }

            // 权限检查
            PermissionRequest request = permissionManager.requiresPermission(tool, input, toolPermissionContext);

            if (request != null) {
                // 需要用户确认
                log.info("工具 {} 需要用户确认", tc.name());

                // 同步等待用户确认
                PermissionResult result = waitForUserConfirmation(request);

                if (result.isDenied()) {
                    throw new PermissionDeniedException(tc.name(), result.getDenyReason());
                }

                if (result.needsConfirmation()) {
                    // 超时未确认
                    throw new PermissionDeniedException(tc.name(), "等待用户确认超时");
                }
            }

            // 开始工具调用指标追踪
            ToolCallMetrics toolMetrics = sessionStats.startToolCall(
                    tc.name(),
                    truncate(tc.arguments(), 100));

            try {
                // 执行工具 - TODO: 需要替换 toolCallingManager
                String toolOutput = "Tool execution not yet implemented for " + tc.name();

                // 记录成功
                sessionStats.completeToolCall(toolMetrics, toolOutput);
                approvedCalls.add(tc);

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

                log.debug("工具调用成功：{} ({} chars)", tc.name(), toolOutput.length());

            } catch (Exception e) {
                sessionStats.failToolCall(toolMetrics, e.getMessage());
                log.error("工具调用失败：{}", tc.name(), e);

                // 记录工具调用失败
                long toolLatencyMs = toolMetrics.getLatencyMs();
                StructuredLogger.logToolCall(sessionId, userId, tc.name(),
                    parseInput(tc.arguments()).toString(), e.getMessage(), toolLatencyMs, false);
                eventBus.publish(new ToolCalledEvent(sessionId, userId, tc.name(),
                    tc.arguments(), e.getMessage(), toolLatencyMs, false));
                metricsCollector.recordToolCall(userId, tc.name(), false, toolMetrics.latency());

                // 执行 AFTER Hook（错误情况）
                if (hookService != null) {
                    hookService.afterToolCall(sessionId, userId, tc.name(), input, e.getMessage(), false, toolLatencyMs);
                }

                // 添加错误响应到消息
                approvedCalls.add(new AssistantMessage.ToolCall(
                        tc.id(),
                        tc.name(),
                        tc.arguments(),
                        "Error: " + e.getMessage()));
            }
        }

        return approvedCalls;
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
        String t = response.getResult().getOutput().getText();
        return t != null ? t : "";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
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
