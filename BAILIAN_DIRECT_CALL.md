# 百炼 API 直接调用模式配置说明

## 功能说明

为了解决 Spring AI 不保留 `reasoning_content` 字段的问题，我们添加了一个新的 `BailianClient` 客户端，可以直接调用百炼 API 并完整解析 `reasoning_content` 字段，**同时支持工具调用**。

## 配置方式

在 `.env` 文件中添加以下配置：

```bash
# 启用直接调用模式（绕过 Spring AI）
DASHSCOPE_DIRECT_CALL=true
```

## 配置说明

- `DASHSCOPE_DIRECT_CALL=true`: 启用直接调用模式，使用 `BailianClient` 直接调用百炼 API
- `DASHSCOPE_DIRECT_CALL=false` (默认): 使用 Spring AI 的 OpenAI 兼容层调用

## 功能对比

| 功能 | Spring AI 模式 | 直接调用模式 |
|------|---------------|-------------|
| reasoning_content | ❌ 丢失 | ✅ 完整保留并流式推送 |
| 工具调用 | ✅ 支持 | ✅ 支持 |
| Token 计数 | ✅ 支持 | ✅ 支持 |
| 请求/响应日志 | ✅ 支持 | ✅ 支持 |
| 流式输出 | ✅ 支持 | ✅ 支持 |

## 工作原理

直接调用模式下，`EnhancedAgenticQueryLoop` 会：

1. 使用 `BailianClient.chatStream()` 直接调用百炼 API
2. 接收 reasoning_content 增量并通过 `onReasoningDelta` 回调推送
3. 检测工具调用并执行（与 Spring AI 模式相同的工具执行逻辑）
4. 将 Bailian 响应转换为 Spring AI 格式，保持后续逻辑兼容

## 注意事项

1. **日志保存路径**：`/tmp/bailian-debug/`
2. **模型名称**：通过 `DASHSCOPE_MODEL` 环境变量控制
3. **工具调用支持**：直接调用模式现在完全支持工具调用

## 测试方法

启动服务后，发送测试请求：

```bash
curl -X POST "http://localhost:8081/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "请帮我分析：如果一个公司有 1000 名员工，每年流失率是 15%，招聘新员工的成本是每人 5 万元，那么公司每年的招聘成本是多少？请详细说明你的思考过程。"}'
```

查看日志：
- 请求日志：`/tmp/bailian-debug/<session-id>/turn-0-direct-request-*.json`
- 响应日志：`/tmp/bailian-debug/<session-id>/response-*.json`

## 调试

查看 WebSocket 日志，确认 REASONING_DELTA 消息：

```bash
tail -f backend.log | grep REASONING_DELTA
```

前端测试工具：

```bash
cd ai-agent-web
npm run test:ws "请用详细的思考过程回答：1+1 等于几？为什么？"
```
