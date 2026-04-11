# 百炼 API 直接调用模式配置说明

## 功能说明

为了解决 Spring AI 不保留 `reasoning_content` 字段的问题，我们添加了一个新的 `BailianClient` 客户端，可以直接调用百炼 API 并完整解析 `reasoning_content` 字段。

## 配置方式

在 `.env` 文件中添加以下配置：

```bash
# 启用直接调用模式（绕过 Spring AI）
demo.bailian.direct-call=true
```

## 配置说明

- `demo.bailian.direct-call=true`: 启用直接调用模式，使用 `BailianClient` 直接调用百炼 API
- `demo.bailian.direct-call=false` (默认): 使用 Spring AI 的 OpenAI 兼容层调用

## 功能对比

| 功能 | Spring AI 模式 | 直接调用模式 |
|------|---------------|-------------|
| reasoning_content | ❌ 不支持 | ✅ 完整支持 |
| 工具调用 | ✅ 支持 | ❌ 不支持 |
| Token 计数 | ✅ 支持 | ✅ 支持 |
| 请求/响应日志 | ✅ 支持 | ✅ 支持 |

## 注意事项

1. **直接调用模式不支持工具调用**：仅适用于纯对话场景
2. **日志保存路径**：`/tmp/bailian-debug/`
3. **模型名称**：通过 `DASHSCOPE_MODEL` 环境变量控制

## 测试方法

启动服务后，发送测试请求：

```bash
curl -X POST "http://localhost:8081/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "请帮我分析：如果一个公司有 1000 名员工，每年流失率是 15%，招聘新员工的成本是每人 5 万元，那么公司每年的招聘成本是多少？请详细说明你的思考过程。"}'
```

查看日志：
- 请求日志：`/tmp/bailian-debug/<session-id>/turn-0-direct-request-*.json`
- 响应日志：`/tmp/bailian-debug/<session-id>/turn-0-direct-response-*.json`
