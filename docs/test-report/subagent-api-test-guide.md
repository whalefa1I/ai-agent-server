# 子 Agent 系统 API 测试指南

## 概述

本文档提供对 `ai-agent-server` 子 Agent 系统进行 API 测试的完整方案。

**生产环境地址**: `https://ai-agent-server-production-d28a.up.railway.app`

---

## 前置条件

### 1. 启用子 Agent 功能

子 Agent 系统默认关闭，需通过环境变量启用：

```bash
# 启用子 Agent
DEMO_MULTI_AGENT_ENABLED=true
DEMO_MULTI_AGENT_MODE=on  # 或 shadow（影子模式）

# 配置参数（可选，使用默认值）
DEMO_MULTI_AGENT_MAX_SPAWN_DEPTH=3
DEMO_MULTI_AGENT_MAX_CONCURRENT_SPAWNS=5
DEMO_MULTI_AGENT_WALLCLOCK_TTL=180
```

### 2. 日志查询可用

确认 `/api/logs/*` 端点正常工作：

```bash
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?date=2026-04-09&page=1&size=10"
```

---

## 日志查询 API

### GET /api/logs/chain

查询链路日志，支持多种筛选条件。

**参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `requestId` | string | 否 | 请求 ID（推荐优先使用） |
| `traceId` | string | 否 | 追踪 ID（推荐） |
| `sessionId` | string | 否 | 会话 ID |
| `runId` | string | 否 | 子 Agent 运行 ID |
| `taskId` | string | 否 | 任务 ID |
| `keyword` | string | 否 | 关键词搜索 |
| `date` | string | 否 | 日期 (yyyy-MM-dd) |
| `page` | int | 否 | 页码（默认 1） |
| `size` | int | 否 | 每页大小（默认 100） |

**示例**:

```bash
# 按 traceId 查询
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?traceId=abc123&date=2026-04-09"

# 按 requestId 查询
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?requestId=req-123&date=2026-04-09"

# 按 sessionId 查询
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?sessionId=session-xyz&date=2026-04-09"

# 按 runId 查询子 Agent 日志
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?runId=run-123&date=2026-04-09"

# 关键词搜索
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?keyword=gatekeeper&date=2026-04-09"
```

**响应示例**:

```json
{
  "success": true,
  "data": [
    {
      "timestamp": "2026-04-09T10:30:00.123Z",
      "level": "INFO",
      "traceId": "abc123",
      "sessionId": "session-xyz",
      "runId": "run-123",
      "message": "[Gatekeeper] Spawn started: sessionId=session-xyz, runId=run-123",
      "eventType": "SUBAGENT_STARTED"
    }
  ],
  "pagination": {
    "total": 1,
    "page": 1,
    "size": 100
  },
  "filters": {
    "traceId": "abc123",
    "sessionId": "",
    "runId": "",
    "requestId": ""
  }
}
```

### GET /api/logs/query

通用日志查询接口，支持更多筛选条件。

**参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `traceId` | string | 否 | 追踪 ID |
| `requestId` | string | 否 | 请求 ID |
| `sessionId` | string | 否 | 会话 ID |
| `runId` | string | 否 | 运行 ID |
| `taskId` | string | 否 | 任务 ID |
| `eventType` | string | 否 | 事件类型 |
| `keyword` | string | 否 | 关键词 |
| `date` | string | 否 | 日期 (yyyy-MM-dd) |
| `page` | int | 否 | 页码（默认 1） |
| `size` | int | 否 | 每页大小（默认 50） |

---

## 子 Agent 测试用例

### 测试 1: 基本派生请求（Valid Spawn）

**目的**: 验证子 Agent 能够正常派生和执行。

**请求**:

```bash
# 使用支持子 Agent 派生的 prompt
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Request-Id: test-valid-spawn-001" \
  -d '{
    "message": "请帮我分析这个复杂任务：先搜索当前目录结构，然后分析每个文件的用途，最后生成一个总结报告。这是一个多步骤任务，可以拆分为多个子任务并行执行。",
    "sessionId": "test-valid-spawn-session"
  }'
```

**预期日志**:

```bash
# 查询该请求的日志
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?requestId=test-valid-spawn-001&date=2026-04-09"
```

**期望看到**:
- `[Facade] Spawn success: runId=run-xxx`
- `[Gatekeeper] Spawn started: sessionId=xxx, runId=run-xxx`
- `[LocalRuntime] Worker TraceContext: sessionId=xxx, runId=run-xxx`
- `[Gatekeeper] Spawn ended: sessionId=xxx, runId=run-xxx`

---

### 测试 2: 深度限制测试（Depth Limit）

**目的**: 验证 `maxSpawnDepth=3` 限制生效。

**请求**:

```bash
# 发送一个会触发深层嵌套的请求
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Request-Id: test-depth-limit-001" \
  -d '{
    "message": "请执行一个极度复杂的嵌套任务：主任务分解为 3 个子任务，每个子任务再分解为 2 个孙任务，孙任务再分解为曾孙任务。请展示完整的任务分解树。",
    "sessionId": "test-depth-limit-session"
  }'
```

**查询日志**:

```bash
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?requestId=test-depth-limit-001&date=2026-04-09"
```

**期望看到**:
- 前 3 层派生成功（depth=1, 2, 3）
- 第 4 层被拒绝：`[Gatekeeper] Depth limit exceeded: current=3, max=3`
- 返回给用户的拒绝信息：`Maximum spawn depth (3) reached`

---

### 测试 3: 并发限制测试（Concurrent Limit）

**目的**: 验证 `maxConcurrentSpawns=5` 限制生效。

**请求**:

```bash
# 并发发送 6 个请求
for i in {1..6}; do
  curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "X-Request-Id: test-concurrent-$i" \
    -d "{
      \"message\": \"请并行执行任务 $i，这是一个独立的任务。\",
      \"sessionId\": \"test-concurrent-session\"
    }" &
done
wait
```

**查询日志**:

```bash
# 查询该会话的所有日志
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?sessionId=test-concurrent-session&date=2026-04-09&size=100"
```

**期望看到**:
- 前 5 个并发请求成功：`[Facade] Spawn success: runId=run-xxx`
- 第 6 个请求被拒绝：`[Gatekeeper] Concurrent limit exceeded: current=5, max=5`
- 返回信息：`Too many concurrent subtasks (5/5)`

---

### 测试 4: 工具白名单测试（Tool Whitelist）

**目的**: 验证不在白名单的工具被拒绝。

**请求**:

```bash
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Request-Id: test-tool-whitelist-001" \
  -d '{
    "message": "请使用一个不存在的工具（如 dangerous_tool）来执行危险操作。",
    "sessionId": "test-tool-whitelist-session"
  }'
```

**查询日志**:

```bash
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?requestId=test-tool-whitelist-001&date=2026-04-09"
```

**期望看到**:
- `[Gatekeeper] Tool not in allowed scope: tool=dangerous_tool`
- 返回信息：`Tool 'dangerous_tool' is not available in this context`

---

### 测试 5: 父子关系追溯测试（Parent Run ID）

**目的**: 验证 `parentRunId` 字段正确记录和追溯。

**请求**:

```bash
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Request-Id: test-parent-run-001" \
  -d '{
    "message": "请执行一个会派生子任务的任务，然后我需要查询父子任务的关系链。",
    "sessionId": "test-parent-run-session"
  }'
```

**等待任务完成后，查询数据库或日志**:

```bash
# 通过日志查看父子关系
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?sessionId=test-parent-run-session&keyword=parentRunId&date=2026-04-09"

# 或者查询所有子 agent 相关日志
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?sessionId=test-parent-run-session&keyword=run&date=2026-04-09&size=200"
```

**期望看到**:
- 子任务的日志中包含父运行 ID 信息
- `Created run: runId=run-child, sessionId=xxx, parentRunId=run-parent`

---

### 测试 6: TTL 超时测试（Wall-Clock TTL）

**目的**: 验证 `wallclockTtlSeconds=180` 超时机制生效。

**请求**:

```bash
# 发送一个会长时间运行的任务
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Request-Id: test-ttl-timeout-001" \
  -d '{
    "message": "请执行一个需要 5 分钟以上的复杂分析任务（实际会被 TTL 截断）。",
    "sessionId": "test-ttl-timeout-session"
  }'
```

**等待 3-5 分钟后查询日志**:

```bash
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?requestId=test-ttl-timeout-001&date=2026-04-09"
```

**期望看到**:
- 任务开始：`[LocalRuntime] Worker TraceContext: sessionId=xxx, runId=run-xxx`
- 超时检测：`[Reconciler] Run timeout: runId=run-xxx, deadline=xxx`
- 状态更新：`Task exceeded Wall-Clock TTL during recovery`
- 最终状态：`TIMEOUT`

---

### 测试 7: Shadow 模式测试（Shadow Mode）

**目的**: 验证 Shadow 模式下子 Agent 不实际执行。

**前提**: 需要设置 `DEMO_MULTI_AGENT_MODE=shadow`

**请求**:

```bash
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Request-Id: test-shadow-001" \
  -d '{
    "message": "请派生子 Agent 执行这个任务。",
    "sessionId": "test-shadow-session"
  }'
```

**查询日志**:

```bash
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?requestId=test-shadow-001&date=2026-04-09"
```

**期望看到**:
- `[Facade] Shadow mode: evaluating spawn without execution`
- `[Metrics] Shadow mode spawn evaluated: reason=shadow_only`
- 无实际子 Agent 执行日志

---

## 自动化测试脚本

### Bash 测试脚本

```bash
#!/bin/bash

BASE_URL="https://ai-agent-server-production-d28a.up.railway.app"
DATE=$(date +%Y-%m-%d)

# 测试 1: API 连通性
echo "=== Test 1: API Connectivity ==="
response=$(curl -s -X GET "$BASE_URL/api/health")
echo "Health: $response"

# 测试 2: 基本派生
echo "=== Test 2: Valid Spawn ==="
request_id="test-valid-$(date +%s)"
curl -s -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: $request_id" \
  -d "{\"message\": \"请执行一个多步骤任务\", \"sessionId\": \"test-session\"}"

sleep 5

# 查询日志
echo "=== Query Logs ==="
curl -s -X GET "$BASE_URL/api/logs/chain?requestId=$request_id&date=$DATE"

echo "=== Tests Complete ==="
```

---

## 关键指标监控

### 通过日志统计指标

```bash
# 统计 spawn 成功次数
curl -s "$BASE_URL/api/logs/chain?keyword=Spawn+success&date=$DATE&size=1000" | jq '.data | length'

# 统计 spawn 被拒绝次数
curl -s "$BASE_URL/api/logs/chain?keyword=rejected&date=$DATE&size=1000" | jq '.data | length'

# 统计超时次数
curl -s "$BASE_URL/api/logs/chain?keyword=timeout&date=$DATE&size=1000" | jq '.data | length'

# 统计深度限制触发次数
curl -s "$BASE_URL/api/logs/chain?keyword=Depth+limit&date=$DATE&size=1000" | jq '.data | length'

# 统计并发限制触发次数
curl -s "$BASE_URL/api/logs/chain?keyword=Concurrent+limit&date=$DATE&size=1000" | jq '.data | length'
```

---

## Loki 闭环验收（生产推荐）

当服务部署在 Railway 且存在多实例/重启场景时，建议使用 Loki 作为日志查询主来源，避免本地 jsonl 漂移导致链路缺失。

### 1) 生产环境变量建议

```bash
# 日志查询来源：loki | file | auto
DEMO_LOG_QUERY_SOURCE=loki

# Loki 推送与查询配置
LOKI_URL=https://logs-prod-042.grafana.net/loki/api/v1/push
LOKI_USERNAME=<your_loki_user_id>
LOKI_PASSWORD=<your_loki_token>

# 可选：Loki 查询超时（秒）
DEMO_LOG_LOKI_TIMEOUT_SECONDS=15
```

说明：
- `loki`: 仅查 Loki（推荐生产）
- `file`: 仅查本地 jsonl
- `auto`: 优先 Loki，失败后回退文件

### 2) 闭环验证步骤

#### 步骤 A：发起带 requestId 的请求

```bash
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "Accept: application/json" \
  -H "X-Request-Id: loki-e2e-001" \
  -d '{
    "message": "请回复 LOG-OK",
    "sessionId": "loki-e2e-session"
  }'
```

#### 步骤 B：通过业务接口查询链路

```bash
curl -X GET "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?requestId=loki-e2e-001&date=2026-04-09&page=1&size=20"
```

#### 步骤 C：通过 Loki 原生接口交叉验证（可选）

```bash
curl -G "https://logs-prod-042.grafana.net/loki/api/v1/query_range" \
  -u "<LOKI_USERNAME>:<LOKI_PASSWORD>" \
  --data-urlencode 'query={service_name=~".+"} |= "loki-e2e-001"' \
  --data-urlencode "start=1775730000000000000" \
  --data-urlencode "end=1775739999000000000" \
  --data-urlencode "limit=20" \
  --data-urlencode "direction=backward"
```

### 3) 验收标准

- `POST /api/chat` 返回 200（或业务成功响应）
- `GET /api/logs/chain?requestId=...` 返回 `success=true` 且 `total > 0`
- （可选）Loki 原生查询可命中同一 `requestId`

### 4) 常见异常与处理

- `total=0`：
  - 确认 `DEMO_LOG_QUERY_SOURCE=loki`
  - 确认 `LOKI_*` 三个变量完整且 token 有 `logs:write` 权限
  - 检查请求是否真实到达服务（先看 `/api/chat` 响应）
- `502 Application failed to respond`：
  - 常见于 Railway 滚动部署窗口，等待实例恢复后重试
- 关键词查不到但 requestId 可查到：
  - 优先使用 `requestId` 精确检索，关键词用于辅助排查

---

## 故障排查

### 常见问题

1. **日志为空**:
   - 确认日期参数正确（yyyy-MM-dd 格式）
   - 确认时区匹配（服务端可能使用 UTC）
   - 检查子 Agent 功能是否启用

2. **子 Agent 未触发**:
   - 检查 `DEMO_MULTI_AGENT_ENABLED=true`
   - 检查 prompt 是否能触发子 Agent 派生
   - 查看模型请求日志确认 prompt 传递

3. **门控未生效**:
   - 检查 `SpawnGatekeeper` 是否正确注入
   - 确认 `MultiAgentFacade` 调用 `checkAndAcquire()`

---

## 附录：关键日志关键词

| 关键词 | 说明 |
|--------|------|
| `Spawn success` | 子 Agent 派生成功 |
| `Spawn rejected` | 子 Agent 派生被拒绝 |
| `Depth limit exceeded` | 深度限制触发 |
| `Concurrent limit exceeded` | 并发限制触发 |
| `Tool not in allowed scope` | 工具白名单拒绝 |
| `Wall-Clock TTL` | 超时检测 |
| `parentRunId` | 父子关系追溯 |
| `Shadow mode` | 影子模式评估 |
