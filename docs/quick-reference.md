# minimal-k8s-agent-demo 快速参考

## API 端点一览

### 对话端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 有状态对话（集成权限检查 + Token 追踪） |
| `/api/chat/agentic` | POST | 原始 AgenticQueryLoop（无状态） |
| `/api/chat/stream` | POST | SSE 流式对话 |
| `/api/chat/history` | GET | 获取历史消息 |
| `/api/chat/files/{path}/history` | GET | 获取文件修改历史 |
| `/api/chat/stats` | GET | 获取会话统计 |

### 权限管理

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/permissions/pending` | GET | 获取待确认权限请求 |
| `/api/permissions/respond` | POST | 提交用户响应 |
| `/api/permissions/stream` | GET | SSE 推送权限请求 |
| `/api/permissions/grants` | GET | 获取会话授权 |
| `/api/permissions/always-allowed` | GET | 获取始终允许的工具 |
| `/api/permissions/revoke` | POST | 撤销始终允许授权 |

### 可观测性

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/observability/stats` | GET | 会话统计摘要 |
| `/api/observability/metrics` | GET | Prometheus 指标 |
| `/api/observability/tool-calls` | GET | 工具调用历史 |
| `/api/observability/model-calls` | GET | 模型调用历史 |

---

## 工具调用

### task - 委派子 Agent

```json
{
  "name": "代码分析",
  "goal": "分析项目结构并生成文档",
  "subagent_type": "explore",
  "run_in_background": false
}
```

### send_message - 发送消息给任务

```json
{
  "task_id": "task_123456",
  "message": "请优先查看 src/main 目录"
}
```

### task_stop - 停止任务

```json
{
  "task_id": "task_123456"
}
```

---

## 权限等级

| 等级 | 说明 | 示例工具 |
|------|------|----------|
| READ_ONLY | 只读操作 | Read, Grep, Glob |
| MODIFY_STATE | 修改状态 | Write, Edit |
| NETWORK | 网络访问 | Fetch, BraveSearch |
| DESTRUCTIVE | 破坏性操作 | Bash (危险命令), DeleteFile |
| AGENT_SPAWN | 子代理 | Task, run_in_background |

---

## 用户响应选择

| 选择 | 说明 | 有效期 |
|------|------|--------|
| ALLOW_ONCE | 本次允许 | 5 分钟 |
| ALLOW_SESSION | 会话允许 | 30 分钟 |
| ALLOW_ALWAYS | 始终允许 | 永久 |
| DENY | 拒绝 | - |

---

## 任务状态

```
PENDING → RUNNING → (COMPLETED | FAILED | STOPPED)
                 → WAITING (等待用户输入)
```

---

## Worker Agent 类型

| 类型 | 专属工具集 | 系统提示 |
|------|-----------|----------|
| general | 所有工具 | 通用助手 |
| bash | Bash 相关 | Shell 专家 |
| explore | 只读工具 | 代码探索专家 |
| plan | 规划类 | 规划专家 |
| edit | 文件编辑 | 代码编辑专家 |

---

## 配置项

### application.yml

```yaml
# 权限模式
demo:
  tools:
    permission-mode: default  # default | read_only | bypass

# 查询参数
demo:
  query:
    max-turns: 32
    full-compact-enabled: false

# K8s
demo:
  k8s:
    enabled: false
    namespace: default

# Coordinator 模式
demo:
  coordinator:
    enabled: false  # true 时仅暴露 Task/SendMessage/TaskStop
```

### 环境变量

```bash
# API 配置
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o-mini

# 功能开关
DEMO_TOOLS_PERMISSION_MODE=default
DEMO_QUERY_MAX_TURNS=32
DEMO_K8S_ENABLED=false
DEMO_COORDINATOR_MODE=false
```

---

## 错误处理

### 权限拒绝

```json
{
  "error": "工具调用被用户拒绝",
  "tool_name": "Write"
}
```

### 超时

```json
{
  "error": "等待用户确认超时",
  "timeout_ms": 300000
}
```

### 任务失败

```json
{
  "error": "任务失败",
  "task_id": "task_123456",
  "reason": "model_error: API timeout"
}
```

---

## 会话管理

### 会话 ID 格式

```
session_<timestamp>_<8-char-uuid>
示例：session_1712131200000_abc12345
```

### 任务 ID 格式

```
task_<timestamp>_<8-char-uuid>
示例：task_1712131200000_xyz67890
```

### 文件快照位置

```
~/.claude/sessions/<session-id>_files_<sanitized-path>.json
```

---

## 最佳实践

### 1. 权限确认流程

```
1. 用户发起请求
2. 模型返回工具调用
3. 系统检查权限等级
4. 如需确认，推送 SSE 到前端
5. 用户在前端选择响应
6. 后端执行或拒绝工具调用
```

### 2. 多 Agent 协作

```
1. 主 Agent 接收任务
2. 使用 task 工具委派子任务
3. 子 Agent 后台执行
4. 主 Agent 使用 send_message 跟进
5. 子 Agent 完成后返回结果
6. 主 Agent 整合结果并回复
```

### 3. 会话恢复

```
1. 从 ~/.claude/sessions/ 加载历史
2. 反序列化 ChatMessage 列表
3. 恢复 FileSnapshot 索引
4. 继续对话
```

---

## 故障排查

### 权限确认不弹出

1. 检查 `demo.tools.permission-mode` 是否为 `default`
2. 确认工具是否被标记为 `isReadOnly()`
3. 查看日志：`PermissionManager.requiresPermission`

### Token 计数为 0

1. 确认 API 响应包含 usage 字段
2. 检查 `EnhancedAgenticQueryLoop.extractTokenCounts`
3. 验证模型是否支持 token 统计

### 子 Agent 无法启动

1. 检查 `AsyncSubagentExecutor` Bean 是否注入
2. 确认 `@EnableAsync` 已添加到配置
3. 查看日志：`WorkerAgentExecutor.executeWorker`

---

## 性能优化建议

1. **提示缓存**: 对重复的系统提示使用 Prompt Caching
2. **工具预加载**: 启动时预加载常用工具
3. **会话清理**: 定期清理过期的 sessions 文件
4. **连接池**: 对 KubernetesClient 使用连接池

---

## 安全建议

1. **权限最小化**: 默认使用 `read_only` 模式
2. **审计日志**: 记录所有工具调用
3. **速率限制**: 对 API 端点添加限流
4. **输入校验**: 严格校验工具输入参数
5. **镜像白名单**: K8s Job 使用固定镜像版本
