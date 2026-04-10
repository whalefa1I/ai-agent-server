package demo.k8s.agent.tools.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 流式工具执行器，参考 Claude Code 的 {@code StreamingToolExecutor.ts} 实现。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>工具到达即执行（不等所有工具都解析完）</li>
 *   <li>并发安全的工具可以并行执行</li>
 *   <li>非并发工具独占执行（exclusive access）</li>
 *   <li>支持错误级联（Bash 工具错误会取消兄弟工具）</li>
 * </ul>
 * <p>
 * 与 Spring AI 自动工具调用的区别：
 * - Spring AI {@code ToolCallingManager} 默认串行执行
 * - 本执行器手动控制并发，最大化并行效率
 */
public class StreamingToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(StreamingToolExecutor.class);

    /**
     * 最大并发工具执行数（参考 Claude Code 的 {@code getMaxToolUseConcurrency()}）
     */
    private static final int MAX_CONCURRENCY = 10;

    private final List<TrackedTool> tools = new ArrayList<>();
    private final ToolPermissionContext toolPermissionContext;
    private final LocalToolExecutor localToolExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 是否有工具执行出错
     */
    private final AtomicBoolean hasErrored = new AtomicBoolean(false);

    /**
     * 出错工具的描述（用于错误级联）
     */
    private volatile String erroredToolDescription = "";

    /**
     * 是否被丢弃（用于 streaming fallback 场景）
     */
    private final AtomicBoolean discarded = new AtomicBoolean(false);

    /**
     * 工具状态枚举
     */
    public enum ToolStatus {
        QUEUED,
        EXECUTING,
        COMPLETED,
        YIELDED
    }

    /**
     * 被追踪的工具
     */
    public static class TrackedTool {
        public final String id;
        public final String toolName;
        public final JsonNode input;
        public final ClaudeLikeTool toolDefinition;
        public ToolStatus status;
        public final boolean isConcurrencySafe;
        public CompletableFuture<LocalToolResult> future;
        public LocalToolResult result;
        public final long queuedAt;

        // TraceContext 快照（用于异步执行时传播）
        public final String traceId;
        public final String spanId;
        public final String requestId;
        public final String sessionId;
        public final String userId;
        public final String tenantId;
        public final String appId;

        public TrackedTool(String id, String toolName, JsonNode input,
                          ClaudeLikeTool toolDefinition, boolean isConcurrencySafe) {
            this.id = id;
            this.toolName = toolName;
            this.input = input;
            this.toolDefinition = toolDefinition;
            this.isConcurrencySafe = isConcurrencySafe;
            this.status = ToolStatus.QUEUED;
            this.queuedAt = System.currentTimeMillis();

            // 捕获当前 TraceContext 快照
            this.traceId = TraceContext.getTraceId();
            this.spanId = TraceContext.getSpanId();
            this.requestId = TraceContext.getRequestId();
            this.sessionId = TraceContext.getSessionId();
            this.userId = TraceContext.getUserId();
            this.tenantId = TraceContext.getTenantId();
            this.appId = TraceContext.getAppId();
        }

        public String getDescription() {
            // 提取工具的关键参数用于日志
            if (input.has("command")) {
                return toolName + "(" + truncate(input.get("command").asText(), 40) + ")";
            }
            if (input.has("file_path")) {
                return toolName + "(" + truncate(input.get("file_path").asText(), 40) + ")";
            }
            if (input.has("pattern")) {
                return toolName + "(" + truncate(input.get("pattern").asText(), 40) + ")";
            }
            return toolName;
        }

        private String truncate(String s, int maxLen) {
            if (s == null || s.length() <= maxLen) return s;
            return s.substring(0, maxLen) + "...";
        }
    }

    /**
     * 工具执行结果（带工具 ID）
     */
    public static class ToolResultWithId {
        public final String toolUseId;
        public final LocalToolResult result;

        public ToolResultWithId(String toolUseId, LocalToolResult result) {
            this.toolUseId = toolUseId;
            this.result = result;
        }
    }

    public StreamingToolExecutor(LocalToolExecutor localToolExecutor,
                                  ToolPermissionContext toolPermissionContext) {
        this.localToolExecutor = localToolExecutor;
        this.toolPermissionContext = toolPermissionContext;
    }

    /**
     * 添加工具到执行队列，会立即开始执行（如果并发条件允许）
     */
    public void addTool(String toolUseId, String toolName, JsonNode input,
                        ClaudeLikeTool toolDefinition) {
        log.debug("[StreamingExecutor] addTool: id={}, name={}", toolUseId, toolName);

        boolean isConcurrencySafe = isToolConcurrencySafe(toolDefinition, input);

        TrackedTool trackedTool = new TrackedTool(
            toolUseId, toolName, input, toolDefinition, isConcurrencySafe
        );

        synchronized (tools) {
            tools.add(trackedTool);
        }

        // 异步处理队列
        processQueueAsync();
    }

    /**
     * 判断工具是否并发安全
     */
    private boolean isToolConcurrencySafe(ClaudeLikeTool tool, JsonNode input) {
        try {
            return tool.isConcurrencySafe(input);
        } catch (Exception e) {
            log.warn("[StreamingExecutor] isConcurrencySafe 检查失败，视为不安全：{} - {}",
                    tool.name(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查当前是否可以执行新工具
     */
    private boolean canExecuteTool(boolean isConcurrencySafe) {
        List<TrackedTool> executing = getExecutingTools();

        if (executing.isEmpty()) {
            return true;
        }

        // 如果所有执行中的工具都是并发安全的，且新工具也是并发安全的，则可以执行
        if (isConcurrencySafe) {
            return executing.stream().allMatch(t -> t.isConcurrencySafe);
        }

        // 非并发工具必须独占执行
        return false;
    }

    private List<TrackedTool> getExecutingTools() {
        synchronized (tools) {
            return tools.stream()
                    .filter(t -> t.status == ToolStatus.EXECUTING)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 异步处理队列
     */
    private void processQueueAsync() {
        CompletableFuture.runAsync(this::processQueue);
    }

    /**
     * 处理队列，按并发条件启动工具
     */
    private void processQueue() {
        while (true) {
            TrackedTool toolToExecute = null;

            synchronized (tools) {
                for (TrackedTool tool : tools) {
                    if (tool.status != ToolStatus.QUEUED) {
                        continue;
                    }

                    if (canExecuteTool(tool.isConcurrencySafe)) {
                        toolToExecute = tool;
                        tool.status = ToolStatus.EXECUTING;
                        break;
                    }

                    // 如果当前工具不是并发安全的，且前面有工具在等待，则停止
                    // 这保证了非并发工具的顺序执行
                    if (!tool.isConcurrencySafe) {
                        break;
                    }
                }
            }

            if (toolToExecute == null) {
                // 没有可执行的工具，退出循环
                break;
            }

            // 异步执行工具
            executeTool(toolToExecute);
        }
    }

    /**
     * 执行单个工具
     */
    private void executeTool(TrackedTool tool) {
        log.info("[StreamingExecutor] executeTool: id={}, name={}, concurrencySafe={}",
                tool.id, tool.toolName, tool.isConcurrencySafe);

        tool.future = CompletableFuture.supplyAsync(() -> {
            // 恢复 TraceContext 快照（从调用线程传播到执行线程）
            String previousTraceId = TraceContext.getTraceId();
            String previousSpanId = TraceContext.getSpanId();
            String previousRequestId = TraceContext.getRequestId();
            String previousSessionId = TraceContext.getSessionId();
            String previousUserId = TraceContext.getUserId();
            String previousTenantId = TraceContext.getTenantId();
            String previousAppId = TraceContext.getAppId();

            try {
                // 设置捕获的上下文
                if (tool.traceId != null) {
                    TraceContext.init(tool.traceId, tool.spanId, tool.tenantId, tool.appId, tool.sessionId, tool.userId);
                    TraceContext.setRequestId(tool.requestId);
                } else if (tool.sessionId != null) {
                    // 降级：只有 sessionId 可用
                    TraceContext.setSessionId(tool.sessionId);
                    TraceContext.setUserId(tool.userId);
                }

                // 检查是否已被中止（由于兄弟工具错误）
                if (shouldCancelTool(tool)) {
                    log.warn("[StreamingExecutor] 工具被取消：{} - 原因：{}",
                            tool.id, getCancelReason(tool));
                    return LocalToolResult.error("Cancelled: " + getCancelReason(tool));
                }

                try {
                    // 执行工具
                    LocalToolResult result = localToolExecutor.execute(
                            tool.toolDefinition,
                            objectMapper.convertValue(tool.input, Map.class),
                            toolPermissionContext
                    );

                    // 如果工具执行失败且是 Bash 工具，触发错误级联
                    if (!result.isSuccess() && "bash".equals(tool.toolName)) {
                        hasErrored.set(true);
                        erroredToolDescription = tool.getDescription();
                        log.error("[StreamingExecutor] Bash 工具失败，将取消兄弟工具：{}",
                                result.getError());
                    }

                    return result;

                } catch (Exception e) {
                    log.error("[StreamingExecutor] 工具执行异常：{} - {}",
                            tool.toolName, e.getMessage(), e);
                    hasErrored.set(true);
                    erroredToolDescription = tool.getDescription();
                    return LocalToolResult.error("Execution error: " + e.getMessage());
                }
            } finally {
                // 恢复之前的上下文（如果有）
                if (previousTraceId != null || previousSessionId != null) {
                    TraceContext.init(previousTraceId, previousSpanId, previousTenantId, previousAppId, previousSessionId, previousUserId);
                    if (previousRequestId != null) {
                        TraceContext.setRequestId(previousRequestId);
                    }
                } else {
                    TraceContext.clear();
                }
            }
        });

        // 完成后更新状态并继续处理队列
        tool.future.whenComplete((result, throwable) -> {
            tool.result = result;
            tool.status = ToolStatus.COMPLETED;

            log.info("[StreamingExecutor] 工具完成：id={}, success={}, duration={}",
                    tool.id, result.isSuccess(), result.getDurationMs());

            // 继续处理队列
            processQueueAsync();
        });
    }

    /**
     * 检查工具是否应该被取消
     */
    private boolean shouldCancelTool(TrackedTool tool) {
        if (discarded.get()) {
            return true;
        }

        // 如果有 Bash 工具出错，且当前工具不是出错的那个，则取消
        if (hasErrored.get() && !erroredToolDescription.equals(tool.getDescription())) {
            return true;
        }

        return false;
    }

    /**
     * 获取取消原因
     */
    private String getCancelReason(TrackedTool tool) {
        if (discarded.get()) {
            return "streaming fallback - execution discarded";
        }
        if (hasErrored.get()) {
            return "sibling error: " + erroredToolDescription + " failed";
        }
        return "unknown";
    }

    /**
     * 丢弃所有待处理和进行中的工具（用于 streaming fallback 场景）
     */
    public void discard() {
        discarded.set(true);
        log.info("[StreamingExecutor] discard() called - discarding all pending tools");
    }

    /**
     * 获取已完成但未被消费的工具结果
     */
    public List<ToolResultWithId> getCompletedResults() {
        List<ToolResultWithId> results = new ArrayList<>();

        synchronized (tools) {
            for (TrackedTool tool : tools) {
                if (tool.status == ToolStatus.YIELDED) {
                    continue;
                }

                if (tool.status == ToolStatus.COMPLETED && tool.result != null) {
                    tool.status = ToolStatus.YIELDED;
                    results.add(new ToolResultWithId(tool.id, tool.result));
                } else if (tool.status == ToolStatus.EXECUTING && !tool.isConcurrencySafe) {
                    // 非并发工具执行中时，后面的结果暂不返回（保持顺序）
                    break;
                }
            }
        }

        return results;
    }

    /**
     * 等待所有工具完成并返回结果
     */
    public List<ToolResultWithId> getAllResults() {
        log.info("[StreamingExecutor] waiting for all tools to complete...");

        // 等待所有 future 完成
        List<CompletableFuture<TrackedTool>> futures = new ArrayList<>();
        synchronized (tools) {
            for (TrackedTool tool : tools) {
                if (tool.future != null) {
                    futures.add(tool.future.thenApply(r -> tool));
                }
            }
        }

        // 等待所有工具完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 收集所有结果
        List<ToolResultWithId> results = new ArrayList<>();
        synchronized (tools) {
            for (TrackedTool tool : tools) {
                if (tool.result != null) {
                    results.add(new ToolResultWithId(tool.id, tool.result));
                }
            }
        }

        log.info("[StreamingExecutor] 所有工具完成：total={}, results={}",
                tools.size(), results.size());

        return results;
    }

    /**
     * 获取所有工具结果（按原始顺序）
     */
    public List<ToolResultWithId> getResultsInOrder() {
        // 先等待所有工具完成
        getAllResults();

        // 按原始顺序返回
        synchronized (tools) {
            return tools.stream()
                    .filter(t -> t.result != null)
                    .map(t -> new ToolResultWithId(t.id, t.result))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 是否有未完成/未消费的工具
     */
    public boolean hasUnfinishedTools() {
        synchronized (tools) {
            return tools.stream().anyMatch(t -> t.status != ToolStatus.YIELDED);
        }
    }

    /**
     * 获取当前执行中的工具数量
     */
    public int getExecutingCount() {
        return getExecutingTools().size();
    }

    /**
     * 获取队列中等待的工具数量
     */
    public int getQueuedCount() {
        synchronized (tools) {
            return (int) tools.stream().filter(t -> t.status == ToolStatus.QUEUED).count();
        }
    }

    /**
     * 获取工具总数
     */
    public int getTotalCount() {
        synchronized (tools) {
            return tools.size();
        }
    }

    /**
     * 是否有工具正在执行
     */
    public boolean isExecuting() {
        return getExecutingCount() > 0;
    }
}
