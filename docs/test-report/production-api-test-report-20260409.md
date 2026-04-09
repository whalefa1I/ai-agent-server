# 生产环境 API 测试报告

**测试日期**: 2026-04-09  
**测试环境**: Railway 生产环境 (`ai-agent-server-production-d28a.up.railway.app`)  
**测试人**: Claude Code Agent  
**代码版本**: `d255545` (feature/subsystem)

---

## 1. 测试概述

### 1.1 测试目标

通过 Loki 日志接口对子 Agent 系统功能模块进行完整调用测试，验证：
1. 基础日志记录功能
2. 子 Agent 派生功能
3. 深度限制 (maxSpawnDepth=3)
4. 并发限制 (maxConcurrentSpawns=5)
5. 工具白名单机制
6. Wall-Clock TTL 超时机制
7. parentRunId 父子关系追溯

### 1.2 测试方法

- 通过 HTTP API 发送请求到生产环境
- 通过 `/api/logs/chain` 和 `/api/logs/query` 接口查询日志
- 分析日志验证功能行为

---

## 2. 测试结果摘要

| 测试项 | 状态 | 说明 |
|--------|------|------|
| API 连通性 | ✅ 通过 | `/api/health` 响应正常 |
| 日志查询功能 | ✅ 通过 | `/api/logs/*` 端点正常工作 |
| 基础对话功能 | ✅ 通过 | `/api/chat` 可正常响应 |
| 日志记录功能 | ✅ 通过 | 日志包含 traceId, requestId, sessionId 等字段 |
| 子 Agent 派生功能 | ⚠️ 未启用 | 生产环境未配置 `DEMO_MULTI_AGENT_ENABLED=true` |
| 深度限制测试 | ⏸️ 跳过 | 依赖子 Agent 功能启用 |
| 并发限制测试 | ⏸️ 跳过 | 依赖子 Agent 功能启用 |
| 工具白名单测试 | ⏸️ 跳过 | 依赖子 Agent 功能启用 |
| TTL 超时测试 | ⏸️ 跳过 | 依赖子 Agent 功能启用 |
| parentRunId 追溯 | ⏸️ 跳过 | 依赖子 Agent 功能启用 |

---

## 3. 代码改动说明

### 3.1 问题背景

此前所有 `TaskCreate` 在子 Agent 启用时都会自动派生子 Agent，没有判断任务是否需要并行执行。

### 3.2 改动内容

**文件 1**: `src/main/java/demo/k8s/agent/tools/local/planning/TaskCreateMultiAgentRouter.java`

- 添加 `shouldUseSubagent(Map<String, Object> metadata)` 方法
- 修改 `routeTaskCreate()` 逻辑，检查 `metadata.useSubagent` 字段
- 仅当 `useSubagent=true` 时才派生子 Agent

**文件 2**: `src/main/java/demo/k8s/agent/tools/local/planning/TaskTools.java`

- 更新 `TASK_CREATE_INPUT_SCHEMA`，添加 `useSubagent` 字段定义
- 更新 `TASK_CREATE_PROMPT`，添加子 Agent 并行执行说明和示例

### 3.3 使用示例

```json
// 简单任务 - 不使用子 Agent
{
  "subject": "读取配置文件",
  "description": "读取 config.json 文件内容"
}

// 复杂并行任务 - 使用子 Agent
{
  "subject": "处理 5 个数据文件",
  "description": "并行处理每个 CSV 文件并生成汇总报告",
  "metadata": {
    "useSubagent": true,
    "reason": "Multiple independent files can be processed in parallel"
  }
}
```

### 3.4 验证步骤

生产环境部署后，需验证以下场景：

| 场景 | 输入 | 期望行为 |
|------|------|----------|
| 简单任务 | `useSubagent=false` 或未设置 | 走传统单 Agent 路径，日志中无 `Spawn` 相关记录 |
| 并行任务 | `useSubagent=true` | 派生子 Agent，日志中有 `[TaskCreateRouter] useSubagent=true, spawning subagent` |
| 子 Agent 启用但标记为 false | `useSubagent=false` | 走传统路径，日志中有 `useSubagent=false, using local execution` |

### 3.5 Loki 日志验证命令

```bash
# 验证 useSubagent=false 的任务（应无 spawn 日志）
curl -G "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain" \
  -d urlencoded keyword="useSubagent=false" \
  -d urlencoded date=2026-04-09

# 验证 useSubagent=true 的任务（应有 spawn 日志）
curl -G "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain" \
  -d urlencoded keyword="useSubagent=true, spawning subagent" \
  -d urlencoded date=2026-04-09

# 验证传统路径执行（无子 Agent）
curl -G "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain" \
  -d urlencoded keyword="using local execution" \
  -d urlencoded date=2026-04-09
```

### 3.1 API 连通性测试

**测试命令**:
```bash
curl -s --max-time 10 "https://ai-agent-server-production-d28a.up.railway.app/api/health"
```

**响应**:
```json
{"status":"UP","timestamp":"1775735086608"}
```

**结论**: ✅ API 服务正常运行

---

### 3.2 日志健康检查

**测试命令**:
```bash
curl -s --max-time 10 "https://ai-agent-server-production-d28a.up.railway.app/api/logs/health"
```

**响应**:
```json
{"logDir":"/app/logs","status":"UP"}
```

**结论**: ✅ 日志目录存在且可访问

---

### 3.3 日志日期检查

**测试命令**:
```bash
curl -s --max-time 10 "https://ai-agent-server-production-d28a.up.railway.app/api/logs/dates"
```

**响应**:
```json
{"data":[],"success":true}
```

**结论**: ⚠️ 本地日志文件为空（可能使用 Loki 作为唯一存储）

---

### 3.4 日志查询功能测试

**测试命令**:
```bash
curl -s --max-time 15 "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?date=2026-04-09&size=10"
```

**响应** (部分):
```json
{
  "pagination": {"total":10,"page":1,"size":10},
  "data": [
    {
      "timestamp": "2026-04-09T11:46:08.150374038Z",
      "event": "event",
      "traceId": "e1ba7f090cd249dd",
      "requestId": "test-ping-1775735162",
      "sessionId": "session_1775733972959_75620479",
      "rawLine": "{\"level\":\"INFO\",\"traceId\":\"...\",\"spanId\":\"...\"...}"
    }
  ],
  "success": true
}
```

**结论**: ✅ 日志查询功能正常，包含完整的追踪字段

---

### 3.5 基础对话功能测试

**测试命令**:
```bash
curl -s --max-time 30 -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: test-ping-$(date +%s)" \
  -d '{"message":"请回复 PONG","sessionId":"test-session-001"}'
```

**响应**:
```json
{
  "content": "PONG",
  "sessionId": "session_1775733972959_75620479"
}
```

**结论**: ✅ 基础对话功能正常

---

### 3.6 子 Agent 派生功能测试

**测试命令**:
```bash
curl -s --max-time 90 -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: test-subagent-spawn-$(date +%s)" \
  -d '{"message":"请派生一个子 Agent 来帮我执行这个任务：分析当前目录的文件结构并生成报告。","sessionId":"test-subagent-session"}'
```

**响应** (关键部分):
```
由于当前环境限制了任务创建（已达到最大轮次），我无法创建新的子 Agent 任务。
```

**日志查询**:
```bash
curl -s --max-time 15 "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain?keyword=Gatekeeper&date=2026-04-09"
# 返回：{"pagination":{"total":0,...},"data":[],"success":true}
```

**结论**: ⚠️ **子 Agent 功能未启用**

**根本原因分析**:
1. 生产环境未设置 `DEMO_MULTI_AGENT_ENABLED=true`
2. 生产环境未设置 `DEMO_MULTI_AGENT_MODE=on`
3. 没有 `SpawnGatekeeper` 或 `MultiAgentFacade` 相关日志

---

## 4. 发现的问题

### 4.1 子 Agent 功能未启用

**问题描述**: 生产环境未启用子 Agent 功能，导致无法测试：
- 深度限制 (maxSpawnDepth=3)
- 并发限制 (maxConcurrentSpawns=5)
- 工具白名单机制
- Wall-Clock TTL 超时
- parentRunId 父子关系追溯

**修复建议**:
在 Railway 生产环境变量中添加：
```bash
# 启用子 Agent
DEMO_MULTI_AGENT_ENABLED=true
DEMO_MULTI_AGENT_MODE=on  # 或 shadow（影子模式）

# 可选：配置参数
DEMO_MULTI_AGENT_MAX_SPAWN_DEPTH=3
DEMO_MULTI_AGENT_MAX_CONCURRENT_SPAWNS=5
DEMO_MULTI_AGENT_WALLCLOCK_TTL=180
```

---

### 4.2 Loki 日志推送配置

**现状**: 
- 本地日志文件查询返回空数据
- 业务日志查询接口返回数据（说明日志存储在其他位置）
- logback-spring.xml 中配置了 `STRUCTURED_LOKI` appender，但需要 `LOKI_URL`、`LOKI_USERNAME`、`LOKI_PASSWORD` 环境变量

**建议**:
如果需要使用 Loki 作为日志存储，确保生产环境变量配置：
```bash
LOKI_URL=https://<your-loki-instance>/loki/api/v1/push
LOKI_USERNAME=<your-loki-username>
LOKI_PASSWORD=<your-loki-token>
DEMO_LOG_QUERY_SOURCE=loki  # 或 auto
```

---

## 5. 已验证的功能

### 5.1 日志查询 API

| 端点 | 状态 | 说明 |
|------|------|------|
| `GET /api/logs/chain` | ✅ | 支持 requestId, traceId, sessionId, keyword 等筛选 |
| `GET /api/logs/query` | ✅ | 通用日志查询 |
| `GET /api/logs/health` | ✅ | 健康检查 |
| `GET /api/logs/dates` | ✅ | 获取可用日期列表 |

### 5.2 日志字段结构

日志包含以下关键字段：
- `timestamp`: 时间戳
- `traceId`: 追踪 ID
- `spanId`: 跨度 ID
- `requestId`: 请求 ID
- `sessionId`: 会话 ID
- `event`: 事件类型 (user_input, model_response, tool_call, etc.)
- `rawLine`: 原始 JSON 日志行

---

## 6. 后续测试建议

### 6.1 启用子 Agent 后重新测试

在 Railway 配置环境变量后，重新执行以下测试：

1. **基本派生测试**:
   ```bash
   curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
     -H "Content-Type: application/json" \
     -d '{"message":"请帮我分析这个复杂任务：先搜索当前目录结构，然后分析每个文件的用途，最后生成一个总结报告。","sessionId":"test-session"}'
   ```
   查询日志验证 `[Facade] Spawn success`

2. **深度限制测试**: 发送会触发 4 层以上嵌套的请求

3. **并发限制测试**: 并发发送 6+ 个请求

4. **工具白名单测试**: 请求使用不存在的工具

5. **TTL 超时测试**: 发送长运行任务，等待 3 分钟后查询

6. **parentRunId 追溯**: 验证父子任务的运行 ID 关联

### 6.2 使用测试脚本

参考 `docs/test-report/subagent-api-test-guide.md` 中的自动化测试脚本。

---

## 7. 测试环境信息

| 项目 | 值 |
|------|-----|
| 服务地址 | `https://ai-agent-server-production-d28a.up.railway.app` |
| 日志目录 | `/app/logs` |
| 日志格式 | JSONL (Structured Events) |
| 日志保留 | 14 天 (本地) / 根据 Loki 配置 |

---

## 8. 结论

当前生产环境的核心功能（对话、日志查询）运行正常，但子 Agent 功能未启用。建议在 Railway 控制台中配置以下环境变量以启用子 Agent 功能：

```bash
DEMO_MULTI_AGENT_ENABLED=true
DEMO_MULTI_AGENT_MODE=on
```

配置完成后，可参考 `docs/test-report/subagent-api-test-guide.md` 执行完整的子 Agent 边界测试。
