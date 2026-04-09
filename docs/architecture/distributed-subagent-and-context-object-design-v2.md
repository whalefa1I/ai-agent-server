# 架构决策记录 (ADR)：Agent-as-a-Service (AaaS) 平台化双栈架构演进

## 1. 执行摘要与演进背景 (Executive Summary)

### 1.1 演进背景

随着业务线对 AI 能力需求的爆发，现有的“单体 Java AI 后台”面临以下瓶颈：

1. ​**生态撕裂**​：主流大模型、RAG 组件、Agent 编排框架（如 LangChain, LlamaIndex, LangGraph）均以 Python 生态为主，Java 强行跟进成本过高，且落后于社区节奏。
2. ​**状态灾难**​：复杂的 Agent 链式调用和长时任务（如代码执行、人类审批）容易导致 Java 进程内存 OOM 和线程池枯竭，影响线上普通对话。
3. ​**架构耦合**​：工具调用（Tools）与具体业务逻辑深度耦合，新业务线接入难度大，无法实现“平台级赋能”。

### 1.2 目标与战略 (The 2-Year Tech Dividend)

以\*\*“双栈架构 (Dual-Stack)”\*\*为核心，将系统从单一应用升级为 ​**AaaS (Agent-as-a-Service) 平台**​。

* ​**锁定技术红利**​：通过 Python 执行面，确保在未来 2 年内能够第一时间零成本接入全球最新的开源 AI 框架库，保持技术代差优势。
* ​**企业级高可用**​：通过 Java 控制面，死守多租户隔离、数据合规管控、账单计费与高并发网关的生命线。

---

## 2. 总体架构蓝图 (Macro Architecture)

系统将在物理与逻辑上拆分为两大核心平面：**控制面 (Control Plane)** 与 ​**AI 数据面 (AI Data Plane)**​。

### 2.1 控制面 (Java + Spring Boot) —— “平台网关与大管家”

负责除了“与大模型对话”之外的所有基础设施能力：

* ​**统一接入网关**​：对外暴露 RESTful / SSE / WebSocket API，承接多终端和多业务线请求。
* ​**三维身份认证**​：解析并注入 `Tenant_ID`（租户）、`App_ID`（应用）、`Session_ID`（会话）。
* ​**资源管控层**​：Token 限流熔断（Rate Limiting）、成本审计、配额管理。
* ​**记忆持久化层**​：全权负责 `ContextObjectStore`（超长外脑记忆）的 CRUD 与越权拦截。

### 2.2 AI 数据面 (Python + FastAPI/LangGraph) —— “最强大脑与执行器”

纯粹的无状态（Stateless）计算节点，负责重度 AI 逻辑：

* ​**编排引擎 (Orchestrator)**​：接管 Prompt 组装、上下文窗口压缩、多 Agent 并行 Fork。
* ​**大模型通信底座**​：对接各种底层 LLM（OpenAI, 闭源大厂 API, 本地 vLLM 部署）。
* ​**联邦工具运行时 (Federated Tool Runtime)**​：执行各类 Python 原生工具（如 Pandas 数据分析、Playwright 爬虫）或通过 Webhook 回调业务线的自定义工具。

---

## 3. 核心接口与通信契约 (Interface Contracts)

为了解耦两大平面并适配未来多业务线接入，彻底摒弃传统的同步 JSON 请求，全面转向**事件驱动 (Event-Driven)** 架构。

### 3.1 控制面 -> 数据面 (gRPC / HTTP2)

Java 向 Python 发起 Agent 任务调度，必须携带**三维上下文**与​**硬性约束**​：

```
// SpawnRequest / ChatRequest
{
  "trace_id": "req-987654321",
  "context": {
    "tenant_id": "corp-xyz",
    "app_id": "hr-assistant-01",
    "session_id": "sess-12345"
  },
  "constraints": {
    "max_budget_tokens": 8000,
    "allowed_tool_scopes": ["read:employee_manual", "write:ticket"]
  },
  "events": [
    {"type": "user_message", "content": "帮我查一下年假政策"}
  ]
}
```

### 3.2 数据面 -> 控制面 (Server-Sent Events / Stream)

Python 将 Agent 的思考、工具调用、结果以**流式事件**推送回 Java 侧，Java 负责广播或落库：

```
// AgentEventStream
{"type": "thought", "content": "正在检索人力资源知识库..."}
{"type": "tool_call", "tool_name": "search_hr_kb", "args": {"query": "年假"}}
{"type": "suspend", "reason": "require_human_approval", "action": "提交请假单"} // 触发挂起逻辑
{"type": "message", "content": "您的年假还有5天，需要我帮您发起请假流程吗？"}
```

---

## 4. 平台核心领域模型设计 (Domain Modeling)

### 4.1 联邦工具注册中心 (Federated Tool Registry)

为了支持业务线“自带工具入驻”，引入工具生命周期管理：

* ​**System Tools**​：Python 层内置原生工具（如代码解释器、全网搜索），高配额开放。
* ​**Tenant Tools**​：租户/业务线通过提供 OpenAPI Spec / Webhook 注册的自定义工具（如“查询某业务订单”）。Python 层无需感知这些代码的实现，只需在模型决定调用时，通过 HTTP 回调 Java 或业务线网关即可。

### 4.2 记忆与状态管理 (Context & Memory)

复用前序设计的 `ContextObjectStore`，在双栈架构下增加传输优化：

* Python 遇到超大工具返回（如 5MB 日志）时，不再将其放在内存中传递，而是直接请求 Java 侧的内部 API：`POST /internal/context_objects`。
* Java 持久化入库后，返回一个短 ID `[ctx-obj-uuid]` 给 Python。
* Python 将该短 ID 丢给大模型，避免了跨进程序列化灾难和 OOM 风险。

---

## 5. 安全与隔离底线 (Security & Blast Radius)

在平台化运营下，安全是唯一的红线。

1. ​**绝对隔离**​：Java 控制面在转发请求前，必须在底层拦截器中将 `Tenant_ID` 强绑定至请求线程。数据库层的每一次查询（包括记忆读写）必须带有 `WHERE tenant_id = ?` 语句。
2. ​**工具权限沙箱**​：基于 `allowed_tool_scopes`，在 Python 层做​**物理裁剪**​。如果 HR 应用请求了不属于它的“数据库修改工具”，框架层直接抛出异常，不允许该工具暴露给大模型。
3. ​**墙上时钟熔断 (Wall-Clock TTL)**​：Java 层维护超时看门狗（Watchdog）。若 Python 侧的某个 Agent 任务超过 3 分钟未返回终态事件，Java 层有权主动下发 `CancelRequest` 强制回收计算资源。

---

## 6. 演进路线图与里程碑 (Roadmap)

建议采用\*\*“渐进式剥离”\*\*策略，控制风险：

* **Phase 1: 基础设施铺垫 (M1 - M2)**
  * 在现有的 Java 单体中，实现三维身份模型 (`Tenant/App/Session`)。
  * 落地 `ContextObjectStore` (外脑记忆 DB 化)，跑通跨会话的数据隔离。
* **Phase 2: 双栈骨架搭建 (M3 - M4)**
  * 引入 Python 节点，搭建 FastAPI + LangGraph 基础通信框架。
  * 制定 Java 与 Python 之间的双向 Event Stream 协议。
  * ​**灰度切流**​：挑选一个边缘的、内部试水的 AI 应用，其核心编排逻辑交由 Python 层接管。
* **Phase 3: 平台化与能力开放 (M5 - M6)**
  * 实现联邦工具注册中心，跑通“业务线提供 API -> 平台自动注册为 Tool -> Agent 自动调用”的全链路。
  * 完善 Java 侧的监控大盘（Token 消耗图表、任务挂起/超时指标）。
  * 正式对公司内部各业务线宣布提供 AaaS 接入能力。

---

## 7. 结语

从业务后台到平台底座，这是一个痛苦但回报丰厚的蜕变。这套双栈架构在保证了企业级严谨性的同时，最大程度释放了 Python 生态在 AI 浪潮中的狂飙突进之力。它足以支撑你们团队平稳穿越未来 2-3 年大模型底层技术的动荡期。

任何高大上的架构设计，如果不能平滑落地、不能让老系统安全迁移，那就是空中楼阁。

针对你的问题：“这套方案能**保证实现**吗？”以及“能**方便地迁移**老结构吗？”

我的回答是：**架构本身不能包治百病，但这份设计通过“门面模式（Facade）”和“状态外置（ContextObjectStore）”，为你提供了一条目前业界容错率最高、迁移成本最可控的落地路径。**

以下我为你拆解，这套设计是如何在工程落地上保护你的团队，并帮助你平滑迁移的：

### 一、 为什么这套设计能“保底”你的实现？（关于落地确定性）

很多团队搞子 Agent 系统失败，不是因为大模型不聪明，而是因为工程底座崩溃（比如内存 OOM、异步任务成为“孤儿”、超长上下文导致请求全部超时）。这套设计通过以下三点保证了落地成功率：

1. **先防守，再进攻（ContextObjectStore 是定海神针）：** 这套架构没有一上来就让你去搞极其复杂的 Python 分布式调度，而是让你第一步先在 Java 里把 `ContextObjectStore`（巨型文本存根库）建起来。**只要你把上下文爆炸的问题按在了数据库里，子 Agent 无论怎么发散，你的核心内存都不会爆。** 这是落地的第一重保证。
2. **TTL 与硬门控（防止无底洞）：** 基于配置的 `maxSpawnDepth` 和 `Wall-Clock TTL` 机制。这意味着你闭着眼睛上线，最坏的情况也就是子 Agent 超时被杀掉，给用户回一句“任务太复杂失败了”，​**绝不会发生系统级雪崩**​。
3. **退路明确（总开关机制）：** 我们在配置文件里预留了 `demo.multi-agent.enabled = false`。一旦上线发现子系统乱跑、大模型产生严重幻觉，你只需要改个配置重启，系统瞬间退化成原来的单 Agent 模式。**这种“一键回滚”能力是技术团队敢于上生产的底气。**

### 二、 如何“方便地”迁移你之前的 Agent 结构？（迁移路径）

你之前的 Agent 结构大概率是紧耦合的（接收 Prompt -> 调大模型 -> 选工具 -> 吐出结果）。要迁移到这套新系统，​**千万不要重写老代码，而是要“套壳重构”**​。

这套设计通过 `MultiAgentFacade`（门面模式） 完美解决了老代码的迁移问题。具体的重构步骤如下，按这个顺序做，你会发现极其平滑：

#### 步骤 1：接口套壳（对上游业务屏蔽变化）

不要去动你原来的核心业务代码。把你们现在负责和用户对话的主入口类（假设叫 `ChatService`），重命名为 `LegacyChatService`。 然后，写一个新的 `MultiAgentFacade` 实现同一个接口：

```
public class MultiAgentFacade implements ChatInterface {
    private LegacyChatService legacyService;
    private MultiAgentOrchestrator newOrchestrator;

    public String chat(String input) {
        if (!config.isMultiAgentEnabled()) {
            // 开关关闭时，100% 走你的老代码，毫无风险
            return legacyService.chat(input); 
        } else {
            // 开关打开时，走新的多 Agent 编排逻辑
            return newOrchestrator.chat(input);
        }
    }
}
```

**收益：前端和上游业务线一行代码都不用改，平滑切流。**

#### 步骤 2：工具物理抽离（Tool Registry 化）

以前你们的工具（比如搜数据库、查天气）可能是直接写在老 Agent 的类里的。现在，把它们提炼成独立的 `ToolExecutor` 接口实现。 老 Agent 和未来的子 Agent，都去一个统一的 `ToolRegistry` 里拿工具用。这样你的老工具就变成了“全局资产”，新子系统可以直接白嫖你们之前积累的工具。

#### 步骤 3：老 Agent 降级为“主节点” (Master Agent)

在新的架构里，​**不要废弃你之前的 Agent**​。把以前那个单体 Agent 当作这套系统里的 `Coordinator`（主协调节点）。

* 简单的聊天，主节点直接回答（复用老逻辑）。
* 当用户提出复杂需求（比如“去爬取 A 网站并和 B 数据库对比”），主节点通过调用你新写的 `sessions_spawn` 工具，把脏活累活丢给子系统。

### 总结与行动建议

这套方案​**不能让你“不写代码就完成升级”**​（实际上由于引入了事件流和上下文隔离，初期的代码量不小）。

但是，它​**保证了你不会把现有系统改炸**​。通过 Facade 模式和明确的配置开关，你可以做到：

* **白天照常跑老业务。**
* **晚上切 5% 的测试流量到子 Agent 架构。**
* **一有风吹草动立刻切回老逻辑。**

如果你准备动手，建议你先提一个 PR：**只建 ​**`context_object` 表，并把你们老代码里最大的几个日志输出工具（比如返回巨大 JSON 的那种工具），改造成返回 `[ctx-obj-id]` 的存根模式。 只要这一小步走通了，后面的子 Agent 派生就是水到渠成的事情。
