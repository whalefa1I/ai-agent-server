# 与 Claude Code Query Loop 对齐报告

## 概述

本文档说明 `minimal-k8s-agent-demo` 项目如何通过对齐 Claude Code 的 `query.ts` 核心逻辑，实现了完整的 Agentic Loop 能力。

---

## Claude Code Query Loop 架构

Claude Code 的 query loop 位于 `src/query.ts`，核心流程如下：

```typescript
export async function query(
  userMessage: string,
  options: QueryOptions
): AsyncGenerator<StreamEvent> {
  
  // 1. 准备阶段
  const systemPrompt = buildSystemPrompt()
  const messages = buildMessages(userMessage)
  const tools = getTools(permissionContext)
  
  // 2. 主循环
  let turnCount = 0
  while (true) {
    // a. 检查终止条件
    if (turnCount >= maxTurns) throw new Error('MAX_TURNS')
    
    // b. 上下文压缩
    const compactedMessages = await compactBeforeModelCall(messages)
    
    // c. 调用模型
    const response = await callModel(compactedMessages, tools)
    
    // d. 检查是否完成
    if (!hasToolCalls(response)) {
      yield { type: 'completion', text: response.text }
      break
    }
    
    // e. 执行工具（带权限检查）
    const toolResults = await executeToolsWithPermissionCheck(response)
    
    // f. 更新消息历史
    messages = [...messages, ...toolResults]
    turnCount++
  }
}
```

---

## Java 实现对齐

### EnhancedAgenticQueryLoop.java 核心流程

```java
public AgenticTurnResult run(String userMessage) {
    // 1. 准备阶段
    List<ToolCallback> tools = toolRegistry.filteredCallbacks(...);
    ToolCallingChatOptions options = ToolCallingChatOptions.builder()
        .toolCallbacks(tools).build();
    
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(system));
    messages.add(new UserMessage(userMessage));
    
    QueryLoopState state = QueryLoopState.initial();
    
    // 2. 主循环
    while (true) {
        // a. 检查终止条件
        if (state.turnCount() >= queryProperties.getMaxTurns()) {
            return new AgenticTurnResult(LoopTerminalReason.MAX_TURNS, "", state);
        }
        
        // b. 上下文压缩
        messages = compactionPipeline.compactBeforeModelCall(messages);
        
        // c. 调用模型（带 Token 追踪）
        ModelCallMetrics metrics = sessionStats.startModelCall(model);
        ChatResponse response = retryPolicy.call(() -> chatModel.call(prompt));
        sessionStats.completeModelCall(metrics, tokenCounts);
        
        // d. 检查是否完成
        if (!hasToolCalls(response)) {
            return new AgenticTurnResult(LoopTerminalReason.COMPLETED, text, state);
        }
        
        // e. 执行工具（带权限检查 + 指标追踪）
        ToolExecutionResult toolResult = 
            executeToolsWithPermissionCheck(response, prompt, options);
        
        // f. 更新消息历史
        messages = toolResult.conversationHistory();
    }
}
```

---

## 功能对齐矩阵

| 功能 | Claude Code (`query.ts`) | Java 实现 | 对齐状态 |
|------|-------------------------|-----------|----------|
| **最大轮次限制** | `maxTurns` 检查 | `DemoQueryProperties.maxTurns` | ✅ |
| **上下文压缩** | `compactBeforeModelCall()` | `CompactionPipeline.compactBeforeModelCall()` | ✅ |
| **Microcompact** | ✅ | ✅ (`DefaultCompactionPipeline.microcompact`) | ✅ |
| **Autocompact** | ✅ | ✅ (`DefaultCompactionPipeline.autocompactSummarize`) | ✅ |
| **重试策略** | `withRetry.ts` | `ModelCallRetryPolicy` | ✅ |
| **Token 追踪** | `tokenCountWithEstimation()` | `SessionStats` + `TokenCounts` | ✅ |
| **权限检查** | `checkPermissions()` | `PermissionManager.requiresPermission()` | ✅ |
| **工具执行** | `runTools()` | `toolCallingManager.executeToolCalls()` | ✅ |
| **指标采集** | `logEvent()` | `SessionStats` + Micrometer | ✅ |
| **SSE 推送** | ✅ | ✅ (`PermissionController.stream`) | ✅ |

---

## 关键差异

### 1. 流式处理

| 方面 | Claude Code | Java 实现 |
|------|-------------|-----------|
| 模型响应流式 | ✅ 原生支持 | ⚠️ 依赖 Spring AI |
| 工具执行流式 | ✅ `StreamingToolExecutor` | ❌ 批量执行 |
| 前端 SSE 推送 | ✅ | ✅ (有限功能) |

**影响**: Java 实现中工具执行期间前端会暂停接收事件，直到工具完成。

### 2. 权限确认

| 方面 | Claude Code | Java 实现 |
|------|-------------|-----------|
| UI 集成 | ✅ React 组件 | ❌ 需前端实现 |
| 批处理 | ✅ 支持 | ⚠️ 单一工具 |
| 会话授权 | ✅ | ✅ |

**影响**: 需要前端实现 PermissionDialog 组件才能完整体验。

### 3. 子 Agent 执行

| 方面 | Claude Code | Java 实现 |
|------|-------------|-----------|
| 同步模式 | ✅ `runAgent()` | ✅ `runSynchronousAgent()` |
| 异步模式 | ✅ `run_in_background` | ✅ `spawnBackgroundAgent()` |
| 提示缓存 | ✅ | ❌ 未实现 |
| Teammate | ✅ | ⚠️ 占位实现 |

---

## Compaction Pipeline 对齐

### Claude Code

```typescript
// src/services/compact/compact.ts
async function compactBeforeModelCall(messages: Message[]): Promise<Message[]> {
  // Tier 1: Microcompact (截断过长工具响应)
  const microcompacted = microcompactMessages(messages)
  
  // Tier 2: Snip (移除附件元数据)
  const snipped = snipAttachments(microcompacted)
  
  // Tier 3: AutoCompact (子 LLM 摘要)
  if (shouldAutoCompact(messages)) {
    return await autocompact(messages)
  }
  
  return microcompacted
}
```

### Java 实现

```java
// DefaultCompactionPipeline.java
@Override
public List<Message> compactBeforeModelCall(List<Message> messages) {
    // Tier 1: Microcompact
    List<Message> tier1 = microcompact(messages);
    if (!props.isFullCompactEnabled()) {
        return tier1;
    }
    
    // Tier 3: Autocompact
    int total = MessageTextEstimator.estimateChars(tier1);
    if (total < props.getFullCompactThresholdChars()) {
        return tier1;
    }
    return autocompactSummarize(tier1, total);
}
```

**对齐状态**: 核心功能已实现，Snip 附件元数据未实现。

---

## Retry Policy 对齐

### Claude Code

```typescript
// src/services/api/withRetry.ts
async function withRetry<T>(fn: () => Promise<T>): Promise<T> {
  const maxRetries = 5
  let delay = 500
  
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await fn()
    } catch (e) {
      if (!isRetriable(e) || i === maxRetries - 1) throw e
      await sleep(delay)
      delay = Math.min(32000, delay * 2)  // 指数退避
    }
  }
}
```

### Java 实现

```java
// ModelCallRetryPolicy.java
public ChatResponse call(Supplier<ChatResponse> supplier) {
    int max = props.getRetry().getMaxAttempts();
    long base = props.getRetry().getBaseDelayMs();
    
    for (int attempt = 1; attempt <= max; attempt++) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            if (attempt == max || !isRetriable(e)) {
                throw e;
            }
            long sleep = Math.min(32_000L, base * (1L << (attempt - 1)));
            Thread.sleep(sleep);
        }
    }
}
```

**对齐状态**: ✅ 完全对齐（指数退避 + 最大延迟 32 秒）

---

## Token 追踪对齐

### Claude Code

```typescript
// src/utils/tokens.ts
function tokenCountWithEstimation(messages: Message[]): number {
  const textLength = messages.reduce((sum, m) => sum + m.content.length, 0)
  // 使用 tiktoken 估算
  return encoding.encode(text).length
}
```

### Java 实现

```java
// SessionStats.java
public void completeModelCall(ModelCallMetrics metrics, TokenCounts counts) {
    totalInputTokens.addAndGet(counts.inputTokens());
    totalOutputTokens.addAndGet(counts.outputTokens());
    // ...
}

// EnhancedAgenticQueryLoop.java
private TokenCounts extractTokenCounts(ChatResponse response) {
    // 从 OpenAI usage 字段提取
    Object usageObj = metadata.get("usage");
    if (usageObj != null) {
        long inputTokens = (Long) reflectGet(usageObj, "promptTokens");
        long outputTokens = (Long) reflectGet(usageObj, "completionTokens");
        return new TokenCounts(inputTokens, outputTokens, 0, 0);
    }
    return TokenCounts.ZERO;
}
```

**对齐状态**: ✅ 已实现（但使用 API 返回值而非估算）

---

## 权限检查对齐

### Claude Code

```typescript
// src/utils/permissions.ts
async function checkPermissions(
  tool: Tool,
  input: unknown,
  ctx: PermissionContext
): Promise<PermissionResult> {
  if (tool.isReadOnly(input)) return allow()
  if (ctx.mode === 'bypass') return allow()
  
  const request = buildPermissionRequest(tool, input)
  const choice = await showPermissionDialog(request)
  
  return processPermissionChoice(choice, tool)
}
```

### Java 实现

```java
// PermissionManager.java
public PermissionRequest requiresPermission(
        ClaudeLikeTool tool,
        JsonNode input,
        ToolPermissionContext ctx) {
    
    if (ctx.mode() == ToolPermissionMode.BYPASS) return null;
    if (tool.isReadOnly(input)) return null;
    if (alwaysAllowedTools.contains(tool.name())) return null;
    
    // 检查会话授权
    List<PermissionGrant> grants = sessionGrants.get(tool.name());
    if (hasValidGrant(grants)) return null;
    
    // 需要用户确认
    return createConfirmationRequest(tool, input, null);
}
```

**对齐状态**: ✅ 核心逻辑对齐，差异在于 UI 层（需前端实现）

---

## 架构差异

### Claude Code (TypeScript + React)

```
┌─────────────────────────────────────┐
│            UI (React/Ink)           │
│  ┌─────────────────────────────┐    │
│  │   PermissionDialog.tsx      │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────┐
│          query.ts (Core Loop)       │
│  - compactBeforeModelCall()         │
│  - callModel()                      │
│  - executeToolsWithPermissionCheck()│
└─────────────────────────────────────┘
```

### Java 实现 (Spring Boot + HTTP)

```
┌─────────────────────────────────────┐
│      Frontend (React/Vue/CLI)       │
│  ┌─────────────────────────────┐    │
│  │   PermissionDialog (待实现)  │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
                  │ HTTP/REST
┌─────────────────▼───────────────────┐
│        Spring Boot Application      │
│  ┌─────────────────────────────┐    │
│  │  EnhancedAgenticQueryLoop   │    │
│  │  - compactBeforeModelCall() │    │
│  │  - callModel()              │    │
│  │  - executeToolsWithPermissionCheck() │
│  └─────────────────────────────┘    │
│                                     │
│  ┌─────────────────────────────┐    │
│  │  PermissionManager          │    │
│  │  - requiresPermission()     │    │
│  │  - handlePermissionResponse()│   │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

---

## 待完成事项

| 功能 | 优先级 | 工作量 | 说明 |
|------|--------|--------|------|
| 前端 PermissionDialog | P0 | 2 天 | React/Vue 组件实现 |
| Snip 附件元数据 | P1 | 1 天 | 压缩优化 |
| 提示缓存 (Prompt Caching) | P1 | 2 天 | Claude API cache_control |
| Teammate Agent | P2 | 3 天 | 多 Agent 终端 |
| Grafana 仪表盘 | P2 | 1 天 | Prometheus 可视化 |

---

## 结论

`minimal-k8s-agent-demo` 已成功实现对 Claude Code `query.ts` 核心逻辑的对齐：

1. **Agentic Loop**: ✅ 完整实现（轮次控制 + 压缩 + 重试）
2. **权限管理**: ✅ 核心逻辑已实现（需前端 UI）
3. **可观测性**: ✅ Token 追踪 + 指标采集
4. **多 Agent**: ✅ Coordinator/Worker 架构

主要差异在于：
- 流式处理能力（Spring AI 限制）
- 前端 UI 集成（需单独实现）
- 提示缓存（需 API 支持）

这些差异不影响核心功能的正确使用。
