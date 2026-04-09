# minimal-k8s-agent-demo

基于 **Spring Boot 4** + **AgentScope** 的智能 Agent 演示项目，支持 WebSocket 实时通信、工具调用权限确认、多前端界面。

## 快速开始

### 环境要求

```bash
# Java 21
java -version

# Maven 3.6+
mvn -version

# Node.js 18+ (前端)
node -v
```

### 启动后端

```bash
cd minimal-k8s-agent-demo

# 设置 API Key (可选，根据使用的模型提供商)
export OPENAI_API_KEY=your-api-key

# 启动服务
./mvnw spring-boot:run
```

后端启动后访问：
- HTTP API: http://localhost:8080
- 健康检查：http://localhost:8080/api/health

### 启动前端

```bash
cd happy-client

# 安装依赖
npm install

# 开发模式
npm run dev
```

前端访问：http://localhost:3000

## 核心功能

### 1. WebSocket 实时通信

- 协议版本：2.0.0
- 双向流式对话
- 自动重连 + 心跳检测
- 支持多前端同时连接

### 2. 权限管理系统

| 风险级别 | 说明 | 示例 |
|---------|------|------|
| READ_ONLY | 只读操作 | 读取文件、搜索代码 |
| MODIFY_STATE | 修改状态 | 编辑文件、创建文件 |
| NETWORK | 网络请求 | HTTP 调用、API 访问 |
| DESTRUCTIVE | 破坏性操作 | 删除文件、停止进程 |
| AGENT_SPAWN | 衍生 Agent | 创建子任务、委派 |

用户可选择：
- `ALLOW_ONCE` - 仅允许本次
- `ALLOW_SESSION` - 本次会话有效
- `ALLOW_ALWAYS` - 始终允许
- `DENY` - 拒绝

### 3. 工具调用

- Bash 工具 - 执行 Shell 命令
- 文件工具 - 读取/编辑/创建/删除文件
- 搜索工具 - Grep 代码搜索
- Agent 工具 - 委派子任务

### 4. 前端界面

- 聊天对话（Markdown 渲染）
- 工具调用状态显示（进度条、执行时长）
- 权限确认对话框
- 设置页面（API 配置、模型选择）
- WebSocket 连接状态监控

## 项目结构

```
minimal-k8s-agent-demo/
├── src/main/java/demo/k8s/agent/
│   ├── AgentServer.java          # WebSocket 服务器
│   ├── AgentService.java         # Agent 核心服务
│   ├── conversation/             # 对话管理
│   ├── state/                    # 会话状态
│   ├── tools/                    # 工具实现
│   ├── sandbox/                  # AgentScope 沙箱
│   └── websocket/                # WebSocket 协议处理
├── docs/                         # 文档
├── tests/                        # 测试
└── pom.xml

happy-client/
├── src/
│   ├── components/               # Vue 组件
│   ├── pages/                    # 页面
│   ├── services/                 # API 服务
│   ├── stores/                   # Pinia 状态
│   ├── types/                    # TypeScript 类型
│   └── styles/                   # 样式
└── package.json
```

## API 端点

### HTTP REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/v2/chat` | POST | 发送消息 |
| `/api/v2/stats` | GET | 会话统计 |
| `/api/v2/permissions` | GET | 获取待确认权限 |
| `/api/v2/history` | GET | 对话历史 |

### WebSocket

```
ws://localhost:8080/ws
```

消息类型：
- `user_message` - 用户消息
- `permission_response` - 权限确认
- `subscribe` - 订阅事件

## 配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:-https://api.openai.com/v1}

agent:
  protocol-version: 2.0.0
  max-steps-per-turn: 100
```

## 许可证

MIT License
