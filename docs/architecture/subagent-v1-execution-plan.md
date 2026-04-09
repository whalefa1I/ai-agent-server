# 子 Agent（v1）开发执行计划（对齐平滑迁移 v2）

本文是 `distributed-subagent-and-context-object-design.md` 的落地执行清单，目标是在当前 Java 单体内完成 v1，并为后续向 `distributed-subagent-and-context-object-design-v2.md` 的双栈演进预留平滑迁移路径。

---

## 1. 范围与目标

- 在当前仓库内完成 v1 的核心能力：`ContextObject` 写入、Facade 开关、门控、运行时抽象、可恢复、可观测。
- 以最小改动保障现网安全：默认可关断、Shadow 不污染主对话、拒绝路径结构化。
- 为后续独立子 Agent 服务（含 Python 数据面）保留兼容契约：只替换运行时与传输层，不重做控制面与安全模型。

### 1.1 非目标（本期不做或仅预留）

- 不在本期完成完整双栈部署与跨语言编排（仅预留 `RemoteSubAgentRuntime` 抽象位）。
- Human-in-the-Loop 挂起审批先做状态预留，不强行打通完整前端交互闭环。
- 不引入大规模架构重写，优先沿现有 `coordinator`、`subagent`、`query` 模块渐进改造。

---

## 2. 设计约束（必须遵守）

- **编排入口唯一化**：上游仅通过 `MultiAgentFacade`（或等价门面）进入子 Agent 路径。
- **契约先行**：先固化 Spawn 请求/终态事件/超时与拒绝载荷，再做运行时替换。
- **状态外置**：子任务可恢复状态以 DB 权威为准，内存只做缓存/句柄。
- **门控集中**：深度、并发、TTL、权限由单一 Gatekeeper 执行，禁止散落判断。
- **工具硬过滤**：`Global_Safe_Tools ∩ Requested_Subset`，不得仅依赖 prompt 约束。
- **观测先行**：`trace_id/session_id/run_id` 与关键指标从 v1 起就打齐。

---

## 3. 里程碑与排期（建议 6~9 周）

> 可按两周一个 Sprint 推进；时间为建议值，按团队人力浮动。

| 里程碑 | 周期 | 目标 |
|--------|------|------|
| M1 | 第 1~2 周 | ContextObject 写入链路 + 失败降级 |
| M2 | 第 3 周 | 租户/会话底座收敛（TraceContext） |
| M3 | 第 4~5 周 | `demo.multi-agent.*` + Facade + Gatekeeper |
| M4 | 第 6 周 | Spawn 契约冻结 + `SubAgentRuntime` 抽象 |
| M5 | 第 7~8 周 | 子 run 持久化 + reconcile 恢复 |
| M6 | 第 9 周 | 指标、压测、灰度与上线检查 |

---

## 4. 分阶段执行项

## 4.1 M1：ContextObject 写入链路（优先级 P0）

### 目标

将超长工具结果从会话上下文外置到 `context_object`，主上下文仅保留存根；写入失败时主链路可降级继续。

### 任务清单

- 新增 `ContextObjectWriteService`（命名可调整）：
  - 输入：原始 `tool_result`、`conversation_id`、`tenant_id`、可选元信息（`producer_kind`、`token_estimate`）。
  - 输出：`ctx_object_id` 与存根文本（如 `[ctx-obj-xxxx]`）。
- 在 `EnhancedCompactionPipeline` 的 Tier1 路径接入外置逻辑：
  - 超阈值时：先写 DB，再把原始返回替换为存根。
  - 写失败时：执行 v1 §5.1 降级（头尾截断 + 明确 SYSTEM 标记）。
- 增加配置项（建议挂在 `demo.context-object`）：
  - `write-enabled`
  - `write-threshold-chars`
  - `fallback-head-chars`
  - `fallback-tail-chars`
  - `default-ttl-hours`（可选）
- 增加单测/集成测试：
  - 正常写入 -> 存根可读回；
  - 写入失败 -> 不抛链路级错误、返回降级片段。

### 验收标准（DoD）

- 超长工具结果不再直接长驻消息体（仅存根）。
- `read_context_object` 能按会话/租户读回正确正文。
- DB 故障场景下对话仍可继续，且日志与指标能识别失败原因。

---

## 4.2 M2：租户与会话一致性（优先级 P0）

### 目标

统一读写链路中的 `tenant_id/session_id` 来源，避免后期双栈拆分时再改安全边界。

### 任务清单

- 扩展 `TraceContext`（或等价上下文）承载 `tenantId`。
- `ContextObjectReadService` 与写入服务统一从服务端上下文取租户与会话信息，不信任模型传入值。
- 配置默认租户仅用于开发兜底，线上必须可由鉴权链路注入。
- 增加跨租户访问拒绝测试。

### 验收标准

- 任意读写路径均可追踪到一致的 `tenant_id/session_id`。
- 错租户读取稳定拒绝，且错误载荷结构化。

---

## 4.3 M3：Facade + 开关 + 门控（优先级 P0）

### 目标

形成可关断、可灰度、可控风险的多 Agent 入口。

### 任务清单

- 新增 `DemoMultiAgentProperties`：
  - `enabled`（默认 false）
  - `mode`（`off|shadow|on`）
  - `max-spawn-depth`
  - `max-concurrent-spawns`
  - `wallclock-ttl-seconds`
- 实现 `MultiAgentFacade`：
  - `enabled=false` -> 走 legacy/no-op；
  - `mode=shadow` -> 只统计与评估，不污染主对话；
  - `mode=on` -> 启用真实子 Agent 路径。
- 实现 `SpawnGatekeeper`（命名可调整）：
  - 统一深度、并发、TTL、工具权限校验；
  - 拒绝返回结构化 `must_do_next`，禁止裸异常导致模型循环重试。
- 已删除占位类 `SubagentTools`；`AsyncSubagentExecutor` 仅由 `LocalSubAgentRuntime` 调用，编排入口统一为 `MultiAgentFacade` + `SpawnGatekeeper`。

### 验收标准

- 三种模式行为可预测且可回归验证。
- Shadow 模式无“伪造成功”返回注入主模型。
- 开关关闭时与当前单 Agent 行为一致。

---

## 4.4 M4：契约冻结与运行时抽象（优先级 P1）

### 目标

把未来最容易变动的“执行位置”与“协议语义”解耦。

### 任务清单

- 定义统一 DTO（名称示例）：
  - `SpawnRequest`
  - `SpawnConstraints`
  - `SubRunEvent`（`STARTED/RUNNING/WAITING/COMPLETED/FAILED/TIMEOUT/REJECTED`）
  - `SpawnResult`
- 增加 `SubAgentRuntime` 接口：
  - 首版实现 `LocalSubAgentRuntime`（包装当前本地执行器）。
  - 预留 `RemoteSubAgentRuntime`（接口桩即可）。
- TTL 终态对齐：超时统一落为 `TIMEOUT`，不以文本字符串判断。

### 验收标准

- Facade 只依赖 `SubAgentRuntime` 与契约 DTO，不感知本地/远端细节。
- 超时、拒绝、失败均为结构化终态，可被前端/日志/指标统一消费。

---

## 4.5 M5：子 run 持久化与恢复（优先级 P1）

### 目标

从“进程内可用”升级到“重启后可恢复”。

### 任务清单

- 新增 Flyway（建议）：
  - `subagent_run`（`run_id/session_id/tenant_id/status/spec_json/deadline/created_at/updated_at`）
  - 必要索引：`(session_id,status)`、`(tenant_id,status)`。
- 在 Spawn 生命周期中落库：
  - 创建 run、状态迁移、终态写回。
- 启动恢复：
  - 实现 `reconcile(sessionId)` 或批量 reconcile 任务；
  - 处理卡死/超时 run 的补偿终态。
- 幂等控制：
  - 按 `run_id` 去重终态事件，保障至少一次投递可重放。

### 验收标准

- 进程重启后可识别并收敛未完成 run，不出现“幽灵任务”。
- 同一终态事件重复投递不造成状态污染。

---

## 4.6 M6：可观测、灰度与上线（优先级 P0）

### 目标

确保可运维、可回滚、可容量评估。

### 任务清单

- 指标接入（Micrometer + 现有监控栈）：
  - `subagent.spawn.rejected.rate`
  - `subagent.wallclock.timeout.count`
  - `context_object.write.failure.count`
  - `context_object.read.bytes` / token 估算
- 日志字段贯通：`trace_id/session_id/run_id`。
- 压测场景：
  - 高并发 spawn；
  - 大结果外置；
  - DB 短时不可用降级。
- 灰度策略：
  - 先 `off`（基线）-> `shadow`（观测）-> `on`（小流量）-> 全量。

### 验收标准

- 有可执行灰度清单与回滚清单。
- 指标可定位“拒绝率高/超时高/写入失败高”的根因路径。

---

## 5. 测试计划（按层次）

- **单元测试**：Gatekeeper 规则、DTO 终态映射、ContextObject 写降级逻辑。
- **集成测试**：DB 写读闭环、跨租户拒绝、TTL 超时收敛、Shadow 不污染。
- **回归测试**：`enabled=false` 的单 Agent 行为与原逻辑一致。
- **故障演练**：数据库短时不可用、执行器超时、重复事件投递。

---

## 6. 上线与回滚清单

### 上线前

- 配置默认值安全（`demo.multi-agent.enabled=false`）。
- 指标、日志字段、告警规则已启用。
- 灰度租户名单与流量比例已确认。

### 回滚策略

- 立即切回 `demo.multi-agent.mode=off` 或 `enabled=false`。
- 保留 `ContextObjectStore` 数据，不做破坏性清理。
- 若远端运行时已接入，切回 `LocalSubAgentRuntime`。

---

## 7. 风险与缓解

| 风险 | 表现 | 缓解 |
|------|------|------|
| compaction 与工具消息格式耦合 | 改动后工具结果解析异常 | 从 Tier1 单点切入，先小范围工具白名单试点 |
| 内存态与 DB 状态双轨不一致 | run 状态跳变异常 | 先“落库记录”再“恢复驱动”，分两步上线 |
| Shadow 设计走偏 | 主对话被影子结果污染 | 在 Facade 层硬断言：Shadow 不注入 spawn 成功结果 |
| 拒绝路径非结构化 | 模型循环重试 | 统一 `must_do_next` 结构并加测试快照 |

---

## 8. 与 v2 平滑迁移的检查点（Exit Criteria）

满足以下条件即可进入 v2 的双栈阶段（Java 控制面 + Python 数据面）：

- `MultiAgentFacade` 已稳定承载入口切换；
- `SubAgentRuntime` 抽象稳定，Local/Remote 可切；
- Spawn 契约（请求/事件/终态）已冻结并测试覆盖；
- ContextObject 读写链路与租户隔离稳定；
- 关键指标已用于灰度决策；
- 运行时替换不会影响上游 API 与权限模型。

---

## 9. 对应关系（v1 -> v2）

| v1 能力 | v2 对应阶段 |
|---------|-------------|
| ContextObject 写读 + 隔离 | Phase 1 |
| Facade + 开关 + Shadow | Phase 1/2 过渡 |
| Spawn 契约 + Runtime 抽象 | Phase 2 |
| 持久化 run + reconcile | Phase 2/3 |
| 指标与灰度 | 全阶段 |

该文档作为执行基线，建议每个里程碑结束后补充一次“完成情况 + 风险 + 下一阶段调整”。

---

## 10. 细化任务拆分（可直接建任务卡）

> 约定：每张任务卡至少包含「代码变更点 + 测试点 + 回滚点 + v2 兼容检查」。

### Sprint 1（M1 主体）：ContextObject 写入与降级

| 卡片 ID | 任务 | 主要改动点（建议） | 交付物 |
|---------|------|--------------------|--------|
| S1-01 | 写服务骨架 | `contextobject` 包新增写服务与 DTO | `ContextObjectWriteService` + 单测 |
| S1-02 | Compaction 接入外置 | `query/EnhancedCompactionPipeline` | 超阈值外置 + 存根替换 |
| S1-03 | 写失败降级 | 同上 + 配置类 | SYSTEM 标记 + 头尾截断逻辑 |
| S1-04 | 配置项落地 | `application.yml` + Properties | 可控阈值与开关 |
| S1-05 | 集成验证 | 集成测试目录 | 大结果写读闭环、DB 故障降级 |

### Sprint 2（M2 + M3 前半）：身份收敛与 Facade 骨架

| 卡片 ID | 任务 | 主要改动点（建议） | 交付物 |
|---------|------|--------------------|--------|
| S2-01 | TraceContext 扩展 tenant | `trace/context` 相关类 | `tenantId` 注入与读取 |
| S2-02 | 读写统一鉴权源 | `ContextObjectReadService` + 写服务 | 不信任模型传入会话/租户 |
| S2-03 | 多 Agent 配置对象 | `config` 包 | `DemoMultiAgentProperties` |
| S2-04 | Facade 壳层接入 | `coordinator/subagent` 入口层 | `MultiAgentFacade`（off/shadow/on） |
| S2-05 | Shadow 行为断言 | Facade/测试 | Shadow 不注入伪成功结果 |

### Sprint 3（M3 后半 + M4）：门控与运行时抽象

| 卡片 ID | 任务 | 主要改动点（建议） | 交付物 |
|---------|------|--------------------|--------|
| S3-01 | Gatekeeper 统一门控 | 新增 `SpawnGatekeeper` | 深度/并发/TTL/工具权限统一校验 |
| S3-02 | Spawn 契约冻结 | `subagent` DTO 包 | `SpawnRequest/SubRunEvent/SpawnResult` |
| S3-03 | Runtime 抽象 | `subagent/runtime` | `SubAgentRuntime` + `LocalSubAgentRuntime` |
| S3-04 | 入口收敛改造 | 已删 `SubagentTools`；`AsyncSubagentExecutor` 仅 Runtime | 全量经 Facade + Gatekeeper |
| S3-05 | 终态一致性 | 事件映射层 | `TIMEOUT/REJECTED/FAILED` 结构化 |

### Sprint 4（M5 + M6）：可恢复与上线保障

| 卡片 ID | 任务 | 主要改动点（建议） | 交付物 |
|---------|------|--------------------|--------|
| S4-01 | run 表迁移 | Flyway 新 migration | `subagent_run` 表与索引 |
| S4-02 | 生命周期落库 | runtime / coordinator | 创建、迁移、终态写回 |
| S4-03 | reconcile 恢复 | 启动流程或定时任务 | 重启后收敛未完成 run |
| S4-04 | 指标与日志 | metrics/logging | §6.1 黄金指标接入 |
| S4-05 | 灰度与回滚演练 | 运维脚本/文档 | off->shadow->on 灰度报告 |

---

## 11. 功能测试点矩阵（必须覆盖）

### 11.1 P0 核心能力测试（上线门槛）

| 编号 | 功能点 | 前置条件 | 测试步骤 | 期望结果 |
|------|--------|----------|----------|----------|
| T-P0-01 | 超长结果外置写入 | `write-enabled=true` | 调用返回超阈值内容的工具 | 消息中仅存根；DB 有正文记录 |
| T-P0-02 | 外置写失败降级 | 模拟 DB 异常 | 触发超阈值工具结果 | 主链路继续；返回 SYSTEM 降级片段 |
| T-P0-03 | read 鉴权（同租户同会话） | 已存在 ctx 对象 | 调用 `read_context_object` | 正确返回正文（受字符/token 限制） |
| T-P0-04 | read 鉴权（跨租户） | A/B 两租户数据 | B 租户读 A 对象 | 拒绝且结构化错误，不泄漏内容 |
| T-P0-05 | 多 Agent 开关 off | `enabled=false` | 执行常规对话与工具调用 | 与旧路径行为一致，无 spawn 副作用 |
| T-P0-06 | 模式 shadow | `mode=shadow` | 触发可 spawn 的请求 | 不向主模型注入伪成功结果，仅记录评估 |
| T-P0-07 | 模式 on + 门控拒绝 | 配置低阈值 | 发起超限 spawn | 返回 `must_do_next`，无循环重试 |
| T-P0-08 | TTL 超时终态 | 设置短 TTL | 发起长任务 | 终态为 `TIMEOUT`，可观测可追踪 |

### 11.2 P1 可靠性与恢复测试（灰度门槛）

| 编号 | 功能点 | 前置条件 | 测试步骤 | 期望结果 |
|------|--------|----------|----------|----------|
| T-P1-01 | run 落库与状态迁移 | 启用 run 持久化 | 发起并完成子任务 | DB 状态按生命周期迁移 |
| T-P1-02 | 重启恢复 reconcile | 存在 RUNNING 任务 | 服务重启并执行恢复 | 未完成任务被收敛为可解释终态 |
| T-P1-03 | 事件重复投递幂等 | 注入重复终态事件 | 连续提交同 `run_id` 终态 | 状态不抖动、仅一次生效 |
| T-P1-04 | 并发 spawn 限流 | 配置并发上限 | 并发触发多任务 | 超限任务被门控拒绝且可观测 |
| T-P1-05 | 指标完整性 | 指标采集开启 | 触发拒绝、超时、写失败场景 | 对应指标值增长且标签正确 |

### 11.3 回归与兼容测试（防止破坏原系统）

| 编号 | 功能点 | 前置条件 | 测试步骤 | 期望结果 |
|------|--------|----------|----------|----------|
| T-REG-01 | 单 Agent 回归 | `enabled=false` | 回归核心聊天脚本 | 结果与基线一致 |
| T-REG-02 | 工具权限硬过滤 | 配置 restricted tools | 请求超权限工具 | 工具不暴露或被拒绝 |
| T-REG-03 | 日志字段完整 | 打开请求日志 | 触发一次完整链路 | `trace_id/session_id/run_id` 可串联 |
| T-REG-04 | 配置热切换风险 | 切换 off/shadow/on | 压测中切换模式 | 无崩溃，行为符合模式定义 |

---

## 12. 按里程碑的 v2 平滑迁移保护清单

> 每个里程碑合并前，必须勾选本节对应项，避免后期“可运行但不可拆”。

### M1（ContextObject）

- [ ] 存根格式有固定协议（可被远端运行时识别），非自由文本拼接。
- [ ] 写失败降级文案与结构稳定，可被 Python 侧未来统一处理。
- [ ] 写/读都不依赖本地线程上下文之外的隐式状态。

### M2（身份）

- [ ] `tenant_id/app_id/session_id` 至少具备统一注入位（`app_id` 可先占位）。
- [ ] 服务端鉴权结果是唯一可信来源，模型入参不作为授权依据。

### M3（Facade/Gatekeeper）

- [ ] 上游入口不直接调用具体执行器（仅依赖 Facade）。
- [ ] Shadow 模式行为在代码层硬断言，不靠提示词约定。
- [ ] Gatekeeper 决策结果结构化（允许/拒绝/原因/建议下一步）。

### M4（契约/Runtime）

- [ ] Spawn 契约有版本号或兼容策略（建议 `version` 字段）。
- [ ] `SubAgentRuntime` 接口与传输层解耦（本地/远端同签名）。
- [ ] 超时/取消/失败终态语义在本地与未来远端完全一致。

### M5（持久化/恢复）

- [ ] run 状态机可被事件驱动重放（不依赖进程内瞬时对象）。
- [ ] reconcile 逻辑在远端执行器场景仍成立（按 run_id 拉齐）。

### M6（可观测/灰度）

- [ ] 指标标签预留 `runtime_type=local|remote`（为双栈切换做对比）。
- [ ] 灰度规则支持按租户/应用分流，而非仅全局开关。
- [ ] 回滚路径可在不改 API 的情况下从 remote 切回 local。

---

## 13. 建议的测试执行顺序（节奏）

1. 先跑 T-P0-01~T-P0-04（确认 ContextObject 基础安全）。
2. 再跑 T-P0-05~T-P0-08（确认开关、Shadow、门控、TTL）。
3. 然后跑 T-P1-01~T-P1-05（可靠性与可恢复）。
4. 最后跑 T-REG 全量回归，并输出灰度建议（off/shadow/on）。

建议每个 Sprint 结束时提交一份「测试结果摘要」：通过数、失败用例、阻断风险、是否允许进入下一阶段。

---

## 14. 外部参考代码路径（Claude Code / OpenClaw）

为避免开发时只看文档不看实现，建议在本地固定以下两个参考仓库路径，并在每个 Sprint 的技术评审中对照关键实现。

### 14.1 建议的本地路径约定（Windows）

- Claude Code 源码路径：`G:\project\claude-code`
- OpenClaw 源码路径：`G:\datasets\openclaw`

> 若你的实际路径不同，直接替换为你本机路径；建议团队统一到同一目录规范，便于脚本与文档复用。

### 14.2 可选环境变量（推荐）

- `CLAUDE_CODE_REPO=G:\project\claude-code`
- `OPENCLAW_REPO=G:\datasets\openclaw`

作用：后续测试脚本、对照脚本、知识迁移文档可直接引用环境变量，减少硬编码路径改动。

### 14.3 每个里程碑的对照参考点

| 里程碑 | 优先参考仓库 | 对照实现点 |
|--------|--------------|------------|
| M1 | OpenClaw | tool result 预算/压缩、超长结果处理与失败兜底 |
| M2 | OpenClaw | 会话恢复、状态持久化、幂等事件思路 |
| M3 | Claude Code + OpenClaw | 子 Agent 触发策略、门控、工具白名单 |
| M4 | Claude Code | 子任务事件语义与运行时抽象边界 |
| M5 | OpenClaw | `reconcile` / 恢复路径与超时收敛 |
| M6 | 双方 | 指标命名、灰度与回滚策略 |

### 14.4 团队执行要求（建议）

- 每张开发任务卡必须附一条“参考实现来源”（Claude Code / OpenClaw / 本仓库）。
- 若参考实现与本项目冲突，优先遵守本文档的 v1 约束与 v2 平滑迁移边界。
- 代码评审中必须说明“为什么采用该实现差异”，并标注未来迁移影响。

