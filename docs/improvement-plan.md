# minimal-k8s-agent-demo 核心改进计划

本文档针对四个高优先级问题提供详细的设计方案和实现路径。

**状态更新**: Phase 1-3 核心功能已实现，详见 [实施状态](#实施状态)。

---

## 目录

1. [工具调用用户确认对话框](#1-工具调用用户确认对话框)
2. [可观测性基础设施](#2-可观测性基础设施)
3. [有状态多轮对话管理](#3-有状态多轮对话管理)
4. [多 Agent 协作架构](#4-多-agent-协作架构)
5. [实施状态](#实施状态)

---

## 实施状态

### Phase 1: 权限管理和可观测性 (已完成)

| 模块 | 状态 | 文件 |
|------|------|------|
| 权限核心类型 | ✅ | `toolsystem/PermissionLevel.java`, `PermissionChoice.java`, `PermissionRequest.java`, `PermissionGrant.java`, `PermissionResponse.java`, `PermissionResult.java` |
| PermissionManager | ✅ | `toolsystem/PermissionManager.java` |
| 权限 HTTP 端点 | ✅ | `web/PermissionController.java` |
| Token 计数 | ✅ | `observability/TokenCounts.java`, `ModelCallMetrics.java`, `ToolCallMetrics.java` |
| SessionStats | ✅ | `observability/SessionStats.java` |
| Micrometer 集成 | ✅ | `observability/ObservabilityConfig.java`, `web/ObservabilityController.java` |
| 增强 QueryLoop | ✅ | `query/EnhancedAgenticQueryLoop.java` |

### Phase 2: 有状态多轮对话 (已完成)

| 模块 | 状态 | 文件 |
|------|------|------|
| 状态类型 | ✅ | `state/ChatMessage.java`, `FileSnapshot.java`, `Attribution.java`, `ConversationSession.java` |
| ConversationManager | ✅ | `state/ConversationManager.java` |
| Repository | ✅ | `state/ConversationRepository.java`, `FileSystemConversationRepository.java` |
| 有状态 Controller | ✅ | `web/StatefulChatController.java` |

### Phase 3: 多 Agent 架构 (已完成)

| 模块 | 状态 | 文件 |
|------|------|------|
| CoordinatorState | ✅ | `coordinator/CoordinatorState.java`, `TaskState.java` |
| WorkerAgentExecutor | ✅ | `coordinator/WorkerAgentExecutor.java` |
| AsyncSubagentExecutor | ✅ | `coordinator/AsyncSubagentExecutor.java` |
| SubagentTools | ✅ | `coordinator/SubagentTools.java` |
| 配置更新 | ✅ | `config/AgentConfiguration.java`, `DemoToolRegistryConfiguration.java` |

---

## 1. 工具调用用户确认对话框

### 1.1 问题分析

**当前状态：**
```java
// ToolPermissionMode.java - 仅有枚举定义
public enum ToolPermissionMode {
    DEFAULT,      // 走注册表 + 每工具 checkPermissions
    READ_ONLY,    // 仅允许只读工具
    BYPASS        // 开发调试用：跳过部分检查
}

// ClaudeLikeTool.java - checkPermissions 默认放行
default PermissionResult checkPermissions(String argumentsJson, ToolPermissionContext ctx) {
    return PermissionResult.allow(argumentsJson);  // ❌ 无实际检查
}
```

**缺失能力：**
- 运行时用户确认（类似 Claude Code 的 Permission Dialog）
- 工具风险等级评估（Destructive / Read-only / Network）
- "本次允许" vs "会话允许" vs "始终允许"
- 批处理模式（一次性确认多个工具调用）

### 1.2 参考架构 (Claude Code)

| 文件 | 职责 |
|------|------|
| `src/utils/permissions.ts` | 权限策略核心逻辑 |
| `src/components/permissions/PermissionDialog.tsx` | 用户确认 UI |
| `src/types/permissions.ts` | `PermissionMode`, `PermissionResult` 类型定义 |

### 1.3 设计方案

#### 1.3.1 新增类型定义

```java
// src/main/java/demo/k8s/agent/toolsystem/PermissionLevel.java
public enum PermissionLevel {
    /** 读操作：读取文件、搜索、查看配置 */
    READ_ONLY,
    
    /** 写操作：修改文件、创建/删除 */
    MODIFY_STATE,
    
    /** 破坏性操作：删除文件、覆盖、执行外部命令 */
    DESTRUCTIVE,
    
    /** 网络操作：HTTP 请求、外部 API 调用 */
    NETWORK,
    
    /** 代理操作：启动子 Agent */
    AGENT_SPAWN
}

// src/main/java/demo/k8s/agent/toolsystem/PermissionChoice.java
public enum PermissionChoice {
    ALLOW_ONCE,      // 本次允许
    ALLOW_SESSION,   // 当前会话允许（同类工具）
    ALLOW_ALWAYS,    // 始终允许（持久化）
    DENY             // 拒绝
}

// src/main/java/demo/k8s/agent/toolsystem/PermissionRequest.java
public record PermissionRequest(
    String toolName,
    String toolDescription,
    PermissionLevel level,
    String inputSummary,    // 参数摘要（非完整 JSON）
    String riskExplanation  // 风险说明
) {}

// src/main/java/demo/k8s/agent/toolsystem/PermissionGrant.java
public record PermissionGrant(
    String toolName,
    PermissionChoice choice,
    Instant grantedAt,
    Instant expiresAt     // null = 永不过期
) {}
```

#### 1.3.2 权限管理器

```java
// src/main/java/demo/k8s/agent/toolsystem/PermissionManager.java
@Service
public class PermissionManager {
    
    // 会话级授权缓存
    private final ConcurrentHashMap<String, List<PermissionGrant>> sessionGrants = new ConcurrentHashMap<>();
    
    // 持久化授权（可从文件加载）
    private final Set<String> alwaysAllowedTools = ConcurrentHashMap.newKeySet();
    
    /**
     * 检查是否需要用户确认
     * @return null = 允许执行；否则返回需要确认的请求
     */
    public PermissionRequest requiresPermission(
            ClaudeLikeTool tool, 
            JsonNode input,
            ToolPermissionContext ctx) {
        
        // 1. 检查是否始终允许
        if (alwaysAllowedTools.contains(tool.name())) {
            return null;
        }
        
        // 2. 检查会话授权
        List<PermissionGrant> grants = sessionGrants.get(tool.name());
        if (grants != null && !grants.isEmpty()) {
            PermissionGrant latest = grants.getLast();
            if (latest.expiresAt == null || latest.expiresAt.isAfter(Instant.now())) {
                return null;
            }
        }
        
        // 3. 只读工具直接放行
        if (tool.isReadOnly(input)) {
            return null;
        }
        
        // 4. 需要用户确认
        PermissionLevel level = determineLevel(tool, input);
        return new PermissionRequest(
            tool.name(),
            tool.description(),
            level,
            summarizeInput(input),
            buildRiskExplanation(level, tool)
        );
    }
    
    public void grantPermission(PermissionGrant grant) {
        sessionGrants
            .computeIfAbsent(grant.toolName(), k -> new CopyOnWriteArrayList<>())
            .add(grant);
    }
    
    private PermissionLevel determineLevel(ClaudeLikeTool tool, JsonNode input) {
        if (tool.isDestructive(input)) return PermissionLevel.DESTRUCTIVE;
        if (tool.category() == ToolCategory.EXTERNAL) return PermissionLevel.NETWORK;
        if (tool.category() == ToolCategory.AGENT) return PermissionLevel.AGENT_SPAWN;
        if (tool.isReadOnly(input)) return PermissionLevel.READ_ONLY;
        return PermissionLevel.MODIFY_STATE;
    }
}
```

#### 1.3.3 HTTP 确认端点

```java
// src/main/java/demo/k8s/agent/web/PermissionController.java
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {
    
    private final PermissionManager permissionManager;
    private final CompletableFuture<PermissionChoice> pendingConfirmation = new CompletableFuture<>();
    
    public PermissionController(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }
    
    /**
     * 返回当前需要确认的权限请求
     */
    @GetMapping("/pending")
    public PermissionRequest getPendingRequest() {
        // 实际实现需要队列管理多个 pending 请求
        return pendingRequest;
    }
    
    /**
     * 用户确认接口
     */
    @PostMapping("/respond")
    public void respond(@RequestBody PermissionResponse response) {
        pendingConfirmation.complete(response.choice());
        if (response.choice() == PermissionChoice.ALLOW_ALWAYS) {
            // 持久化授权
        } else if (response.choice() != PermissionChoice.DENY) {
            permissionManager.grantPermission(new PermissionGrant(
                response.toolName(),
                response.choice(),
                Instant.now(),
                response.sessionExpiry()
            ));
        }
    }
    
    /**
     * SSE 推送权限请求给前端
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter permissionStream() {
        // 推送权限请求到前端
    }
}
```

#### 1.3.4 集成到工具执行流程

```java
// src/main/java/demo/k8s/agent/toolsystem/ToolExecutor.java
@Service
public class ToolExecutor {
    
    private final PermissionManager permissionManager;
    private final ToolRegistry toolRegistry;
    
    public ToolExecutionResult executeWithPermission(
            String toolName, 
            String argumentsJson,
            ToolPermissionContext ctx) {
        
        ClaudeLikeTool tool = findTool(toolName);
        JsonNode input = parseInput(argumentsJson);
        
        // 检查权限
        PermissionRequest request = permissionManager.requiresPermission(tool, input, ctx);
        if (request != null) {
            // 阻塞等待用户确认（或返回 PENDING 状态）
            PermissionChoice choice = waitForUserConfirmation(request);
            if (choice == PermissionChoice.DENY) {
                return ToolExecutionResult.error("用户拒绝了工具调用：" + toolName);
            }
        }
        
        // 执行工具
        return tool.call(argumentsJson);
    }
}
```

### 1.4 前端参考实现（伪代码）

```typescript
// 对应 Claude Code 的 PermissionDialog.tsx
function PermissionDialog({ request, onRespond }) {
  const levelConfig = {
    READ_ONLY: { icon: '📖', color: 'green' },
    MODIFY_STATE: { icon: '✏️', color: 'yellow' },
    DESTRUCTIVE: { icon: '⚠️', color: 'red' },
    NETWORK: { icon: '🌐', color: 'blue' },
    AGENT_SPAWN: { icon: '🤖', color: 'purple' }
  };
  
  return (
    <Dialog>
      <Header>{levelConfig[request.level].icon} 工具调用确认</Header>
      <Body>
        <p><strong>工具:</strong> {request.toolName}</p>
        <p><strong>说明:</strong> {request.toolDescription}</p>
        <p><strong>参数:</strong> {request.inputSummary}</p>
        <Alert type={levelConfig[request.level].color}>
          {request.riskExplanation}
        </Alert>
      </Body>
      <Footer>
        <Button onClick={() => onRespond(DENY)}>拒绝</Button>
        <Button onClick={() => onRespond(ALLOW_ONCE)}>本次允许</Button>
        <Button onClick={() => onRespond(ALLOW_SESSION)}>会话允许</Button>
        <Button onClick={() => onRespond(ALLOW_ALWAYS)}>始终允许</Button>
      </Footer>
    </Dialog>
  );
}
```

---

## 2. 可观测性基础设施

### 2.1 问题分析

**当前状态：**
- 无 Token 计数
- 无工具调用追踪
- 无延迟指标
- 无会话级统计

### 2.2 参考架构 (Claude Code)

| 组件 | 职责 |
|------|------|
| `StatsContext` | 全局状态中的统计数据 |
| `src/utils/token-tracking.ts` | Token 计数逻辑 |
| `src/services/api/claude.ts` | API 调用指标采集 |

### 2.3 设计方案

#### 2.3.1 核心指标类型

```java
// src/main/java/demo/k8s/agent/observability/TokenCounts.java
public record TokenCounts(
    long inputTokens,      // 输入 tokens
    long outputTokens,     // 输出 tokens
    long cacheReadTokens,  // 缓存读取 tokens
    long cacheWriteTokens  // 缓存写入 tokens
) {
    public long total() { return inputTokens + outputTokens; }
}

// src/main/java/demo/k8s/agent/observability/ModelCallMetrics.java
public record ModelCallMetrics(
    Instant startTime,
    Instant endTime,
    Duration latency,
    TokenCounts tokenCounts,
    String model,
    boolean success,
    String errorMessage  // 失败时
) {}

// src/main/java/demo/k8s/agent/observability/ToolCallMetrics.java
public record ToolCallMetrics(
    Instant startTime,
    Instant endTime,
    Duration latency,
    String toolName,
    String toolInputSummary,
    String toolOutputSummary,
    boolean success,
    String errorMessage
) {}

// src/main/java/demo/k8s/agent/observability/SessionStats.java
@Component
@SessionScope
public class SessionStats {
    private final AtomicLong totalModelCalls = new AtomicLong(0);
    private final AtomicLong totalToolCalls = new AtomicLong(0);
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final List<ToolCallMetrics> recentToolCalls = new ConcurrentLinkedQueue<>();
    private final Instant sessionStartedAt = Instant.now();
    
    // 统计方法...
}
```

#### 2.3.2 Token 计数器（拦截器模式）

```java
// src/main/java/demo/k8s/agent/observability/TokenCountingInterceptor.java
@Component
public class TokenCountingInterceptor implements ClientInterceptor {
    
    private final SessionStats sessionStats;
    private final ObjectMapper objectMapper;
    
    public TokenCountingInterceptor(SessionStats sessionStats, ObjectMapper objectMapper) {
        this.sessionStats = sessionStats;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {
            
            @Override
            public void sendMessage(ReqT message) {
                // 估算请求 token 数
                long inputTokens = estimateTokens(message.toString());
                super.sendMessage(message);
            }
            
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onMessage(RespT message) {
                        // 解析响应中的 usage 字段
                        TokenCounts counts = extractTokenCounts(message);
                        if (counts != null) {
                            sessionStats.recordTokenUsage(counts);
                        }
                        super.onMessage(message);
                    }
                }, headers);
            }
        };
    }
}
```

#### 2.3.3 Micrometer 指标导出

```java
// src/main/java/demo/k8s/agent/observability/ObservabilityConfig.java
@Configuration
public class ObservabilityConfig {
    
    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                    Tag.of("application", "minimal-k8s-agent-demo"),
                    Tag.of("component", "agent")
                );
    }
    
    @Bean
    MeterBinder sessionStatsBinder(SessionStats sessionStats) {
        return (id, registry) -> {
            Gauge.builder("agent.session.model_calls", sessionStats, s -> s.totalModelCalls())
                .description("Total model calls in session")
                .register(registry);
            
            Gauge.builder("agent.session.input_tokens", sessionStats, s -> s.totalInputTokens())
                .description("Total input tokens")
                .register(registry);
            
            Gauge.builder("agent.session.output_tokens", sessionStats, s -> s.totalOutputTokens())
                .description("Total output tokens")
                .register(registry);
        };
    }
}
```

#### 2.3.4 工具调用追踪

```java
// src/main/java/demo/k8s/agent/observability/ToolCallTracer.java
@Component
public class ToolCallTracer {
    
    private final SessionStats sessionStats;
    private final MeterRegistry meterRegistry;
    
    public ToolCallTracer(SessionStats sessionStats, MeterRegistry meterRegistry) {
        this.sessionStats = sessionStats;
        this.meterRegistry = meterRegistry;
    }
    
    public <T> T traceToolCall(
            String toolName, 
            String input, 
            Supplier<T> execution) {
        
        Instant start = Instant.now();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            T result = execution.get();
            
            Instant end = Instant.now();
            sample.stop(Timer.builder("agent.tool.execution_time")
                .tag("tool", toolName)
                .tag("status", "success")
                .register(meterRegistry));
            
            sessionStats.recordToolCall(new ToolCallMetrics(
                start, end, Duration.between(start, end),
                toolName, summarize(input), summarize(result.toString()), true, null
            ));
            
            return result;
        } catch (Exception e) {
            Instant end = Instant.now();
            sample.stop(Timer.builder("agent.tool.execution_time")
                .tag("tool", toolName)
                .tag("status", "error")
                .register(meterRegistry));
            
            sessionStats.recordToolCall(new ToolCallMetrics(
                start, end, Duration.between(start, end),
                toolName, summarize(input), null, false, e.getMessage()
            ));
            
            throw e;
        }
    }
}
```

#### 2.3.5 统计查询端点

```java
// src/main/java/demo/k8s/agent/web/ObservabilityController.java
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {
    
    private final SessionStats sessionStats;
    private final MeterRegistry meterRegistry;
    
    @GetMapping("/stats")
    public SessionStatsResponse getStats() {
        return new SessionStatsResponse(
            sessionStats.getSessionStartedAt(),
            sessionStats.getTotalModelCalls(),
            sessionStats.getTotalToolCalls(),
            sessionStats.getTotalInputTokens(),
            sessionStats.getTotalOutputTokens(),
            sessionStats.getRecentToolCalls()
        );
    }
    
    @GetMapping("/metrics")
    public String getPrometheusMetrics() {
        // Prometheus 格式导出
        return PrometheusMeterRegistry.toPrometheus(meterRegistry);
    }
}
```

---

## 3. 有状态多轮对话管理

### 3.1 问题分析

**当前状态：**
- HTTP 无状态请求，每次对话独立
- 无消息历史管理
- 无文件快照
- Compaction 有实现但无持久化

### 3.2 参考架构 (Claude Code)

| 组件 | 职责 |
|------|------|
| `src/state/AppState.tsx` | 中央状态管理 |
| `src/state/store.ts` | Zustand store |
| `src/QueryEngine.tsx` | 对话轮次管理 |
| `src/utils/file-snapshots.ts` | 文件历史快照 |

### 3.3 设计方案

#### 3.3.1 会话存储

```java
// src/main/java/demo/k8s/agent/state/ConversationSession.java
@Component
@SessionScope
public class ConversationSession {
    
    private final String sessionId = UUID.randomUUID().toString();
    private final Instant createdAt = Instant.now();
    
    // 消息历史
    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    
    // 工具调用历史
    private final List<ToolCallRecord> toolCallHistory = new CopyOnWriteArrayList<>();
    
    // 文件快照
    private final ConcurrentHashMap<String, FileSnapshot> fileSnapshots = new ConcurrentHashMap<>();
    
    // 归因信息
    private final Map<String, Attribution> attributions = new ConcurrentHashMap<>();
    
    // 对话元数据
    private final Map<String, String> metadata = new ConcurrentHashMap<>();
    
    // Getter/Setter + 状态管理方法...
}

// src/main/java/demo/k8s/agent/state/ChatMessage.java
public record ChatMessage(
    String id,
    MessageType type,       // USER, ASSISTANT, SYSTEM, TOOL
    String content,
    Instant timestamp,
    Map<String, Object> metadata,
    List<ToolCall> toolCalls,       // Assistant 消息可能包含工具调用
    String toolResponseId           // Tool 消息关联的请求 ID
) {}

// src/main/java/demo/k8s/agent/state/FileSnapshot.java
public record FileSnapshot(
    String filePath,
    String content,
    Instant snapshotTime,
    String contentHash,
    String attributedToMessageId    // 哪个消息导致的修改
) {}
```

#### 3.3.2 会话管理器

```java
// src/main/java/demo/k8s/agent/state/ConversationManager.java
@Service
public class ConversationManager {
    
    private final ConversationSession currentSession;
    private final ConversationRepository repository;
    
    public ConversationManager(
            @SessionScope ConversationSession currentSession,
            ConversationRepository repository) {
        this.currentSession = currentSession;
        this.repository = repository;
    }
    
    /**
     * 添加用户消息并开始新回合
     */
    public TurnContext startTurn(String userInput) {
        ChatMessage userMsg = new ChatMessage(
            UUID.randomUUID().toString(),
            MessageType.USER,
            userInput,
            Instant.now(),
            Map.of(),
            null,
            null
        );
        currentSession.addMessage(userMsg);
        return new TurnContext(userMsg.getId());
    }
    
    /**
     * 完成回合，返回摘要
     */
    public TurnResult completeTurn(TurnContext ctx) {
        // 计算回合统计
        return new TurnResult(
            ctx.turnId(),
            currentSession.getMessageCount(),
            currentSession.getTokenCount()
        );
    }
    
    /**
     * 获取历史消息（支持分页）
     */
    public List<ChatMessage> getHistory(int limit) {
        List<ChatMessage> all = currentSession.getMessages();
        int start = Math.max(0, all.size() - limit);
        return all.subList(start, all.size());
    }
    
    /**
     * 创建文件快照
     */
    public void snapshotFile(String path, String content, String attributedToMessageId) {
        FileSnapshot snapshot = new FileSnapshot(
            path,
            content,
            Instant.now(),
            hash(content),
            attributedToMessageId
        );
        currentSession.getFileSnapshots().put(path, snapshot);
    }
    
    /**
     * 获取文件变更历史
     */
    public List<FileSnapshot> getFileHistory(String path) {
        // 从 repository 加载历史快照
    }
}
```

#### 3.3.3 会话持久化

```java
// src/main/java/demo/k8s/agent/state/ConversationRepository.java
public interface ConversationRepository {
    
    void saveSession(ConversationSession session);
    
    Optional<ConversationSession> loadSession(String sessionId);
    
    List<String> listSessions();
    
    void deleteSession(String sessionId);
    
    default void saveMessage(String sessionId, ChatMessage message) {
        // 增量保存消息
    }
    
    default List<FileSnapshot> getFileHistory(String sessionId, String filePath) {
        // 从存储加载文件历史
    }
}

// src/main/java/demo/k8s/agent/state/FileSystemConversationRepository.java
@Repository
public class FileSystemConversationRepository implements ConversationRepository {
    
    private final Path sessionsDir;
    private final ObjectMapper objectMapper;
    
    public FileSystemConversationRepository(
            @Value("${agent.state.dir:${user.home}/.claude/sessions}") String sessionsDir,
            ObjectMapper objectMapper) {
        this.sessionsDir = Path.of(sessionsDir);
        this.objectMapper = objectMapper;
        Files.createDirectories(this.sessionsDir);
    }
    
    @Override
    public void saveSession(ConversationSession session) {
        Path file = sessionsDir.resolve(session.getSessionId() + ".json");
        objectMapper.writeValue(file.toFile(), session);
    }
    
    @Override
    public Optional<ConversationSession> loadSession(String sessionId) {
        Path file = sessionsDir.resolve(sessionId + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(file.toFile(), ConversationSession.class));
    }
}
```

#### 3.3.4 有状态的 Chat Controller

```java
// src/main/java/demo/k8s/agent/web/StatefulChatController.java
@RestController
@RequestMapping("/api/chat")
public class StatefulChatController {
    
    private final ConversationManager conversationManager;
    private final AgenticQueryLoop queryLoop;
    
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        // 开始新回合
        TurnContext ctx = conversationManager.startTurn(request.message());
        
        try {
            // 执行 agentic loop
            AgenticTurnResult result = queryLoop.run(request.message());
            
            // 保存结果到会话
            conversationManager.saveAssistantMessage(ctx.turnId(), result);
            
            // 完成回合
            conversationManager.completeTurn(ctx);
            
            return new ChatResponse(result.content());
        } catch (Exception e) {
            conversationManager.saveError(ctx.turnId(), e);
            throw e;
        }
    }
    
    @GetMapping("/history")
    public List<ChatMessage> history(
            @RequestParam(defaultValue = "50") int limit) {
        return conversationManager.getHistory(limit);
    }
    
    @GetMapping("/files/{path:.+}/history")
    public List<FileSnapshot> fileHistory(@PathVariable String path) {
        return conversationManager.getFileHistory(path);
    }
}
```

---

## 4. 多 Agent 协作架构

### 4.1 问题分析

**当前状态：**
- `InMemoryWorkerMailbox` 仅有基础框架
- 只实现了 `SpawnPath.SYNCHRONOUS_TYPED_AGENT`
- 缺少 Coordinator 完整实现
- 无异步子 Agent 支持

### 4.2 参考架构 (Claude Code)

| 组件 | 职责 |
|------|------|
| `src/coordinator/` | Coordinator 工具实现 |
| `src/tools/AgentTool/` | 子 Agent 生成逻辑 |
| `src/query.ts` | Coordinator 模式切换 |

### 4.3 设计方案

#### 4.3.1 Coordinator 增强

```java
// src/main/java/demo/k8s/agent/coordinator/CoordinatorState.java
@Component
@SessionScope
public class CoordinatorState {
    
    // 活跃任务
    private final ConcurrentHashMap<String, TaskState> activeTasks = new ConcurrentHashMap<>();
    
    // 任务队列
    private final BlockingQueue<TaskInput> taskQueue = new LinkedBlockingQueue<>();
    
    // 任务结果邮箱
    private final ConcurrentHashMap<String, CompletableFuture<TaskResult>> taskFutures = new ConcurrentHashMap<>();
    
    /**
     * 创建新任务
     */
    public TaskHandle createTask(String name, String goal, String assignedTo) {
        String taskId = UUID.randomUUID().toString();
        TaskState state = new TaskState(taskId, name, goal, assignedTo, TaskState.PENDING);
        activeTasks.put(taskId, state);
        return new TaskHandle(taskId);
    }
    
    /**
     * 发送消息给任务
     */
    public void sendMessage(String taskId, String message) {
        TaskState task = activeTasks.get(taskId);
        if (task != null) {
            task.addMessage(message);
            // 通知 Worker
        }
    }
    
    /**
     * 停止任务
     */
    public void stopTask(String taskId) {
        TaskState task = activeTasks.get(taskId);
        if (task != null) {
            task.setState(TaskState.STOPPING);
            // 通知 Worker 停止
        }
    }
    
    /**
     * 等待任务完成
     */
    public TaskResult waitForTask(String taskId, Duration timeout) throws TimeoutException {
        CompletableFuture<TaskResult> future = taskFutures.computeIfAbsent(
            taskId, k -> new CompletableFuture<>());
        return future.get(timeout.toMillis(), MILLISECONDS);
    }
}

// src/main/java/demo/k8s/agent/coordinator/TaskState.java
public record TaskState(
    String taskId,
    String name,
    String goal,
    String assignedTo,      // "general" / "explore" / "plan" / "bash"
    TaskStatus status,      // PENDING, RUNNING, WAITING, COMPLETED, FAILED, STOPPED
    List<String> messages,  // 收件箱
    List<String> outputs,   // 输出历史
    Instant createdAt,
    Instant updatedAt
) {
    public enum TaskStatus { PENDING, RUNNING, WAITING, COMPLETED, FAILED, STOPPED }
}
```

#### 4.3.2 Worker Agent 执行器

```java
// src/main/java/demo/k8s/agent/coordinator/WorkerAgentExecutor.java
@Service
public class WorkerAgentExecutor {
    
    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final CoordinatorState coordinatorState;
    private final InMemoryWorkerMailbox mailbox;
    
    /**
     * 异步执行 Worker Agent
     */
    @Async("workerExecutor")
    public void executeWorker(String taskId, String agentType) {
        TaskState task = coordinatorState.getActiveTasks().get(taskId);
        if (task == null) {
            return;
        }
        
        // 构建 Worker 专属工具集
        List<ToolCallback> workerTools = selectToolsForAgent(agentType);
        
        // 构建 Worker 系统提示
        String systemPrompt = buildWorkerSystemPrompt(agentType, task.goal());
        
        // Worker 循环
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        
        try {
            while (task.status() == TaskStatus.RUNNING) {
                // 检查取消请求
                if (mailbox.isCancelRequested(taskId)) {
                    task.setStatus(TaskStatus.STOPPED);
                    break;
                }
                
                // 处理收件箱消息
                List<String> newMessages = mailbox.drain(taskId);
                for (String msg : newMessages) {
                    messages.add(new UserMessage(msg));
                }
                
                // 调用模型
                ChatResponse response = chatModel.call(new Prompt(messages, 
                    ToolCallingChatOptions.builder().toolCallbacks(workerTools).build()));
                
                // 处理工具调用
                if (hasToolCalls(response)) {
                    ToolExecutionResult result = executeTools(response);
                    messages = result.conversationHistory();
                    
                    // 将输出发回 Coordinator
                    for (String output : result.outputs()) {
                        task.addOutput(output);
                    }
                } else {
                    // 任务完成
                    String finalAnswer = response.getResult().getOutput().getText();
                    coordinatorState.completeTask(taskId, finalAnswer);
                    break;
                }
            }
        } catch (Exception e) {
            coordinatorState.failTask(taskId, e);
        }
    }
    
    private List<ToolCallback> selectToolsForAgent(String agentType) {
        return switch (agentType) {
            case "bash" -> toolRegistry.getBashOnlyTools();
            case "explore" -> toolRegistry.getReadonlyTools();
            case "plan" -> toolRegistry.getPlanningTools();
            default -> toolRegistry.getAllTools();
        };
    }
}
```

#### 4.3.3 异步子 Agent 支持

```java
// src/main/java/demo/k8s/agent/subagent/AsyncSubagentExecutor.java
@Service
public class AsyncSubagentExecutor {
    
    private final WorkerAgentExecutor workerExecutor;
    private final CoordinatorState coordinatorState;
    private final ExecutorService executorService;
    
    public AsyncSubagentExecutor(
            WorkerAgentExecutor workerExecutor,
            CoordinatorState coordinatorState) {
        this.workerExecutor = workerExecutor;
        this.coordinatorState = coordinatorState;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * 后台启动子 Agent，立即返回 TaskHandle
     */
    public TaskHandle spawnBackgroundAgent(
            String name, 
            String goal, 
            String agentType) {
        
        TaskHandle handle = coordinatorState.createTask(name, goal, agentType);
        
        executorService.submit(() -> {
            workerExecutor.executeWorker(handle.taskId(), agentType);
        });
        
        return handle;
    }
    
    /**
     * 等待子 Agent 完成（阻塞）
     */
    public TaskResult runSynchronousAgent(
            String name, 
            String goal, 
            String agentType,
            Duration timeout) throws TimeoutException {
        
        TaskHandle handle = spawnBackgroundAgent(name, goal, agentType);
        return coordinatorState.waitForTask(handle.taskId(), timeout);
    }
}
```

#### 4.3.4 子 Agent 工具定义

```java
// src/main/java/demo/k8s/agent/toolsystem/SubagentTools.java
@Component
public class SubagentTools {
    
    @ToolDefinition(name = "task", description = "委派任务给子 Agent")
    public TaskToolOutput createTask(
            @ToolParam(name = "name", description = "任务名称") String name,
            @ToolParam(name = "goal", description = "任务目标") String goal,
            @ToolParam(name = "subagent_type", description = "子 Agent 类型") String subagentType,
            @ToolParam(name = "run_in_background", description = "是否后台执行", required = false) boolean background) {
        
        if (background) {
            TaskHandle handle = asyncSubagentExecutor.spawnBackgroundAgent(name, goal, subagentType);
            return new TaskToolOutput(handle.taskId(), "任务已后台启动");
        } else {
            TaskResult result = asyncSubagentExecutor.runSynchronousAgent(name, goal, subagentType, Duration.ofMinutes(10));
            return new TaskToolOutput(result.taskId(), result.output());
        }
    }
    
    @ToolDefinition(name = "send_message", description = "发送消息给任务")
    public void sendMessage(
            @ToolParam(name = "task_id") String taskId,
            @ToolParam(name = "message") String message) {
        coordinatorState.sendMessage(taskId, message);
    }
    
    @ToolDefinition(name = "task_stop", description = "停止任务")
    public void stopTask(@ToolParam(name = "task_id") String taskId) {
        coordinatorState.stopTask(taskId);
    }
}
```

#### 4.3.5 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      Main Agent Loop                            │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────────┐│
│  │ Coordinator │  │ Message History│  │ Permission Manager    ││
│  │   Tools     │  │ + Snapshots  │  │ + Observability        ││
│  └──────┬──────┘  └──────────────┘  └─────────────────────────┘│
│         │                                                      │
│         ▼                                                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    Task Tool                              │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐   │  │
│  │  │ spawn task  │  │ send_message│  │  task_stop      │   │  │
│  │  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘   │  │
│  └─────────┼────────────────┼──────────────────┼────────────┘  │
└────────────┼────────────────┼──────────────────┼───────────────┘
             │                │                  │
             │                ▼                  │
             │        ┌───────────────┐          │
             │        │ Mailbox Bridge│          │
             │        │ (Message Queue)│          │
             │        └───────┬───────┘          │
             │                │                  │
             ▼                ▼                  ▼
    ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
    │ Worker Agent 1 │ │ Worker Agent 2 │ │ Worker Agent N │
    │ (general)      │ │ (explore)      │ │ (bash)         │
    │                │ │                │ │                │
    │ - Tool Set A   │ │ - Tool Set B   │ │ - Tool Set C   │
    │ - Own Loop     │ │ - Own Loop     │ │ - Own Loop     │
    └────────────────┘ └────────────────┘ └────────────────┘
```

---

## 实施路线图 (实际完成)

| 阶段 | 任务 | 状态 |
|------|------|------|
| **Phase 1.1** | 工具权限核心类型 | ✅ 完成 |
| **Phase 1.2** | PermissionManager | ✅ 完成 |
| **Phase 1.3** | 权限确认 HTTP 端点 | ✅ 完成 |
| **Phase 1.4** | Token 计数和 SessionStats | ✅ 完成 |
| **Phase 1.5** | Micrometer 指标导出 | ✅ 完成 |
| **Phase 2.1** | 增强 AgenticQueryLoop | ✅ 完成 |
| **Phase 2.2** | ConversationSession 状态管理 | ✅ 完成 |
| **Phase 2.3** | ConversationManager 和 Repository | ✅ 完成 |
| **Phase 3.1** | CoordinatorState 任务状态机 | ✅ 完成 |
| **Phase 3.2** | WorkerAgentExecutor | ✅ 完成 |
| **Phase 3.3** | AsyncSubagentExecutor | ✅ 完成 |
| **Phase 3.4** | SubagentTools 工具定义 | ✅ 完成 |

---

## 附录：与 Claude Code 的映射

| 本模块 | Claude Code 对应 |
|--------|-----------------|
| `PermissionManager` | `src/utils/permissions.ts` |
| `PermissionDialog` | `src/components/permissions/PermissionDialog.tsx` |
| `SessionStats` | `StatsContext` |
| `ConversationSession` | `src/state/AppState.tsx` |
| `ConversationManager` | `src/QueryEngine.tsx` |
| `CoordinatorState` | `src/coordinator/` |
| `WorkerAgentExecutor` | `src/tools/AgentTool/runAgent()` |
| `EnhancedAgenticQueryLoop` | `src/query.ts` + `src/QueryEngine.ts` |
