# 百炼 API 流式调用功能说明

## 功能概述

本次更新实现了百炼 API 的直接流式调用，支持：
1. **reasoning_content 流式推送** - 实时推送模型的思考过程
2. **工具调用解析** - 自动解析并执行模型返回的工具调用
3. **SSE 流式输出** - 通过 WebSocket 逐 token 推送响应内容

## 架构说明

```
用户消息 → WebSocket → AgentWebSocketHandler
                     ↓
                     EnhancedAgenticQueryLoop.callBailianStream()
                     ↓
                     BailianClient.chatStream()
                     ↓
                     百炼 API (SSE 流)
                     ↓
                     解析响应：
                     - reasoning_content → ReasoningDeltaMessage
                     - content → TextDeltaMessage  
                     - tool_calls → ToolCallMessage
```

## 消息类型

前端现在会收到以下类型的 WebSocket 消息：

| 消息类型 | 说明 | 示例 |
|---------|------|------|
| `RESPONSE_START` | 响应开始 | `{"type": "RESPONSE_START", "requestId": "...", "turnId": "..."}` |
| `REASONING_DELTA` | reasoning 内容增量（新增） | `{"type": "REASONING_DELTA", "delta": "让我思考一下..."}` |
| `TEXT_DELTA` | 普通内容增量 | `{"type": "TEXT_DELTA", "delta": "你好"}` |
| `TOOL_CALL` | 工具调用通知 | `{"type": "TOOL_CALL", "toolName": "file_read", "status": "started"}` |
| `RESPONSE_COMPLETE` | 响应完成 | `{"type": "RESPONSE_COMPLETE", "content": "...", "inputTokens": 10, "outputTokens": 20}` |

## 配置方式

在 `.env` 文件中配置：

```bash
# 启用直接调用模式（绕过 Spring AI）
demo.bailian.direct-call=true

# 百炼 API 配置
DASHSCOPE_API_KEY=sk-sp-ab63f62c8df3494a8763982b1a741081
DASHSCOPE_BASE_URL=https://coding.dashscope.aliyuncs.com
DASHSCOPE_MODEL=qwen3.5-plus
```

## 文件清单

### 后端文件

1. **`src/main/java/demo/k8s/agent/client/BailianClient.java`**
   - 百炼 API 直接调用客户端
   - `chatStream()` - 流式调用方法
   - `chat()` - 非流式调用方法
   - `ToolCall` / `FunctionCall` - 工具调用数据结构

2. **`src/main/java/demo/k8s/agent/query/EnhancedAgenticQueryLoop.java`**
   - `callBailianStream()` - 流式调用入口
   - 支持 Spring AI Message 到百炼 API 格式转换
   - 支持 ToolCallback 到百炼 API 工具格式转换

3. **`src/main/java/demo/k8s/agent/ws/protocol/WsProtocol.java`**
   - 新增 `ServerMessageType.REASONING_DELTA`
   - 新增 `ReasoningDeltaMessage` 类

4. **`src/main/java/demo/k8s/agent/ws/AgentWebSocketHandler.java`**
   - 添加 reasoning 增量回调
   - 使用 `ReasoningDeltaMessage` 推送 reasoning 内容

### 前端文件

5. **`src/types/happy-protocol.ts`**
   - 新增 `HappyEventType.reasoning`
   - 新增 `ReasoningEvent` 接口
   - 新增 `Message.kind = 'reasoning'`

6. **`src/tools/hooks/use-agent-websocket.ts`**
   - 新建 Agent WebSocket 客户端
   - 支持 `REASONING_DELTA` 消息处理
   - 支持流式回调：`onReasoningDelta`, `onTextDelta`, `onResponseStart`, `onResponseComplete`

7. **`src/components/MessageView.vue`**
   - 新增 `reasoning` kind 的消息渲染
   - reasoning 内容样式：灰色斜体，带左侧边框

8. **`src/tools/transformers/artifact-transformer.ts`**
   - 支持 `reasoning` kind 的 artifact 转换

## 测试方法

### 1. WebSocket 测试

使用 WebSocket 客户端连接：

```bash
# 安装 wscat
npm install -g wscat

# 连接 WebSocket
wscat -c "ws://localhost:8081/ws/agent/your-token"

# 发送消息
{"type": "USER_MESSAGE", "requestId": "test_001", "content": "请帮我分析：如果一个公司有 1000 名员工，每年流失率是 15%，招聘新员工的成本是每人 5 万元，那么公司每年的招聘成本是多少？请详细说明你的思考过程。"}
```

### 2. 查看调试日志

流式调用的请求/响应日志保存在：

```
/tmp/bailian-debug/<session-id>/turn-*-stream-request-*.json
/tmp/bailian-debug/<session-id>/turn-*-stream-response-*.json
```

### 3. 预期输出顺序

```
1. RESPONSE_START
2. REASONING_DELTA × N (模型思考过程)
3. TEXT_DELTA × N (最终回答)
4. [可选] TOOL_CALL (如果需要调用工具)
5. RESPONSE_COMPLETE
```

## 注意事项

1. **流式调用不支持 Spring AI 的工具执行**
   - 工具调用由百炼 API 返回，应用层解析并执行
   - 需要在 `EnhancedAgenticQueryLoop` 中显式处理工具执行

2. **reasoning_content 是百炼 API 特有字段**
   - OpenAI API 不返回此字段
   - 如果使用其他模型，可能收不到 `REASONING_DELTA` 消息

3. **Token 计数**
   - 流式调用中 usage 信息在最后一个 chunk 返回
   - 当前实现可能不包含完整的 token 统计

## 故障排查

### 问题：收不到 REASONING_DELTA 消息

- 确认模型支持 reasoning_content（qwen3.5-plus 支持）
- 检查百炼 API 响应中是否包含 reasoning_content 字段
- 查看 `/tmp/bailian-debug/` 中的响应日志

### 问题：工具调用不执行

- 检查工具定义是否正确转换为百炼 API 格式
- 确认工具名称与注册的工具名称匹配
- 查看日志中是否有 "转换工具定义失败" 的错误

### 问题：WebSocket 连接失败

- 确认 Token 是否正确
- 检查 WebSocket 路径：`/ws/agent/{token}`
- 查看服务日志中是否有连接拒绝的记录
