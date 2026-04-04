# 多用户 TUI 架构文档

## 概述

本文档描述了 minimal-k8s-agent-demo 的多用户 TUI（终端用户界面）架构，支持多个用户通过独立的 TUI 客户端同时连接到同一个 Agent 服务。

---

## 架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          多用户 TUI 架构                                  │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  TUI Client │    │  TUI Client │    │  TUI Client │
│   (Java)    │    │   (Java)    │    │   (Java)    │
│             │    │             │    │             │
│ JLine3 TUI  │    │ JLine3 TUI  │    │ JLine3 TUI  │
│ WebSocket   │    │ WebSocket   │    │ WebSocket   │
└──────┬──────┘    └──────┬──────┘    └──────┬──────┘
       │                  │                  │
       │ WebSocket        │ WebSocket        │ WebSocket
       │ wss://...        │ wss://...        │ wss://...
       │ (独立连接)        │ (独立连接)        │ (独立连接)
       ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Spring Boot Server                                │
│  Port: 8080                                                             │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    WebSocket Handler                              │  │
│  │   @ServerEndpoint("/ws/agent")                                    │  │
│  │   - Session 1 → SessionContext (用户 A)                            │  │
│  │   - Session 2 → SessionContext (用户 B)                            │  │
│  │   - Session 3 → SessionContext (用户 C)                            │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                              │                                          │
│  ┌───────────────────────────┼───────────────────────────────────────┐  │
│  │                    Core Services                                  │  │
│  │   - EnhancedAgenticQueryLoop (无状态，每请求独立)                   │  │
│  │   - PermissionManager (会话级授权缓存)                             │  │
│  │   - ConversationManager (@SessionScope 每用户独立)                 │  │
│  │   - CoordinatorState (@SessionScope 每用户独立)                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 组件说明

### 1. TUI 客户端 (tui-client/)

**技术栈：**
- JLine3 - 终端 UI 框架
- Java-WebSocket - WebSocket 客户端
- Jackson - JSON 序列化

**核心功能：**
- 终端界面渲染（提示符、消息、对话框）
- WebSocket 通信
- 权限确认对话框
- 命令处理（/help, /quit, /clear 等）

**运行方式：**
```bash
# 构建
cd tui-client
mvn package

# 运行
java -jar target/minimal-k8s-agent-tui-jar-with-dependencies.jar

# 指定服务器地址
java -jar ...jar --server ws://192.168.1.100:8080/ws/agent
```

### 2. WebSocket 服务端 (minimal-k8s-agent-demo/)

**新增文件：**
- `ws/protocol/WsProtocol.java` - 通信协议定义
- `ws/AgentWebSocketHandler.java` - WebSocket 处理器
- `config/WebSocketConfig.java` - WebSocket 配置

**端点：**
```
ws://localhost:8080/ws/agent
```

**会话管理：**
- 每个 WebSocket 连接创建独立的 `SessionContext`
- 使用 Spring @SessionScope 实现用户隔离
- 支持并发多用户连接

---

## 通信协议

### 客户端 → 服务端消息

| 类型 | 说明 | 示例 |
|------|------|------|
| `USER_MESSAGE` | 用户输入 | `{"type":"USER_MESSAGE","content":"你好"}` |
| `PERMISSION_RESPONSE` | 权限响应 | `{"type":"PERMISSION_RESPONSE","requestId":"xxx","choice":"ALLOW_ONCE"}` |
| `PING` | 心跳 | `{"type":"PING"}` |
| `GET_HISTORY` | 获取历史 | `{"type":"GET_HISTORY","limit":20}` |
| `STOP_TASK` | 停止任务 | `{"type":"STOP_TASK","taskId":"xxx"}` |

### 服务端 → 客户端消息

| 类型 | 说明 | 示例 |
|------|------|------|
| `CONNECTED` | 连接确认 | `{"type":"CONNECTED","sessionId":"xxx"}` |
| `RESPONSE_START` | 响应开始 | `{"type":"RESPONSE_START","turnId":"xxx"}` |
| `TEXT_DELTA` | 文本增量（流式） | `{"type":"TEXT_DELTA","delta":"你"}` |
| `TOOL_CALL` | 工具调用通知 | `{"type":"TOOL_CALL","toolName":"k8s_sandbox_run","status":"started"}` |
| `PERMISSION_REQUEST` | 权限请求 | `{"type":"PERMISSION_REQUEST","id":"xxx","toolName":"..."}` |
| `RESPONSE_COMPLETE` | 响应完成 | `{"type":"RESPONSE_COMPLETE","content":"..."}` |
| `ERROR` | 错误 | `{"type":"ERROR","code":"PERMISSION_DENIED","message":"..."}` |
| `PONG` | 心跳响应 | `{"type":"PONG","serverTime":"..."}` |

---

## 用户隔离机制

### 1. WebSocket 会话隔离

每个用户连接创建独立的 `SessionContext`：
```java
static class SessionContext {
    final WebSocketSession session;
    final String sessionId;
    
    // 待确认的权限请求
    volatile PermissionRequest pendingPermissionRequest;
    volatile CompletableFuture<PermissionResult> pendingPermissionFuture;
}
```

### 2. Spring Session 隔离

使用 `@SessionScope` 确保每用户独立状态：
```java
@Component
@SessionScope
public class ConversationSession { ... }

@Component
@SessionScope
public class CoordinatorState { ... }
```

### 3. 权限授权隔离

权限授权存储在会话级缓存中：
```java
// PermissionManager
private final ConcurrentHashMap<String, CopyOnWriteArrayList<PermissionGrant>> sessionGrants;
```

---

## 权限确认流程

```
用户                TUI 客户端              WebSocket             服务端
 │                      │                      │                    │
 │ 输入"删除文件"        │                      │                    │
 ├─────────────────────>│                      │                    │
 │                      │  USER_MESSAGE        │                    │
 │                      ├─────────────────────>│                    │
 │                      │                      │  执行工具调用      │
 │                      │                      ├───────────────────>│
 │                      │                      │  需要权限确认      │
 │                      │                      │<───────────────────┤
 │                      │  PERMISSION_REQUEST  │                    │
 │                      │<─────────────────────┤                    │
 │ 显示权限对话框        │                      │                    │
 │ ┌─────────────────┐  │                      │                    │
 │ │ 🔐 工具调用确认  │  │                      │                    │
 │ │ [1] 本次允许     │  │                      │                    │
 │ └─────────────────┘  │                      │                    │
 │ 用户按 [1]           │                      │                    │
 ├─────────────────────>│                      │                    │
 │                      │  PERMISSION_RESPONSE │                    │
 │                      ├─────────────────────>│                    │
 │                      │                      │  处理响应          │
 │                      │                      ├───────────────────>│
 │                      │                      │  继续执行工具      │
 │                      │                      │<───────────────────┤
 │                      │  TEXT_DELTA          │                    │
 │                      │<─────────────────────┤                    │
 │ 显示结果             │                      │                    │
 │                      │  RESPONSE_COMPLETE   │                    │
 │                      │<─────────────────────┤                    │
```

---

## 部署方案

### 方案 A：单机部署（开发/测试）

```
┌──────────────────────┐
│   本地主机            │
│                      │
│  ┌────────────────┐  │
│  │ Spring Boot    │  │
│  │ Server :8080   │  │
│  └────────────────┘  │
│           ▲          │
│           │          │
│  ┌────────┴───────┐  │
│  │ TUI Client     │  │
│  │ (本地运行)     │  │
│  └────────────────┘  │
└──────────────────────┘
```

**启动顺序：**
```bash
# 1. 启动服务端
cd minimal-k8s-agent-demo
mvn spring-boot:run

# 2. 启动 TUI 客户端（新终端）
cd tui-client
mvn spring-boot:run -Dspring-boot.run.mainClass=demo.k8s.agent.tui.AgentTuiClient
```

### 方案 B：服务器部署（生产）

```
┌─────────────────────────────────────────────────────────┐
│                      云服务器                            │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │              Spring Boot Server                    │ │
│  │              (WebSocket :8080)                     │ │
│  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
           ▲                    ▲                    ▲
           │ WebSocket          │ WebSocket          │ WebSocket
           │                    │                    │
┌──────────┴──────┐   ┌─────────┴──────┐   ┌────────┴──────┐
│  TUI Client A   │   │  TUI Client B  │   │  TUI Client C │
│  (用户 A 本地)    │   │  (用户 B 本地)   │   │  (用户 C 本地)  │
└─────────────────┘   └────────────────┘   └───────────────┘
```

**服务端启动：**
```bash
# 服务器
java -jar minimal-k8s-agent-demo.jar \
  --server.port=8080 \
  --demo.k8s.enabled=true
```

**客户端连接：**
```bash
# 用户 A
java -jar minimal-k8s-agent-tui.jar --server ws://server.example.com:8080/ws/agent

# 用户 B
java -jar minimal-k8s-agent-tui.jar --server ws://server.example.com:8080/ws/agent
```

### 方案 C：Docker 部署

```yaml
# docker-compose.yml
version: '3.8'
services:
  agent-server:
    image: minimal-k8s-agent-demo:latest
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - DEMO_K8S_ENABLED=true
    volumes:
      - ~/.claude:/root/.claude
```

**启动：**
```bash
docker-compose up -d
```

**客户端连接：**
```bash
java -jar minimal-k8s-agent-tui.jar --server ws://localhost:8080/ws/agent
```

---

## 安全考虑

### 1. WebSocket 认证

当前实现未启用认证，生产环境建议添加：

```java
// 添加 Token 验证
@ServerEndpoint("/ws/agent/{token}")
public void onOpen(Session session, @PathParam("token") String token) {
    if (!userService.validateToken(token)) {
        session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION, "Invalid token"));
        return;
    }
}
```

### 2. WSS 加密

生产环境应使用 WSS（WebSocket over SSL）：

```
wss://server.example.com:8443/ws/agent
```

### 3. 速率限制

防止恶意用户频繁请求：
```java
// 每分钟最多 60 条消息
if (ctx.messageCount.get() > 60) {
    sendMessage(ctx.session, new ErrorMessage("RATE_LIMIT", "请求过于频繁"));
    return;
}
```

---

## 性能优化

### 1. 连接池

对于高并发场景，可以使用连接池管理 WebSocket 连接。

### 2. 消息压缩

启用 WebSocket 消息压缩：
```java
// 添加压缩支持
registry.addHandler(handler, "/ws/agent")
        .setAllowedOrigins("*")
        .withWebSocketTransport(new StandardWebSocketTransport() {{
            setMaxTextMessageBufferSize(8192);
        }});
```

### 3. 异步执行

Query loop 已使用异步执行，不阻塞 WebSocket 主线程。

---

## 监控和日志

### 1. 日志配置

```yaml
# application.yml
logging:
  level:
    demo.k8s.agent.ws: DEBUG
    org.springframework.web.socket: INFO
```

### 2. Prometheus 指标

现有 `/api/observability/metrics` 端点已包含 WebSocket 连接数指标。

---

## 故障排查

### 客户端无法连接

1. 检查服务端是否启动：`curl http://localhost:8080/api/health`
2. 检查 WebSocket 端点：`wscat -c ws://localhost:8080/ws/agent`
3. 检查防火墙设置

### 权限对话框不显示

1. 检查 `PermissionManager` 是否正确注入
2. 查看服务端日志是否有 `PERMISSION_REQUEST` 发送
3. 确认客户端 `handlePermissionRequest` 方法执行

### 多用户会话混淆

1. 确认 `@SessionScope` 正确配置
2. 检查 `SessionContext` 的 sessionId 是否唯一
3. 查看 WebSocket 连接是否独立

---

## 扩展功能

### 1. 添加新命令

```java
// AgentTuiClient.java
private boolean handleCommand(String cmd) {
    switch (cmd.toLowerCase()) {
        case "/tasks":
            listTasks();
            break;
        // ... 其他命令
    }
}
```

### 2. 添加颜色主题

```java
// 自定义样式
private static final AttributedStyle PROMPT_STYLE = 
    AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
```

### 3. 添加自动补全

```java
// 使用 JLine 的 Completer
lineReader.setCompleter(new AgentCompleter());
```

---

## 总结

本架构实现了：
- ✅ 多用户并发连接
- ✅ 用户会话隔离
- ✅ 实时权限确认
- ✅ 流式文本输出
- ✅ 独立 TUI 客户端
- ✅ 生产部署支持

TUI 客户端代码位于 `tui-client/`，服务端代码位于 `minimal-k8s-agent-demo/`，两者通过 WebSocket 协议通信。
