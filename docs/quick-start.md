# 快速开始指南

## 概述

minimal-k8s-agent-demo 是一个基于 Spring Boot 4 + Spring AI 的智能代理示例，支持：

- ✅ Kubernetes Job 沙盒工具调用
- ✅ 5 级权限分类和用户确认对话框
- ✅ 完整的多轮对话和历史管理
- ✅ 多 Agent 协作架构
- ✅ WebSocket TUI 客户端（支持多用户）

## 架构图

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  TUI Client │    │  TUI Client │    │  TUI Client │
│   (Java)    │    │   (Java)    │    │   (Java)    │
└──────┬──────┘    └──────┬──────┘    └──────┬──────┘
       │                  │                  │
       │ WebSocket        │ WebSocket        │ WebSocket
       ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────┐
│                    Spring Boot Server                    │
│  - EnhancedAgenticQueryLoop (带权限检查和指标追踪)         │
│  - PermissionManager (会话级授权)                          │
│  - ConversationManager (多轮对话管理)                      │
│  - Coordinator (多 Agent 协作)                             │
└─────────────────────────────────────────────────────────┘
```

## 前置条件

1. **JDK 17+**
   ```bash
   java -version
   ```

2. **Maven 3.6+**
   ```bash
   mvn -version
   ```

3. **spring-ai-agent-utils**（本地安装）
   ```bash
   cd ../spring-ai-agent-utils
   mvn install -DskipTests
   ```

4. **LLM API Key**（OpenAI 或兼容）
   ```bash
   # Linux/macOS
   export OPENAI_API_KEY=your-api-key
   export OPENAI_BASE_URL=https://api.openai.com  # 可选
   
   # Windows
   set OPENAI_API_KEY=your-api-key
   ```

## 快速启动

### 方式一：使用启动脚本（推荐）

**Linux/macOS:**
```bash
./run.sh
```

**Windows:**
```bash
run.bat
```

脚本会自动：
1. 构建 TUI 客户端
2. 启动服务端（后台运行）
3. 等待服务端就绪
4. 启动 TUI 客户端

### 方式二：手动启动

#### 1. 启动服务端

```bash
cd minimal-k8s-agent-demo
mvn spring-boot:run
```

服务端会在 `http://localhost:8080` 启动。

#### 2. 构建 TUI 客户端

```bash
cd tui-client
mvn package
```

#### 3. 启动 TUI 客户端

```bash
java -jar target/minimal-k8s-agent-tui-jar-with-dependencies.jar
```

或指定服务器地址：
```bash
java -jar ...jar --server ws://192.168.1.100:8080/ws/agent
```

## 使用方式

### TUI 客户端界面

启动后，你会看到类似这样的界面：

```
╔════════════════════════════════════════════════╗
║   minimal-k8s-agent-demo  TUI Client v0.1.0     ║
║   按 /help 查看帮助，/quit 退出                 ║
╚════════════════════════════════════════════════╝

❯ 
```

### 基本命令

| 命令 | 说明 |
|------|------|
| `/help` | 显示帮助信息 |
| `/quit` | 退出程序 |
| `/clear` | 清屏 |
| `/history` | 显示历史消息 |
| `/stats` | 显示会话统计 |

### 示例对话

```
❯ 请用 k8s_sandbox_run 执行 echo hello
```

如果需要权限确认，会显示对话框：

```
┌─────────────────────────────────────────────┐
│  🔐 工具调用确认                            │
├─────────────────────────────────────────────┤
│  工具：k8s_sandbox_run                      │
│  风险：修改状态                              │
├─────────────────────────────────────────────┤
│  {"command":"echo hello"}                   │
├─────────────────────────────────────────────┤
│  ⚠️  此操作将修改文件系统状态...              │
└─────────────────────────────────────────────┘

[1] 本次允许   [2] 会话允许   [3] 始终允许   [4] 拒绝
```

按数字键选择：
- `[1]` - 本次允许
- `[2]` - 会话允许（30 分钟）
- `[3]` - 始终允许（持久化）
- `[4]` - 拒绝

## API 端点

服务端提供以下 HTTP API：

### 聊天

```bash
# 阻塞式对话
POST http://localhost:8080/api/chat
Content-Type: application/json

{"message":"你好"}

# 流式对话（SSE）
POST http://localhost:8080/api/chat/stream
Content-Type: application/json
Accept: text/event-stream

{"message":"你好"}
```

### 权限管理

```bash
# 获取待确认的权限请求
GET http://localhost:8080/api/permissions/pending

# 提交权限响应
POST http://localhost:8080/api/permissions/respond
Content-Type: application/json

{"requestId":"xxx","choice":"ALLOW_ONCE"}

# SSE 推送
GET http://localhost:8080/api/permissions/stream
```

### 可观测性

```bash
# 会话统计
GET http://localhost:8080/api/observability/stats

# Prometheus 指标
GET http://localhost:8080/api/observability/metrics

# 工具调用历史
GET http://localhost:8080/api/observability/tool-calls
```

### 会话状态

```bash
# 有状态对话
POST http://localhost:8080/api/chat
Content-Type: application/json

{"message":"你好","sessionId":"xxx"}

# 获取历史消息
GET http://localhost:8080/api/chat/history?sessionId=xxx

# 文件历史
GET http://localhost:8080/api/chat/files/{path}/history
```

## 配置选项

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `OPENAI_API_KEY` | OpenAI API 密钥 | `changeme` |
| `OPENAI_BASE_URL` | OpenAI API 基础 URL | `https://api.openai.com` |
| `OPENAI_MODEL` | 模型名称 | `gpt-4o-mini` |
| `DEMO_K8S_ENABLED` | 启用 K8s 功能 | `false` |
| `DEMO_K8S_NAMESPACE` | K8s 命名空间 | `default` |
| `DEMO_K8S_RUNNER_IMAGE` | K8s Job 镜像 | `busybox:1.36` |
| `DEMO_COORDINATOR_MODE` | 启用多 Agent 模式 | `false` |
| `DEMO_TOOLS_PERMISSION_MODE` | 权限模式 | `default` |
| `DEMO_QUERY_MAX_TURNS` | 最大对话轮次 | `32` |

### application.yml

详见 `src/main/resources/application.yml`。

## 故障排查

### 服务端无法启动

1. 检查端口占用：
   ```bash
   netstat -ano | findstr :8080
   ```

2. 检查日志：
   ```bash
   cat server.log
   ```

### TUI 客户端无法连接

1. 确认服务端已启动：
   ```bash
   curl http://localhost:8080/api/health
   ```

2. 检查 WebSocket 连接：
   ```bash
   wscat -c ws://localhost:8080/ws/agent
   ```

### 权限确认对话框不显示

1. 检查 `PermissionManager` 是否正确注入
2. 查看服务端日志是否有 `PERMISSION_REQUEST` 发送
3. 确认 TUI 客户端 `handlePermissionRequest` 方法执行

## 多用户部署

### 场景 A：本地开发

```
┌──────────────────────┐
│   本地主机            │
│  ┌────────────────┐  │
│  │ Spring Boot    │  │
│  │ Server :8080   │  │
│  └────────────────┘  │
│           ▲          │
│  ┌────────┴───────┐  │
│  │ TUI Client     │  │
│  └────────────────┘  │
└──────────────────────┘
```

### 场景 B：服务器部署

```
┌─────────────────────────────────────────────────────────┐
│                      云服务器                            │
│  ┌───────────────────────────────────────────────────┐ │
│  │              Spring Boot Server                    │ │
│  │              (WebSocket :8080)                     │ │
│  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
           ▲                    ▲                    ▲
           │                    │                    │
┌──────────┴──────┐   ┌─────────┴──────┐   ┌────────┴──────┐
│  TUI Client A   │   │  TUI Client B  │   │  TUI Client C │
│  (用户 A 本地)    │   │  (用户 B 本地)   │   │  (用户 C 本地)  │
└─────────────────┘   └────────────────┘   └───────────────┘
```

### 启动命令

**服务端：**
```bash
java -jar minimal-k8s-agent-demo.jar \
  --server.port=8080 \
  --demo.k8s.enabled=true
```

**客户端：**
```bash
java -jar minimal-k8s-agent-tui.jar \
  --server ws://server.example.com:8080/ws/agent
```

## 安全建议

生产环境部署时，建议添加：

1. **WebSocket 认证**
   ```java
   @ServerEndpoint("/ws/agent/{token}")
   ```

2. **WSS 加密**
   ```
   wss://server.example.com:8443/ws/agent
   ```

3. **速率限制**
   ```java
   // 每分钟最多 60 条消息
   ```

4. **K8s 安全**
   - 镜像白名单
   - 非 root 运行
   - 只读根文件系统
   - NetworkPolicy
   - ResourceQuota

## 模块结构

| 路径 | 说明 |
|------|------|
| `config/` | Spring 配置类 |
| `k8s/` | Kubernetes Job 沙盒服务 |
| `web/` | REST API 控制器 |
| `toolsystem/` | 权限管理核心 |
| `observability/` | 可观测性基础设施 |
| `state/` | 会话状态管理 |
| `coordinator/` | 多 Agent 协作 |
| `ws/` | WebSocket 支持 |
| `tui-client/` | TUI 客户端项目 |
| `docs/` | 文档 |

## 参考文档

- [改进计划](docs/improvement-plan.md)
- [实现总结](docs/implementation-summary.md)
- [TUI 架构](docs/tui-architecture.md)
- [API 参考](docs/quick-reference.md)
- [Query Loop 对齐](docs/query-loop-alignment.md)
