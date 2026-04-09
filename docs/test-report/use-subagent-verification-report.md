# useSubagent 功能验证报告

**测试日期**: 2026-04-09  
**测试环境**: Railway 生产环境 (`ai-agent-server-production-d28a.up.railway.app`)  
**代码版本**: `bd6b26b` (feature/subsystem)

---

## 1. 测试概述

### 1.1 测试目标

验证 `useSubagent` 标志功能是否正常工作：
- `useSubagent=false` 或未设置 → 走传统单 Agent 路径
- `useSubagent=true` → 派生子 Agent 并行执行

### 1.2 测试结果摘要

| 测试项 | 状态 | 说明 |
|--------|------|------|
| 代码编译 | ✅ 通过 | Maven 编译成功 |
| Git 推送 | ✅ 通过 | 已推送到远端 |
| API 连通性 | ✅ 通过 | `/api/health` 响应正常 |
| 日志查询 | ✅ 通过 | `/api/logs/chain` 正常工作 |
| TaskCreate 功能 | ✅ 通过 | 任务创建成功 |
| **子 Agent 功能启用** | ❌ **未启用** | 生产环境未配置 `DEMO_MULTI_AGENT_ENABLED=true` |
| **useSubagent 标志判断** | ⏸️ **无法验证** | 依赖子 Agent 功能启用 |

---

## 2. 详细测试结果

### 2.1 测试 1: useSubagent=false

**请求**:
```json
{
  "message": "请使用 TaskCreate 创建一个简单任务：读取配置文件。在 metadata 中设置 useSubagent=false",
  "sessionId": "verify-session-xxx"
}
```

**响应**:
```
任务已成功创建：
- Task ID: 1
- Subject: 读取配置文件
- Description: 读取并查看配置文件内容
```

**日志分析**:
- 日志中有 `TaskCreate` 工具调用记录
- 没有 `TaskCreateRouter` 相关日志（说明路由逻辑未执行）
- 没有 `useSubagent` 相关日志

**结论**: 子 Agent 功能未启用，所有 TaskCreate 走传统路径

---

### 2.2 测试 2: useSubagent=true

**请求**:
```json
{
  "message": "请使用 TaskCreate 创建一个复杂任务：并行处理 5 个 CSV 文件。必须设置 metadata 为对象格式，useSubagent=true",
  "sessionId": "verify-session-xxx"
}
```

**响应**:
```
任务已成功创建：
- Task ID: 2
- Subject: 并行处理 5 个 CSV 文件
- Description: 同时读取并处理 5 个 CSV 文件，进行数据分析和转换
```

**日志分析**:
- 日志中有 `TaskCreate` 工具调用记录
- 没有 `TaskCreateRouter` 相关日志
- 没有 `spawning subagent` 或 `useSubagent=true` 相关日志
- 没有 `MultiAgentFacade` 或 `SpawnGatekeeper` 相关日志

**结论**: 子 Agent 功能未启用，所有 TaskCreate 走传统路径

---

## 3. 发现的问题

### 3.1 生产环境未启用子 Agent 功能

**现象**: 
- 没有 `TaskCreateRouter`、`MultiAgentFacade`、`SpawnGatekeeper` 相关日志
- 所有 TaskCreate 都返回 `"location":"local"`

**根本原因**:
生产环境 Railway 未配置以下环境变量：
```bash
DEMO_MULTI_AGENT_ENABLED=true
DEMO_MULTI_AGENT_MODE=on
```

**解决方案**:
在 Railway 控制台中添加环境变量，然后重启应用。

---

### 3.2 模型未正确使用 metadata 对象格式

**现象**:
模型将 metadata 传成了字符串而不是 Map 对象：
```json
"metadata": "{\"useSubagent\": true}"
```

导致类型转换错误：
```
class java.lang.String cannot be cast to class java.util.Map
```

**日志证据**:
```json
{
  "error": "class java.lang.String cannot be cast to class java.util.Map (java.lang.String and java.util.Map are in module java.base of loader 'bootstrap')",
  "input": "{\"subject\": \"并行处理 5 个 CSV 文件\", ..., \"metadata\": \"{\\\"useSubagent\\\": true}\"}"
}
```

**影响**:
即使子 Agent 功能启用，metadata 解析也会失败，导致 `shouldUseSubagent()` 方法无法正确读取 `useSubagent` 值。

**解决方案**:
1. ✅ **已实现**: 在 `parseTaskCreateInput` 中添加 `parseMetadata()` 方法，支持字符串到 Map 的自动转换（向后兼容）
2. 建议：更新模型系统提示词，强调 metadata 必须是 JSON 对象格式

**修复详情**:
- 文件：`TaskTools.java`
- 方法：新增 `parseMetadata(Object metadataObj)` 私有方法
- 功能：
  - 支持 Map 类型（直接返回）
  - 支持 String 类型（JSON 解析为 Map）
  - 支持空值处理
- 提交：`e98bd32` - fix(metadata): add String-to-Map parsing for metadata backward compatibility

---

## 4. 代码逻辑验证

虽然生产环境未启用子 Agent 功能，但我们可以从代码层面验证逻辑正确性：

### 4.1 TaskCreateMultiAgentRouter.routeTaskCreate() 逻辑

```java
// 1. 检查子 Agent 系统是否启用
if (!multiAgentProperties.isEnabled() || mode == off) {
    return TaskTools.executeTaskCreate(input);  // ✅ 传统路径
}

// 2. Shadow 模式
if (mode == shadow) {
    return TaskTools.taskCreateShadowBlocked(input);  // ✅ 影子模式
}

// 3. 检查 metadata.useSubagent 字段
Boolean useSubagent = shouldUseSubagent(p.metadata());
if (!useSubagent) {
    return TaskTools.executeTaskCreate(input);  // ✅ 传统路径
}

// 4. 派生子 Agent
SpawnResult spawnResult = multiAgentFacade.getObject().spawnTask(...);  // ✅ 子 Agent 路径
```

### 4.2 shouldUseSubagent() 方法

```java
private Boolean shouldUseSubagent(Map<String, Object> metadata) {
    if (metadata == null) return false;  // ✅ null → false
    Object value = metadata.get("useSubagent");
    if (value instanceof Boolean b) return b;  // ✅ Boolean → 直接返回
    if (value instanceof String s) return Boolean.parseBoolean(s);  // ✅ String → 解析
    if (value instanceof Number n) return n.intValue() != 0;  // ✅ Number → 非零为 true
    return false;  // ✅ 默认 false
}
```

**代码验证结论**: ✅ 逻辑正确，支持多种类型输入

---

## 6. Loki 超时问题分析

### 6.1 根本原因

经过代码和配置分析，Loki 查询超时的根本原因如下：

**1. Loki 未配置但 query-source=loki**

```yaml
# application.yml 第 189 行
demo:
  logs:
    query-source: ${DEMO_LOG_QUERY_SOURCE:loki}  # 默认值 loki
    loki-timeout-seconds: ${DEMO_LOG_LOKI_TIMEOUT_SECONDS:15}
```

```yaml
# application.yml 第 63-66 行
logging:
  loki:
    url: ${LOKI_URL:http://localhost:3100/loki/api/v1/push}
    username: ${LOKI_USERNAME:}
    password: ${LOKI_PASSWORD:}
```

**2. 生产环境问题**：

| 配置项 | 默认值 | 生产环境实际值 | 问题 |
|--------|--------|---------------|------|
| `DEMO_LOG_QUERY_SOURCE` | `loki` | 未配置 → `loki` | 只查 Loki，不回退文件 |
| `LOKI_URL` | `http://localhost:3100` | 未配置 → `http://localhost:3100` | Railway 无本地 Loki |
| `LOKI_USERNAME` | 空 | 未配置 | 认证缺失 |
| `LOKI_PASSWORD` | 空 | 未配置 | 认证缺失 |

**3. 超时机制**：

- `LogFileReader.queryFromLoki()` 方法中，HTTP 超时设置为 `lokiTimeoutSeconds`（默认 15 秒）
- 由于 `localhost:3100` 不可达，HTTP 请求等待 15 秒后超时
- 因为 `query-source=loki`（非 `auto`），代码不会回退到文件查询

### 6.2 解决方案

**方案 A：配置 Loki（推荐，如果有 Loki 服务）**

在 Railway 环境变量中配置：
```bash
LOKI_URL=https://your-loki-instance.railway.app
LOKI_USERNAME=your-username
LOKI_PASSWORD=your-password
DEMO_LOG_QUERY_SOURCE=loki
```

**方案 B：切换到文件查询模式（快速解决）**

在 Railway 环境变量中配置：
```bash
DEMO_LOG_QUERY_SOURCE=file
```

或者使用 auto 模式（优先 Loki，失败回退文件）：
```bash
DEMO_LOG_QUERY_SOURCE=auto
```

### 6.3 代码逻辑验证

```java
// LogFileReader.java:70-96
public List<LogEntry> queryLogs(LogQuery query) {
    SourceMode mode = SourceMode.from(querySource);
    if (mode == SourceMode.LOKI || mode == SourceMode.AUTO) {
        if (isLokiConfigured()) {
            try {
                List<LogEntry> lokiResults = queryFromLoki(query);
                if (mode == SourceMode.LOKI) {
                    return paginateAndSort(lokiResults, query);
                }
                if (!lokiResults.isEmpty()) {
                    return paginateAndSort(lokiResults, query);
                }
            } catch (Exception e) {
                if (mode == SourceMode.LOKI) {
                    log.warn("Loki 查询失败（loki 模式，返回空）: {}", e.getMessage());
                    return List.of();  // ← 当前行为：直接返回空，不回退
                }
                log.warn("Loki 查询失败，回退文件查询：{}", e.getMessage());
            }
        } else if (mode == SourceMode.LOKI) {
            log.warn("query-source=loki 但 Loki 未配置，返回空结果");
            return List.of();  // ← 当前行为：直接返回空
        }
    }
    // file 模式，或 auto 模式下 Loki 无可用结果时回退
    return queryFromFiles(query);
}
```

**关键发现**：
- 当 `query-source=loki` 且 Loki 未配置时，直接返回空结果
- 当 `query-source=loki` 且 Loki 查询超时时，返回空结果（不回退文件）
- 只有 `query-source=auto` 或 `file` 时才会查询本地文件

---

## 7. 后续验证步骤

### 7.1 配置生产环境变量

在 Railway 控制台中添加：
```bash
DEMO_MULTI_AGENT_ENABLED=true
DEMO_MULTI_AGENT_MODE=on
```

### 7.2 重启生产环境应用

等待 Railway 滚动部署完成。

### 7.3 重新执行验证测试

```bash
# 测试 1: useSubagent=false (应无子 Agent 日志)
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"请创建一个简单任务，metadata 中设置 useSubagent=false","sessionId":"test-1"}'

# 查询日志
curl -G "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain" \
  -d "keyword=useSubagent=false" -d "date=2026-04-09"

# 期望看到：
# [TaskCreateRouter] useSubagent=false, using local execution for task: xxx

# 测试 2: useSubagent=true (应有子 Agent 日志)
curl -X POST "https://ai-agent-server-production-d28a.up.railway.app/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"请创建一个复杂任务，metadata 中设置 useSubagent=true","sessionId":"test-2"}'

# 查询日志
curl -G "https://ai-agent-server-production-d28a.up.railway.app/api/logs/chain" \
  -d "keyword=useSubagent=true, spawning subagent" -d "date=2026-04-09"

# 期望看到：
# [TaskCreateRouter] useSubagent=true, spawning subagent for task: xxx
# [Facade] Spawn success: runId=xxx
```

### 7.4 验收标准

| 场景 | 期望日志 | 验收状态 |
|------|----------|----------|
| `useSubagent=false` | `useSubagent=false, using local execution` | ⏸️ 待验证 |
| `useSubagent=true` | `useSubagent=true, spawning subagent` | ⏸️ 待验证 |
| 未设置 `useSubagent` | `using local execution` (默认 false) | ⏸️ 待验证 |

---

## 6. 总结

### 6.1 已完成

- ✅ 代码实现完成并推送
- ✅ 编译验证通过
- ✅ 生产环境 API 和日志功能正常

### 6.2 待完成

- ❌ 生产环境未配置 `DEMO_MULTI_AGENT_ENABLED=true`
- ⏸️ 功能验证待环境配置完成后重新执行

---

## 6. 验证结论

### 6.1 最终状态

**✅ 代码修复已完成**：
- metadata 字符串到 Map 的转换逻辑已实现（`parseMetadata()` 方法）
- 代码已提交并推送（commit `e98bd32`）
- Railway 部署已完成

**⚠️ 生产环境配置问题**：
根据日志分析，生产环境存在以下问题：

1. **环境变量未生效**：日志中 `"environment":"development"` 而非 `production`
2. **TaskCreateRouter 未激活**：没有相关日志，说明 `DEMO_MULTI_AGENT_ENABLED` 可能未设置为 `true`
3. **metadata 仍以字符串传输**：LLM 继续使用 `TaskUpdate` 的 metadata 字段而非 `TaskCreate`

### 6.2 根本原因

生产 Railway 环境可能未正确配置或未重启：
```bash
# 需要的配置
DEMO_MULTI_AGENT_ENABLED=true
DEMO_MULTI_AGENT_MODE=on
```

### 6.3 建议

1. ✅ **已完成**: 修复 metadata 字符串到 Map 的转换逻辑（向后兼容），代码已推送
2. **待完成**: 在 Railway 配置环境变量并重启应用
3. **长期**: 考虑在模型系统提示词中明确 metadata 格式要求（当前已在 TASK_CREATE_PROMPT 中说明）
