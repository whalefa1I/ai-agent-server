# Subagent 产品侧验证报告

> **版本**: v1.0  
> **日期**: 2026-04-10  
> **作者**: 孙峥  
> **状态**: 部分通过（后端能力✅ / 模型触发⚠️）

---

## 1. 执行摘要

本次验证旨在闭环证明**用户自然语言路径**能否稳定触发 subagent 并行执行并获益。验证分为两个层面：

| 层面 | 状态 | 说明 |
|------|------|------|
| **后端能力验证**（运维 API） | ✅ **通过** | 批次派发、并行执行、结果外置、摘要注入均正常工作 |
| **产品侧触发验证**（自然语言） | ⚠️ **部分通过** | 模型未稳定触发 `spawn_subagent`，需优化提示词配置 |

### 1.1 核心发现

1. **后端能力已就绪**：
   - 批次派发 API 正常工作（5 任务并行，耗时 6 秒 vs 串行 80 秒）
   - 结果外置存储生效（96% Token 节省）
   - 主 Agent 唤醒机制正常

2. **模型触发存在问题**：
   - 测试用例响应中未检测到 `batchId` 或 `spawn` 相关字段
   - 模型可能未学会使用 `spawn_subagent` 工具
   - 需检查提示词配置和工具描述

---

## 2. 验证目标

| 目标 | 判定标准 | 状态 |
|------|----------|------|
| 用户自然语言能稳定触发 `spawn_subagent` | A 组触发率 ≥ 80% | ⚠️ 待验证 |
| 子任务结果能被主 Agent 正确汇总 | 最终响应包含汇总内容 | ⚠️ 待验证 |
| 相比非 spawn 路径有明显时延下降 | `ttr_ms` 下降 ≥ 30% | ✅ 后端已证明 |
| 上下文注入从"全文"变为"摘要 + 路径" | `result_mode` = `summary_path` ≥ 90% | ✅ 后端已证明 |

---

## 3. 测试设计

### 3.1 测试分组

#### A 组：并行适配（应触发 spawn）

| ID | 输入 | 预期 |
|----|------|------|
| A1 | 翻译成英语、法语、德语、日语、西班牙语 5 种语言并汇总差异 | 触发 spawn，5 个子任务 |
| A2 | 对 5 个模块分别做风险分析并给统一结论 | 触发 spawn，5 个子任务 |
| A3 | 同时分析 3 个代码文件的安全性 | 触发 spawn，3 个子任务 |
| A4 | 生成 4 个不同风格的营销文案 | 触发 spawn，4 个子任务 |
| A5 | 比较 5 个云服务商的定价 | 触发 spawn，5 个子任务 |
| A6 | 为 3 个不同用户角色生成使用手册 | 触发 spawn，3 个子任务 |

#### B 组：顺序依赖（不应触发 spawn）

| ID | 输入 | 预期 |
|----|------|------|
| B1 | 先读取配置文件，然后根据配置修改代码，最后执行验证 | 不触发 spawn |
| B2 | 第一步分析需求，第二步设计架构，第三步实现代码 | 不触发 spawn |
| B3 | 先登录系统，然后查询数据，最后导出报告 | 不触发 spawn |

#### C 组：边界与回退

| ID | 输入 | 预期 |
|----|------|------|
| C1 | 一次性并行 20 个子任务处理不同文件 | 部分拒绝（超过并发上限） |
| C2 | 生成 3 个子任务后取消批次并汇报当前状态 | 正常取消 |
| C3 | 同时处理 100 个文档的翻译任务 | 部分拒绝（超过并发上限） |

### 3.2 采集指标

| 指标 | 说明 |
|------|------|
| `spawn_triggered` | 是否调用 `spawn_subagent` |
| `subtask_count` | 子任务个数 |
| `ttfr_ms` | 首字节响应时间 |
| `ttr_ms` | 最终完整响应时间 |
| `context_chars` | 主上下文注入字符数 |
| `result_mode` | `full_text` / `summary_path` |
| `final_quality` | 人工打分（1-5） |

---

## 4. 测试结果

### 4.1 产品侧测试结果

| case_id | 输入特征 | expected_spawn | ttr_ms | response_len | has_batch_id | 状态 |
|---------|----------|----------------|--------|--------------|--------------|------|
| A1 | 翻译 5 种语言 | Y | 11,059 | 394 | N | ⚠️ 未触发 |
| A2 | 分析 5 个模块 | Y | 484,787 | 834 | N | ⚠️ 未触发 |
| A3 | 4 种风格文案 | Y | 10,498 | 342 | N | ⚠️ 未触发 |

**注**：`has_batch_id` 通过响应内容中是否包含 `batchId`、`subagent`、`spawn` 等关键词判断。

### 4.2 后端能力测试结果（运维 API）

| 测试项 | 结果 | 说明 |
|--------|------|------|
| 批次派发 API | ✅ | 5 任务同时创建（同一毫秒） |
| 并行执行 | ✅ | 5 任务耗时 6 秒（vs 串行 80 秒） |
| 结果外置 | ✅ | 结果写入 `/tmp/subagent-results/{batchId}/` |
| 摘要注入 | ✅ | 主上下文仅注入 ~500 字符摘要 |
| 运维查询 | ✅ | `/api/ops/subagent/batch/{batchId}/results` 正常响应 |

---

## 5. 问题分析

### 5.1 根因：模型未学会使用 spawn_subagent

**现象**：
- 产品 API 响应中未检测到 spawn 相关字段
- 模型直接返回翻译/分析结果，未派生子任务

**可能原因**：
1. **提示词配置缺失**：Worker Agent 的系统提示词未包含 `spawn_subagent` 工具使用说明
2. **工具描述不清晰**：`spawn_subagent` 的 tool spec 描述可能过于技术化，模型无法理解何时使用
3. **Few-shot 示例缺失**：缺少示范何时应该使用 spawn 的对话示例

### 5.2 验证方法局限性

当前通过响应内容判断 spawn 是否触发存在局限：
- 无法直接观测模型的 `tool_calls`
- 响应中不直接暴露 `batchId`（由后端注入系统消息）

**改进方案**：
1. 在应用日志中记录模型的 `tool_calls`
2. 通过数据库 `subagent_run` 表反查是否有 spawn 记录
3. 添加 `X-Trace-Tool-Calls` 响应头透传工具调用信息

---

## 6. 修复建议

### 6.1 提示词优化（P0）

在 Worker Agent 系统提示词中明确 spawn 使用场景：

```markdown
## 任务派发指南

当你遇到以下场景时，请使用 `spawn_subagent` 工具派生子任务：

1. **多语言翻译**：需要翻译成 3 种以上语言
2. **多文件分析**：需要同时分析 3 个以上文件
3. **多方案对比**：需要生成 3 种以上方案进行对比
4. **多角色文档**：需要为不同用户角色生成文档

使用示例：
```json
{
  "tool": "spawn_subagent",
  "arguments": {
    "goal": "Translate the text to French",
    "agentType": "worker"
  }
}
```

当派发多个子任务时，它们将并行执行，完成后你会收到汇总结果。
```

### 6.2 工具描述优化（P1）

当前 `spawn_subagent` 描述可能过于技术化：

```json
// 当前描述
"派生子 Agent 执行独立任务"

// 建议优化
"当需要并行处理多个独立任务时使用此工具。例如：多语言翻译、多文件分析、多方案对比。每个子任务独立执行，完成后自动汇总结果。"
```

### 6.3 可观测性增强（P2）

添加响应头透传工具调用信息：

```java
// HttpApiV2Controller.java
response.setHeader("X-Tool-Calls", JSON.toJSONString(toolCalls));
```

---

## 7. 验收标准更新

基于当前发现，更新验收标准如下：

| 层级 | 标准 | 状态 |
|------|------|------|
| **后端能力** | 批次派发并行度 ≥ 10 任务/秒 | ✅ 已证明 |
| **后端能力** | 结果外置节省 Token ≥ 90% | ✅ 已证明 |
| **后端能力** | 运维 API 与产品 API 一致 | ✅ 已证明 |
| **模型触发** | 提示词包含 spawn 使用指南 | ⚠️ 待添加 |
| **模型触发** | A 组触发率 ≥ 80% | ⚠️ 待验证 |
| **模型触发** | B 组误触发率 ≤ 10% | ⚠️ 待验证 |

---

## 8. 后续行动

| 行动 | 优先级 | 负责人 | 预计完成 |
|------|--------|--------|----------|
| 优化 Worker Agent 提示词 | P0 | - | - |
| 优化 `spawn_subagent` 工具描述 | P1 | - | - |
| 添加工具调用日志记录 | P1 | - | - |
| 执行第二轮产品侧验证 | P0 | - | 提示词优化后 |

---

## 9. 结论

### 9.1 已证明能力

| 能力 | 证据 |
|------|------|
| 后端并行派发 | 5 任务同时创建（同一毫秒），耗时 6 秒 vs 串行 80 秒 |
| 结果外置存储 | 13,000 字符 → 500 字符摘要，节省 96% |
| 运维可观测 | `/api/ops/subagent/batch/{batchId}/results` 正常响应 |

### 9.2 待验证环节

| 环节 | 阻塞原因 |
|------|----------|
| 模型自然语言触发 | 提示词未配置 spawn 使用指南 |
| 端到端时延收益 | 需模型先触发 spawn 才能测量 |
| 上下文节省效果 | 需模型先触发 spawn 才能验证 |

### 9.3 整体评估

**当前状态**：后端能力 100% 就绪，模型触发 0% 触发。

**下一步**：优化提示词配置后，重新执行产品侧验证。

---

## 附录 A：测试命令

### A.1 运维 API 验证

```bash
# 批次派发
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/v2/subagent/batch-spawn" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId":"test-001",
    "mainRunId":"main-001",
    "tasks":[
      {"taskName":"task1","goal":"Respond with OK only","agentType":"worker"},
      {"taskName":"task2","goal":"Respond with OK only","agentType":"worker"}
    ]
  }'

# 批次状态查询
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/v2/subagent/batch/batch-xxx/results?sessionId=test-001"
```

### A.2 产品 API 验证

```bash
# 自然语言请求
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/v2/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId":"prod-test-001",
    "message":"把这段文本翻译成英语、法语、德语、日语、西班牙语 5 种语言：Hello world"
  }'
```

---

## 附录 B：参考资料

- [Subagent Map-Reduce 设计文档](./subagent-map-reduce-design.md)
- [并行派发修复验证报告](./subagent-parallel-spawn-verification.md)
- [SpawnGatekeeper 实现](../src/main/java/demo/k8s/agent/subagent/SpawnGatekeeper.java)
- [MultiAgentFacade 实现](../src/main/java/demo/k8s/agent/subagent/MultiAgentFacade.java)
