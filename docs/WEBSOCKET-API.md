# WebSocket & HTTP API 协议文档

> 版本：2.0.0  
> 最后更新：2026-04-04

本文档描述了 `minimal-k8s-agent-demo` 项目的前端集成协议，包括 WebSocket 实时通信和 HTTP REST API。

---

## 目录

- [快速开始](#快速开始)
- [WebSocket 协议](#websocket-协议)
- [HTTP REST API](#http-rest-api)
- [消息类型参考](#消息类型参考)
- [权限确认流程](#权限确认流程)
- [前端集成示例](#前端集成示例)

---

## 快速开始

### 连接方式

| 客户端类型 | 连接方式 | 端点 |
|-----------|---------|------|
| WebSocket | `ws://localhost:8080/ws/agent/{token}` | 实时双向通信 |
| HTTP REST | `http://localhost:8080/api/v2/*` | 轮询/简单客户端 |
| SSE | `http://localhost:8080/api/v2/chat/stream` | 流式输出 |

### 认证

生产环境下需要 Token 认证：

```bash
# 1. 生成 Token
TOKEN=$(curl -X POST http://localhost:8080/api/ws/token | jq -r '.token')

# 2. WebSocket 连接
ws://localhost:8080/ws/agent/$TOKEN

# 3. HTTP 请求（可选：通过 Header 传递）
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v2/chat
```

开发环境下可跳过认证。

---

## WebSocket 协议

### 连接建立

```typescript
const ws = new WebSocket('ws://localhost:8080/ws/agent');

ws.onopen = () => {
  console.log('已连接');
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  handleMessage(msg);
};
```

### 服务端 → 客户端消息

#### 1. ConnectedMessage - 连接确认

```json
{
  "type": "CONNECTED",
  "sessionId": "ws_abc123",
  "serverVersion": "2.0.0",
  "timestamp": "2026-04-04T10:00:00Z"
}
```

#### 2. ResponseStartMessage - 响应开始

```json
{
  "type": "RESPONSE_START",
  "requestId": "req_1234567890",
  "turnId": "turn_abc",
  "timestamp": "2026-04-04T10:00:01Z"
}
```

#### 3. TextDeltaMessage - 文本增量（流式）

```json
{
  "type": "TEXT_DELTA",
  "delta": "你好",
  "timestamp": "2026-04-04T10:00:02Z"
}
```

#### 4. ToolCallMessage - 工具调用通知

```json
{
  "type": "TOOL_CALL",
  "toolCallId": "tc_1234567890",
  "toolName": "read_file",
  "toolDisplayName": "Read File",
  "icon": "📄",
  "input": { "path": "/tmp/test.txt" },
  "inputDisplay": "读取文件 /tmp/test.txt",
  "status": "started",
  "timestamp": "2026-04-04T10:00:03Z"
}
```

**状态说明**：
- `started`: 工具调用开始
- `in_progress`: 执行中（长时间任务）
- `completed`: 执行完成
- `failed`: 执行失败

#### 5. PermissionRequestMessage - 权限请求

```json
{
  "type": "PERMISSION_REQUEST",
  "id": "perm_1234567890_abc",
  "toolName": "write_file",
  "toolDisplayName": "Write File",
  "toolDescription": "写入文件到指定路径",
  "icon": "✏️",
  "level": "MODIFY_STATE",
  "levelLabel": "修改状态",
  "levelIcon": "✏️",
  "levelColor": "#f59e0b",
  "inputSummary": "{\"path\":\"/tmp/test.txt\",\"content\":\"hello\"}",
  "riskExplanation": "此操作将修改文件系统状态，建议确认修改内容。",
  "permissionOptions": [
    { "value": "ALLOW_ONCE", "label": "本次允许", "style": "default", "shortcut": "1" },
    { "value": "ALLOW_SESSION", "label": "会话允许", "style": "primary", "shortcut": "2" },
    { "value": "ALLOW_ALWAYS", "label": "始终允许", "style": "primary", "shortcut": "3" },
    { "value": "DENY", "label": "拒绝", "style": "danger", "shortcut": "4" }
  ],
  "timestamp": "2026-04-04T10:00:04Z"
}
```

#### 6. ResponseCompleteMessage - 响应完成

```json
{
  "type": "RESPONSE_COMPLETE",
  "content": "已完成文件写入。",
  "inputTokens": 150,
  "outputTokens": 30,
  "durationMs": 2500,
  "toolCalls": 1,
  "timestamp": "2026-04-04T10:00:05Z"
}
```

#### 7. ErrorMessage - 错误

```json
{
  "type": "ERROR",
  "code": "PERMISSION_DENIED",
  "message": "工具调用被拒绝：write_file",
  "timestamp": "2026-04-04T10:00:06Z"
}
```

#### 8. PermissionCancelledMessage - 权限请求取消

当某个前端已响应权限请求时，其他前端会收到此通知：

```json
{
  "type": "PERMISSION_CANCELLED",
  "requestId": "perm_1234567890_abc",
  "timestamp": "2026-04-04T10:00:07Z"
}
```

### 客户端 → 服务端消息

#### 1. UserMessage - 用户消息

```json
{
  "type": "USER_MESSAGE",
  "content": "请帮我读取 /tmp/test.txt 文件",
  "requestId": "req_1234567890",
  "timestamp": "2026-04-04T10:00:00Z"
}
```

#### 2. PermissionResponseMessage - 权限响应

```json
{
  "type": "PERMISSION_RESPONSE",
  "requestId": "perm_1234567890_abc",
  "choice": "ALLOW_ONCE",
  "sessionDurationMinutes": 30,
  "timestamp": "2026-04-04T10:00:01Z"
}
```

**choice 选项**：
- `ALLOW_ONCE`: 仅允许本次
- `ALLOW_SESSION`: 会话内允许（需指定时长）
- `ALLOW_ALWAYS`: 始终允许
- `DENY`: 拒绝

#### 3. GetHistoryMessage - 获取历史

```json
{
  "type": "GET_HISTORY",
  "limit": 20,
  "requestId": "req_history_123",
  "timestamp": "2026-04-04T10:00:02Z"
}
```

#### 4. GetStatsMessage - 获取统计

```json
{
  "type": "GET_STATS",
  "requestId": "req_stats_123",
  "timestamp": "2026-04-04T10:00:03Z"
}
```

#### 5. PingMessage - 心跳

```json
{
  "type": "PING",
  "timestamp": "2026-04-04T10:00:04Z"
}
```

---

## HTTP REST API

### POST /api/v2/chat

发送消息并获取响应（阻塞模式）。

**请求**：
```bash
curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "hello", "sessionId": "session_123"}'
```

**响应**：
```json
{
  "content": "你好！有什么可以帮助你的？",
  "inputTokens": 10,
  "outputTokens": 20,
  "toolCalls": 0
}
```

### GET /api/v2/chat/stream

流式输出（SSE）。

```bash
curl -N http://localhost:8080/api/v2/chat/stream?message=hello
```

**事件流**：
```
event: response_start
data: {"type":"RESPONSE_START","turnId":"turn_123"}

event: text_delta
data: {"type":"TEXT_DELTA","delta":"你"}

event: text_delta
data: {"type":"TEXT_DELTA","delta":"好"}

event: response_complete
data: {"type":"RESPONSE_COMPLETE","content":"你好！...","durationMs":1500}
```

### GET /api/v2/permissions

获取待确认的权限请求列表。

```bash
curl http://localhost:8080/api/v2/permissions
```

**响应**：
```json
[
  {
    "id": "perm_123",
    "toolName": "write_file",
    "level": "MODIFY_STATE",
    "inputSummary": "...",
    "riskExplanation": "..."
  }
]
```

### POST /api/v2/permissions/respond

提交权限响应。

```bash
curl -X POST http://localhost:8080/api/v2/permissions/respond \
  -H "Content-Type: application/json" \
  -d '{"requestId": "perm_123", "choice": "ALLOW_ONCE"}'
```

**响应**：
```json
{
  "success": true,
  "status": "ALLOWED",
  "message": "权限已授予"
}
```

### GET /api/v2/permissions/stream

SSE 推送权限请求。

```bash
curl -N http://localhost:8080/api/v2/permissions/stream
```

### GET /api/v2/messages

获取历史消息。

```bash
curl "http://localhost:8080/api/v2/messages?limit=20"
```

### GET /api/v2/stats

获取会话统计。

```bash
curl http://localhost:8080/api/v2/stats
```

### GET /api/v2/health

健康检查。

```bash
curl http://localhost:8080/api/v2/health
```

---

## 消息类型参考

### ServerMessageType 枚举

| 类型 | 说明 | 前端处理建议 |
|-----|------|------------|
| `CONNECTED` | 连接确认 | 更新 UI 状态为"已连接" |
| `RESPONSE_START` | 响应开始 | 清空当前回复区域，准备接收增量 |
| `TEXT_DELTA` | 文本增量 | 追加文本到回复区域，实现打字机效果 |
| `TOOL_CALL` | 工具调用 | 显示工具卡片，根据 status 显示加载/成功/失败状态 |
| `PERMISSION_REQUEST` | 权限请求 | 弹出模态对话框，显示风险等级和选项按钮 |
| `RESPONSE_COMPLETE` | 响应完成 | 关闭加载状态，显示完整回复 |
| `ERROR` | 错误 | 显示错误提示 |
| `PERMISSION_CANCELLED` | 权限取消 | 关闭权限对话框（其他前端已响应） |
| `HISTORY` | 历史消息 | 渲染历史消息列表 |
| `STATS` | 统计信息 | 显示统计面板 |

---

## 权限确认流程

```
┌─────────┐          ┌─────────┐          ┌─────────┐
│ 前端 A   │          │  服务端  │          │ 前端 B   │
└────┬────┘          └────┬────┘          └────┬────┘
     │                    │                    │
     │  UserMessage       │                    │
     │───────────────────>│                    │
     │                    │                    │
     │                    │ 需要权限确认        │
     │                    │                    │
     │  PermissionRequest │  PermissionRequest │
     │<───────────────────│───────────────────>│
     │                    │                    │
     │  显示对话框         │                    │  显示对话框
     │                    │                    │
     │  PermissionResponse│                    │
     │───────────────────>│                    │
     │                    │                    │
     │                    │ 处理响应            │
     │                    │                    │
     │  PermissionCancelled                   │
     │<───────────────────│───────────────────>│
     │                    │                    │
     │  关闭对话框         │                    │  关闭对话框
     │                    │                    │
```

---

## 前端集成示例

### React Hook 示例

```typescript
// hooks/useAgent.ts
import { useEffect, useState, useCallback } from 'react';

interface Message {
  type: string;
  [key: string]: any;
}

export function useAgent(serverUrl: string) {
  const [connected, setConnected] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [pendingPermissions, setPendingPermissions] = useState<any[]>([]);

  const sendMessage = useCallback((msg: Message) => {
    ws.send(JSON.stringify(msg));
  }, []);

  const handlePermissionResponse = (requestId: string, choice: string) => {
    sendMessage({
      type: 'PERMISSION_RESPONSE',
      requestId,
      choice,
    });
  };

  useEffect(() => {
    const ws = new WebSocket(serverUrl);

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);

      switch (msg.type) {
        case 'CONNECTED':
          setConnected(true);
          break;

        case 'PERMISSION_REQUEST':
          setPendingPermissions(prev => [...prev, msg]);
          break;

        case 'PERMISSION_CANCELLED':
          setPendingPermissions(prev =>
            prev.filter(p => p.id !== msg.requestId)
          );
          break;

        case 'TEXT_DELTA':
          // 处理流式文本
          break;

        case 'TOOL_CALL':
          // 处理工具调用
          break;
      }

      setMessages(prev => [...prev, msg]);
    };

    ws.onclose = () => setConnected(false);

    return () => ws.close();
  }, [serverUrl]);

  return {
    connected,
    messages,
    pendingPermissions,
    sendMessage,
    handlePermissionResponse,
  };
}
```

### Vue 3 示例

```typescript
// composables/useAgent.ts
import { ref, reactive } from 'vue';

export function useAgent(serverUrl: string) {
  const connected = ref(false);
  const messages = reactive<any[]>([]);
  const permissions = reactive<any[]>([]);
  let ws: WebSocket | null = null;

  const connect = () => {
    ws = new WebSocket(serverUrl);

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      messages.push(msg);

      if (msg.type === 'PERMISSION_REQUEST') {
        permissions.push(msg);
      }
    };

    ws.onopen = () => connected.value = true;
    ws.onclose = () => connected.value = false;
  };

  const respondPermission = (requestId: string, choice: string) => {
    ws?.send(JSON.stringify({
      type: 'PERMISSION_RESPONSE',
      requestId,
      choice,
    }));
  };

  return {
    connected,
    messages,
    permissions,
    connect,
    respondPermission,
  };
}
```

---

## 错误处理

### WebSocket 错误码

| Code | 说明 |
|------|------|
| 1000 | 正常关闭 |
| 1001 | 端点离开 |
| 1006 | 异常关闭（网络问题） |
| 4001 | Token 无效 |
| 4002 | Token 过期 |

### HTTP 错误码

| Code | 说明 |
|------|------|
| 400 | 请求格式错误 |
| 401 | 未认证/Token 无效 |
| 403 | 权限拒绝 |
| 404 | 资源未找到 |
| 500 | 服务器内部错误 |
| 504 | 请求超时 |

---

## 版本历史

| 版本 | 日期 | 变更 |
|-----|------|------|
| 2.0.0 | 2026-04-04 | 增强的工具和权限消息，多前端广播支持 |
| 1.0.0 | 2026-03-01 | 初始版本，基础 TUI 支持 |

---

## 联系与反馈

- GitHub: https://github.com/whalefa1I/ai-agent-demo
- 问题反馈：请在仓库提交 Issue
