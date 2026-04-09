# 分布式子 Agent 与 ContextObjectStore 架构设计

本文档汇总多 Agent / 子系统编排与「外脑记忆区」（超长工具结果外置 + 指针回读）的企业级设计，与 `ai-agent-server` 实现对齐，便于评审与迭代。

---

## 1. 目标

- **控制力**：代码硬门控（深度、并发、权限、TTL）兜底。
- **灵活性**：提示词策略驱动意图分发，但不替代硬门控。
- **可解耦**：核心单 Agent 循环与多 Agent 能力通过 **Facade + 开关** 隔离；关开关时无残留行为。
- **可恢复**：状态可 Hydrate，避免 Pod 重启后的幽灵事件与悬空指针。
- **可观测**：Trace/Span 贯穿；多 Agent / ContextBus 路径上补充 **黄金指标**（见 §6.1），拒绝仅靠控制台日志。

### 1.1 与双栈战略文档（v2）的关系

| 文档 | 角色 |
|------|------|
| **本文（v1）** | 与当前 **Java 单体 `ai-agent-server`** 对齐的可执行设计：开关、ContextBus、门控、指标、实现清单与 Backlog。 |
| [`distributed-subagent-and-context-object-design-v2.md`](./distributed-subagent-and-context-object-design-v2.md) | **战略 ADR**：控制面（Java）与 AI 执行面（Python）分离、事件流、联邦工具与阶段路线图。 |

**衔接方式**：v1 中的 `MultiAgentFacade`、`ContextObjectStore`（DB 权威）、Gatekeeper 与租户/会话模型，构成 v2 **Phase 1（基础设施）** 在单体内的落地；后续引入 Python 数据面时，应视为 **替换子 Agent 运行时与编排实现**，控制面继续承担网关、鉴权、配额与 `ContextObjectStore`，避免双栈上线时重做安全与状态模型。

> **命名区分**：下文及 v2 中的「双栈 / Python 数据面」与 **§8** 小节标题里的「V2 规划（合规：脱敏 / 加密）」是不同主题；后者指数据合规演进，勿与双栈 ADR 混淆。

### 1.2 为后期 v2 平滑迁移须遵守的工程边界

按 v1 在单体内构建子 Agent 时，下列约束**不是可选优化**，而是后期把子 Agent **独立成进程 / 交给 Python 执行面**时的迁移前提；违反则易沦为「拆不动」的耦合实现。

| 边界 | 要求 |
|------|------|
| **编排入口** | 上游对话入口与多 Agent 能力之间仅通过 **`MultiAgentFacade`（或等价门面）** 切换；禁止业务代码直接依赖具体 Orchestrator / spawn 实现类。 |
| **契约先行** | 在首版实现中就固定 **spawn 请求 / 子 run 状态机 / 终态事件** 的语义与 DTO（含超时 `TaskCompleted(TIMEOUT)`、门控拒绝的结构化载荷），即使首版仅在进程内调用；远程化时只换传输层，不改语义。 |
| **状态外置** | 子任务可恢复信息以 **DB 持久化 run 记录** 等为权威；JVM 堆内仅存可丢弃的句柄或缓存。与 §6、`ContextObjectStore` 原则一致。 |
| **门控集中** | 深度、并发、Wall-Clock TTL、权限子集、sandbox 规则在 **Gatekeeper（或单一策略组件）** 统一执行；禁止散落在各工具或 prompt 分支中「顺手判断」。 |
| **工具与权限** | 工具经 **Registry** 注册；暴露给子 Agent 的工具集合为 **硬过滤** 结果（见 §7），与执行进程无关，便于将来在远端重复同一裁剪逻辑。 |
| **可观测** | `trace_id` / `session_id` / `run_id` 及 §6.1 黄金指标在多 Agent 路径上可追溯；独立服务后仍依赖同一套下钻能力。 |
| **运行时抽象** | 子 Agent 执行侧宜以 **`SubAgentRuntime`（命名示例）** 抽象封装：首版可为 `LocalSubAgentRuntime`（同进程）；后期增加 `RemoteSubAgentRuntime`（gRPC/HTTP 事件流）时，Facade 与门控保持稳定。 |

**反模式（将导致后期难以平滑迁移）**：主循环内联子任务逻辑；子任务状态仅内存；仅靠自然语言错误串；无 Facade 总开关；工具列表写死在 Agent 类中。

### 1.3 建议的演进顺序（与 v2 路线图对齐）

1. **当前阶段**：完成本文 §9～§10 Backlog（含 `ContextObject` 写入路径、`demo.multi-agent.*`、Facade、spawn 与持久化 run），在 **单进程** 内跑通端到端与 Shadow 策略。
2. **双实现阶段**：在保持 Facade 与契约不变的前提下，引入 **Local / Remote** 两种 `SubAgentRuntime` 实现之一为占位，用配置做 **灰度或按租户切流**。
3. **事件化阶段**：将同步调用逐步替换为 **与 v2 §3 对齐的流式事件**（可先 HTTP/SSE，再演进到 gRPC），终态与取消语义与 §4、§6 保持一致。
4. **独立部署阶段**：子 Agent 服务（可为 Python 数据面）独立扩缩容；Java 控制面保留网关、三维身份、`ContextObjectStore` CRUD 与越权拦截，与 v2 控制面职责对齐。

---

## 2. 总开关与模式

### 2.1 多 Agent 子系统（规划）

| 配置（规划名） | 含义 |
|----------------|------|
| `demo.multi-agent.enabled` | 总闸：关闭时不装配子系统 Bean、不注册 spawn 类工具 |
| `demo.multi-agent.mode` | `off` / `shadow` / `on` |

**Shadow 模式（关键约束）**

- **禁止**向处理用户请求的主模型注入「伪造成功」的 spawn 结果（否则污染线上对话、引发幻觉）。
- 推荐 **方案 B**：若仍暴露工具，则 Gatekeeper 返回强语义拦截，明确「不得等待子结果、仅基于当前已知信息作答」。
- 可选 **方案 A（旁路）**：独立影子流水线仅统计 metrics，与主链路隔离。

### 2.2 ContextObjectStore（已实现）

| 配置 | 说明 |
|------|------|
| `demo.context-object.enabled` | 是否允许 `read_context_object` 从数据库按 id 读正文；默认 `false` |
| `demo.context-object.default-tenant-id` | 单租户默认租户 id；多租户时应扩展 TraceContext / Resolver |
| `demo.context-object.read-max-chars` | 单次返回字符硬顶 |
| `demo.context-object.read-max-token-estimate` | 与行内 `token_estimate` 比较，超限拒绝全文一次读出 |

数据库表 `context_object` 由 **Flyway** 始终创建；开关只控制**业务是否允许读**，避免「有表无能力」与「有能力无表」混用。

---

## 3. 分层架构

```
Core Chat Loop（单 Agent + 工具 + compaction）
        │
        ▼
MultiAgentFacade（enabled → Orchestrator；disabled → NoOp）
        │
   ┌────┴────┬────────────┬──────────────┐
   ▼         ▼            ▼              ▼
Gatekeeper  ActorRuntime ContextBus    Tracing
(并发/深度/ (子 run 生命 (存根/摘要/   (trace_id/
 TTL/权限)   周期)        收件箱)       span_id)
```

- **ContextBus（DB-first）**：超长 `tool_result` 写入 `context_object`，主上下文仅保留 `[ctx-obj-…]` 存根；`read_context_object` 按 **id + 隐式 conversation_id + tenant_id** 鉴权读取。

---

## 4. 触发与门控

- **Spawn 前**：`maxSpawnDepth`、`maxChildrenPerAgent`、权限子集、sandbox 规则。
- **Spawn 规格**：强制 **Wall-Clock TTL**（`spawn(spec, deadline)`），超时 → `TaskCompleted(TIMEOUT)`，驱动主流程推进。
- **拒绝时**：工具返回 **结构化语义**（`must_do_next`），禁止裸 Error 导致模型重试死循环。

---

## 5. 上下文与防幻觉

- **指针非悬空**：删除/过期后返回明确 SYSTEM 文案，禁止模型编造 id 对应内容。
- **洋葱模型**：内层永驻目标与约束；中层 Task 树与 Summary；外层 tool 细节默认不回流主会话。
- **分片读取**：`read_context_object` 支持 `offset`/`limit`（字符级），避免单次拉回超大正文。

### 5.1 降级：ContextBus 写入失败

超长 `tool_result` 外置写入 `context_object` 时，若数据库抖动或写入失败，**不得**让核心对话链路整体失败。

- **策略**：回退为对**原文**做极限物理截断（例如保留前 N 字符 + 尾部 M 字符，N/M 可配置），并在正文前附带明确标记，例如：`[SYSTEM: 外置存储失败，以下为强行截断片段；完整内容未持久化]`。
- **目标**：主会话继续可用，模型仍能获得「部分真实片段」，而非虚构的存根 id；同时打点监控（见 §6.1）便于 SRE 发现 DB 异常。

---

## 6. 分布式与高可用

- **ActorRuntime 状态**：内存句柄 + **持久化 run 记录**；启动 `reconcile(sessionId)` 恢复未完成子任务。
- **事件投递**：至少一次 + `run_id` 幂等去重。
- **ContextObjectStore**：**不以 JVM 堆为唯一存储**；DB 为权威；未来可加 Redis 作 L1 缓存（miss 回源 DB）。

### 6.1 可观测性：建议的黄金指标（Grafana / 大盘）

除 Trace/Span 外，建议在多 Agent 与 ContextBus 路径上至少暴露以下指标，供 SRE 做告警与容量规划：

| 指标（命名示例） | 含义与用途 |
|------------------|------------|
| `subagent.spawn.rejected.rate` | 门控拦截率（深度、并发、权限、配额等）。若持续偏高，优先检查提示词拆解策略与 `maxSpawnDepth` 等配置是否不匹配。 |
| `subagent.wallclock.timeout.count` | 子任务 Wall-Clock TTL 触发强杀次数。偏高说明任务拆解过大或下游过慢。 |
| `context_object.write.failure.count` | 外置写入失败次数（与 §5.1 降级联动）。偏高应对齐 DB 健康与连接池。 |
| `context_object.read.bytes` / `context_object.read.tokens`（或估算） | 单次/累计读回流量。异常飙升可提示模型陷入「反复读取巨型日志」的循环，需配合 limit/分页策略。 |

日志中继续携带 `trace_id` / `session_id` / `run_id`，便于从指标下钻到单次请求。

---

## 7. 权限与工具白名单

- **禁止仅依赖 prompt**：子 Agent 的 `tools` 数组必须为 `Global_Safe_Tools ∩ Requested_Subset` 的硬过滤。
- **租户与会话**：读存根时 **不信任模型传入的 conversation_id**，使用 `TraceContext.getSessionId()` 与配置的 `tenant_id` 联合查询。

---

## 8. 数据合规与隐私（预留）

`context_object.content` 可能包含超长日志、代码、工具输出，**易夹杂 PII、密钥或商业机密**。

- **V1（当前）**：明文落库；依赖访问控制（会话/租户绑定）、最小权限与定期清理（`expires_at`）。
- **合规侧 V2 规划（择一或组合）**（与 §1.1 所述双栈 ADR 文件名中的「v2」无关，指合规能力迭代）：
  - 写入前经 **PII 脱敏 Pipeline**（规则 + 可选模型辅助检测，需审计误杀率）；
  - 或对 `content` 在存储层使用 **透明加密（TDE）** / 应用层信封加密，以满足企业审计与合规要求。

安全评审应明确：**谁可访问、保留多久、如何删除与导出**（与 GDPR 等场景对齐）。

---

## 9. 实现清单（代码库）

| 项 | 位置 |
|----|------|
| Flyway `context_object` 表 | `src/main/resources/db/migration/V9__context_object.sql` |
| 实体 / 仓库 | `demo.k8s.agent.contextobject.ContextObject`, `ContextObjectRepository` |
| 读取服务 | `ContextObjectReadService` |
| 工具 | `ReadContextObjectTool`；`LocalToolExecutor` 分支 `read_context_object` |
| 注册 | `DemoToolSpecs.readContextObject()`，`DemoToolRegistryConfiguration` |
| 配置 | `DemoContextObjectProperties`，`application.yml` → `demo.context-object` |

---

## 10. 后续 Backlog（未在最小补丁中实现）

- 写入路径：compaction / 工具层自动写入 `context_object` 并注入存根（与 §5.1 降级策略一起实现）。
- 定时清理：`DELETE FROM context_object WHERE expires_at < NOW()`。
- `TraceContext` 扩展 `tenantId`（多租户）。
- MultiAgentFacade、`demo.multi-agent.*` 与 spawn 工具完整接入。
- **Human-in-the-Loop / 挂起（Suspend）**：当子 Agent 或主会话请求**高危工具**（如真实支付、生产库写、破坏性运维命令）时，将状态机切入 `Suspended`，通过 Webhook/WebSocket 向前端推送**审批卡片**；用户 Approve/Reject 并可选附注后，再 **Hydrate** 上下文并恢复执行。与「权限降级」「工具白名单」并列，作为企业场景下的必经演进，V1 可不实现但应在架构上预留扩展点。

---

## 11. 参考

- 双栈演进与平台化路线图：[`distributed-subagent-and-context-object-design-v2.md`](./distributed-subagent-and-context-object-design-v2.md)（与本文 §1.1～§1.3 对照阅读）。
- 对话设计讨论：混合架构、Shadow 修正、TTL、reconcile、工具白名单。
- 对标思路：OpenClaw（硬门控 + 会话修复）、Claude Code（tool result budget + replacement state）。
