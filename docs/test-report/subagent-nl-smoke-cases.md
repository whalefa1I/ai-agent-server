# 子 Agent 自然语言冒烟与边界说明（无前端）

## 1. 场景矩阵：工程保证 vs 模型自主

| 场景 | 含义 | 谁决定 | 本仓库行为（v1） |
|------|------|--------|------------------|
| A | 仅主 Agent 使用 TaskCreate（内存或 Facade），**不派生子 Agent** | **模型** 是否调用 TaskCreate；**工程** `demo.multi-agent.mode=off` 时 TaskCreate 只走内存 `TaskTools`，绝不 spawn | `mode=off`：无 `SubagentRun`；`mode=on`：模型若调用 TaskCreate 则 `TaskCreateMultiAgentRouter` → `MultiAgentFacade` |
| B | 主 Agent **不**建 Task，但对话里仍有「子任务」由**主 Agent自己用其它工具**完成 | **模型** | 无子 Agent；无 `SubagentRun` |
| C | 主 Agent 触发 **子 Agent**（spawn），子 Worker **内不再暴露 Task\*** | **工程** 默认 | `WorkerAgentExecutor` 在 `demo.multi-agent.worker-expose-task-tools=false`（默认）时从 Worker 工具集**移除** `TaskCreate`/`TaskList`/…，避免「子内再嵌 Task」与主会话 Task 混淆 |
| D | 主 Agent 不调用 TaskCreate，但通过 **其它路径** spawn（若未来暴露） | **工程** 门控 + **模型** goal | 当前对外主路径是 TaskCreate→Facade；`SpawnGatekeeper` 管深度/并发/白名单 |
| E | 递归再 spawn 子子 Agent | **工程** | `max-spawn-depth`、`max-concurrent-spawns`；达到上限 **拒绝**（结构化 `MustDoNext`），不是纯模型自律 |

**结论**：  
- **派不派子 Agent、主对话里写不写 Task**：强依赖**模型是否选工具**；  
- **派生之后的安全与边界**（深度、并发、TTL、子 Worker 是否带 Task 工具）：应由**工程**约束；本仓库已用门控 + **默认去掉 Worker 的 Task\*** 体现这一点。  
- 若业务要求「子 Agent 也能建 Task」，可设 `DEMO_MULTI_AGENT_WORKER_TASK_TOOLS=true`（仍受门控深度等限制）。

## 2. TraceContext / 日志完整性（已加固）

| 环节 | 说明 |
|------|------|
| 主 Agent HTTP | `POST /api/v2/chat` 在 `HttpApiV2Controller` 内 `applyChatTraceContext`，绑定 `sessionId` / `tenantId` / `appId` |
| 主 Agent 工具回调 | `DemoToolRegistryConfiguration` 中 `[TOOL CALLBACK]`，若 `sessionId` 空会生成 `anon-session-*` |
| 子 Agent Worker 线程 | `LocalSubAgentRuntime` 在调用 `runSynchronousAgent` **前**绑定 `SpawnRequest` 维度；`AsyncSubagentExecutor` 将 `TraceContext.getTraceInfo()` **快照**到执行 `executeWorker` 的线程，并在该线程 `finally` 中 `clear` |
| Worker 执行方式 | `executeWorker` **不再** `@Async`，与线程池线程同一链路传递 ThreadLocal，避免工具日志会话错乱 |

建议在日志中检索：  
`[LocalRuntime] Worker TraceContext`、`[TOOL CALLBACK]`、`[TaskCreateRouter]`、`[Facade]`、`[WorkerAgent] 已移除 * 个 Task* 工具`。

## 3. 接口完整性（无前端）

| 能力 | 接口 |
|------|------|
| 主对话 + 工具（含 TaskCreate） | `POST /api/v2/chat`，body：`{ "sessionId", "message" }` |
| 健康检查 | `GET /api/health` |
| 子 run 状态（需会话与实现） | `MultiAgentFacade.getStatus` 走运行时；HTTP 若未单独暴露则以当前 `Ws`/内部 API 为准 |

## 4. 前置条件与脚本

- `DEMO_MULTI_AGENT_ENABLED=true`，`DEMO_MULTI_AGENT_MODE=on`
- 有效模型 Key（如 `DASHSCOPE_API_KEY`）

```powershell
.\scripts\subagent-nl-smoke.ps1
```

说明：是否出现「主 Agent 调 TaskCreate → spawn」仍依赖**模型**，工程侧保证一旦 spawn，上下文与 Worker 工具策略符合上表。
