# AgentScope 沙盒集成指南

## 概述

本集成允许你的 Spring Boot 应用通过 HTTP 调用远程 AgentScope Runtime 沙盒服务，提供安全的代码执行、文件操作、浏览器自动化等功能。

## 架构

```
┌─────────────────────────┐      HTTP/JSON      ┌──────────────────────────┐
│  minimal-k8s-agent-demo │ ◄─────────────────► │  AgentScope Sandbox      │
│  (Spring Boot + Java)   │                     │  (Python + Docker/K8s)   │
│                         │                     │                          │
│  - AgentScopeSandbox    │                     │  - BaseSandbox           │
│    Service              │                     │  - GuiSandbox            │
│  - REST Controller      │                     │  - FilesystemSandbox     │
│                         │                     │  - BrowserSandbox        │
└─────────────────────────┘                     │  - MobileSandbox         │
                                                └──────────────────────────┘
```

## 快速开始

### 1. 启动 AgentScope Sandbox Server

```bash
# 安装 AgentScope Runtime
pip install agentscope-runtime

# 启动沙盒服务器
runtime-sandbox-server --host 0.0.0.0 --port 8000
```

### 2. 配置 Spring Boot 应用

在 `application.yml` 或 `application-local.yml` 中添加：

```yaml
agentscope:
  sandbox:
    # 远程沙盒服务器地址
    base-url: http://localhost:8000
    # 是否启用
    enabled: true
    # 默认沙盒类型：base, gui, filesystem, browser, mobile
    default-sandbox-type: base
    # 会话超时时间（秒）
    session-timeout-seconds: 3600
```

### 3. 使用沙盒服务

#### 方式 1：直接注入 Service

```java
@Autowired
private AgentScopeSandboxService sandboxService;

// 创建会话
SandboxSessionInfo session = sandboxService.createSession(
    "base",           // 沙盒类型
    "my-session-001", // 会话 ID
    "user-123"        // 用户 ID
);

// 执行 Python 代码
SandboxToolResult result = sandboxService.executePython(
    session.sessionId(),
    "print(1 + 1)"
);
System.out.println(result.output());

// 执行 Shell 命令
SandboxToolResult shellResult = sandboxService.executeShell(
    session.sessionId(),
    "ls -la"
);

// 关闭会话
sandboxService.closeSession(session.sessionId());
```

#### 方式 2：通过 HTTP API

```bash
# 创建会话
curl -X POST http://localhost:8080/api/sandbox/session/create \
  -H "Content-Type: application/json" \
  -d '{
    "sandbox_type": "base",
    "session_id": "my-session-001",
    "user_id": "user-123"
  }'

# 执行 Python
curl -X POST http://localhost:8080/api/sandbox/execute/python \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "my-session-001",
    "code": "print(\"Hello from Python!\")"
  }'

# 执行 Shell
curl -X POST http://localhost:8080/api/sandbox/execute/shell \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "my-session-001",
    "command": "echo Hello from Shell"
  }'

# 列出活跃会话
curl http://localhost:8080/api/sandbox/sessions

# 关闭会话
curl -X POST http://localhost:8080/api/sandbox/session/close \
  -H "Content-Type: application/json" \
  -d '{"session_id": "my-session-001"}'
```

## HTTP API 参考

### 会话管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/sandbox/session/create` | 创建新会话 |
| POST | `/api/sandbox/session/close` | 关闭会话 |
| GET | `/api/sandbox/sessions` | 获取活跃会话列表 |

### 代码执行

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/sandbox/execute/python` | 执行 Python 代码 |
| POST | `/api/sandbox/execute/shell` | 执行 Shell 命令 |

### 工具调用

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/sandbox/tools?session_id=xxx` | 列出可用工具 |
| POST | `/api/sandbox/tool/call` | 调用指定工具 |

### MCP 集成

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/sandbox/mcp/add` | 添加 MCP 服务器 |

### 健康检查

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/sandbox/health` | 服务健康检查 |

## 支持的沙盒类型

### BaseSandbox（基础沙盒）

- Python 代码执行
- Shell 命令执行
- IPython 环境

```java
sandboxService.createSession("base", "session-1", "user-1");
```

### GuiSandbox（图形界面沙盒）

- 鼠标操作
- 键盘输入
- 屏幕截图

```java
SandboxSessionInfo session = sandboxService.createSession("gui", "gui-1", "user-1");

// 调用计算机操作工具
sandboxService.callTool(session.sessionId(), "computer_use", Map.of(
    "action", "click",
    "coordinate", List.of(500, 300)
));
```

### FilesystemSandbox（文件系统沙盒）

- 文件读写
- 目录管理
- 文件搜索

```java
sandboxService.callTool(session.sessionId(), "write_file", Map.of(
    "path", "/workspace/test.txt",
    "content", "Hello World"
));
```

### BrowserSandbox（浏览器沙盒）

- 网页导航
- 元素操作
- 截图保存

```java
sandboxService.callTool(session.sessionId(), "browser_navigate", Map.of(
    "url", "https://www.example.com"
));
```

## 高级功能

### 会话复用

```java
// 使用相同的 session_id 会复用同一个沙盒
SandboxSessionInfo session1 = sandboxService.createSession("base", "reusable", "user-1");
sandboxService.executePython("reusable", "a = 1");

// 后续调用
SandboxSessionInfo session2 = sandboxService.getOrCreateSession("base", "reusable", "user-1");
SandboxToolResult result = sandboxService.executePython("reusable", "print(a)");
// 输出：1 （变量 a 仍然存在）
```

### 添加 MCP 服务器

```java
Map<String, Object> mcpConfig = Map.of(
    "mcpServers", Map.of(
        "time", Map.of(
            "command", "uvx",
            "args", List.of("mcp-server-time", "--local-timezone=America/New_York")
        )
    )
);

sandboxService.addMcpServer(session.sessionId(), mcpConfig);

// 调用 MCP 工具
sandboxService.callTool(session.sessionId(), "get_current_time", Map.of(
    "timezone", "America/New_York"
));
```

## 错误处理

```java
try {
    SandboxToolResult result = sandboxService.executePython(sessionId, code);
    if (result.success()) {
        // 处理成功结果
        System.out.println("Output: " + result.output());
    } else {
        // 处理错误
        System.err.println("Error: " + result.error());
    }
} catch (Exception e) {
    // 处理网络或其他异常
    log.error("沙盒调用失败", e);
}
```

## 配置远程沙盒服务器

### Docker 部署

```bash
# 拉取镜像
docker pull agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest

# 启动服务器
docker run -d -p 8000:8000 \
  --name sandbox-server \
  agentscope/runtime-sandbox-server
```

### Kubernetes 部署

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sandbox-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: sandbox-server
  template:
    metadata:
      labels:
        app: sandbox-server
    spec:
      containers:
      - name: sandbox-server
        image: agentscope/runtime-sandbox-server:latest
        ports:
        - containerPort: 8000
---
apiVersion: v1
kind: Service
metadata:
  name: sandbox-server
spec:
  selector:
    app: sandbox-server
  ports:
  - port: 80
    targetPort: 8000
  type: ClusterIP
```

然后在应用中配置：
```yaml
agentscope:
  sandbox:
    base-url: http://sandbox-server.default.svc.cluster.local
```

## 安全考虑

1. **网络隔离**: 沙盒服务器应部署在隔离的网络中
2. **身份验证**: 启用 bearer token 认证
3. **资源限制**: 配置 Docker/K8s 资源配额
4. **会话超时**: 设置合理的 session-timeout-seconds

## 故障排查

### 连接失败

检查日志：
```
AgentScopeSandboxService 初始化完成，远程地址：http://localhost:8000
健康检查失败：Connection refused
```

解决：
1. 确认 sandbox-server 已启动
2. 检查端口是否被占用
3. 验证防火墙规则

### 会话创建失败

检查 AgentScope Runtime 版本：
```bash
pip show agentscope-runtime
```

## 测试

运行单元测试：
```bash
./mvnw.cmd test -Dtest=AgentScopeSandboxServiceTest
```

## 参考资料

- [AgentScope Runtime 文档](https://github.com/modelscope/agentscope-runtime)
- [AgentScope Sandbox Cookbook](https://github.com/modelscope/agentscope-runtime/tree/main/cookbook)
