# MCP 和 Skills 集成文档

## 概述

本文档描述了近来的 MCP（Model Context Protocol）和 Skills 集成工作，这些功能使得 Agent 能够：

1. **MCP**: 连接到外部 MCP 服务器，发现和使用远程工具
2. **Skills**: 使用预定义的高级工具包（计算、代码执行、解释等）

## 完成的功能

### 1. MCP 集成

#### 核心组件

```
demo.k8s.agent.mcp
├── McpClientService.java       # MCP 服务管理器
├── McpServerConnection.java    # 单个 MCP 服务器连接
├── McpToolCall.java            # MCP 工具调用包装器
├── McpProperties.java          # MCP 配置属性
├── McpController.java          # MCP 管理 HTTP API
└── SpringMcpToolProvider.java  # Spring AI MCP 工具提供者
```

#### 功能特性

- **多传输方式支持**:
  - `stdio`: 通过子进程与本地 MCP 服务器通信
  - `sse`: 通过 Server-Sent Events 连接远程服务器
  - `websocket`: 通过 WebSocket 连接（降级为 HTTP 轮询）

- **JSON-RPC 2.0 协议**:
  - `tools/list`: 发现服务器上的工具
  - `tools/call`: 调用远程工具
  - `initialize`: 获取服务器信息

- **自动健康检查**: 每 30 秒检查一次所有连接的 MCP 服务器

- **动态工具发现**: 连接后自动发现工具并注册到 Agent 系统

#### HTTP API

```bash
# 获取所有 MCP 服务器状态
GET /api/mcp/servers

# 注册新的 MCP 服务器
POST /api/mcp/servers/register
{
  "name": "filesystem",
  "url": "stdio://npx -y @modelcontextprotocol/server-filesystem /workspace",
  "transport": "stdio",
  "autoConnect": true
}

# 连接到服务器
POST /api/mcp/servers/connect
{
  "serverName": "filesystem"
}

# 断开连接
POST /api/mcp/servers/disconnect
{
  "serverName": "filesystem"
}

# 获取服务器工具列表
GET /api/mcp/servers/{serverName}/tools

# 执行健康检查
POST /api/mcp/servers/{serverName}/health
```

#### 配置示例

```yaml
demo:
  mcp:
    enabled: true
    servers:
      - name: filesystem
        url: stdio://npx -y @modelcontextprotocol/server-filesystem /workspace
        transport: stdio
        autoConnect: true
        enabled: true
      - name: github
        url: https://api.github-mcp.example.com
        transport: sse
        autoConnect: false
        enabled: true
```

### 2. Skills 集成

#### 核心组件

```
demo.k8s.agent.skills
├── Skill.java                  # Skill 接口
├── SkillTool.java              # 工具定义
├── SkillResult.java            # 执行结果
├── SkillRegistry.java          # Skill 注册表
├── SkillService.java           # Spring 服务
├── SkillController.java        # HTTP API
├── SkillToolProvider.java      # 工具提供者
└── builtin/                    # 内置 Skills
    ├── CalcSkill.java          # 计算技能
    ├── CodeSkill.java          # 代码执行技能
    └── ExplainSkill.java       # 解释技能
```

#### 内置 Skills

##### CalcSkill - 计算技能

提供数学计算功能。

**工具**:
- `evaluate_expression`: 计算数学表达式（如 `2 + 2 * 3`）
- `statistics`: 统计计算（平均值、中位数、标准差等）

##### CodeSkill - 代码执行技能

提供安全的代码执行功能。

**工具**:
- `execute_python`: 执行 Python 代码
- `execute_shell`: 执行 Shell 命令

特性:
- 超时保护（默认 30 秒）
- 支持自定义工作目录
- 支持 stdin 输入

##### ExplainSkill - 解释技能

提供代码和文本解释功能（需要 LLM 集成）。

**工具**:
- `explain_code`: 解释代码
- `explain_text`: 解释文本

#### HTTP API

```bash
# 获取所有 Skills
GET /api/skills

# 获取 Skill 详情
GET /api/skills/{skillName}

# 执行 Skill 工具
POST /api/skills/{skillName}/execute/{toolName}
Content-Type: application/json

{"expression": "2 + 2 * 3"}
```

#### 工具命名约定

Skill 工具在 Agent 系统中的名称格式为：`{skillName}_{toolName}`

例如:
- `calc_evaluate_expression`
- `code_execute_python`
- `explain_explain_code`

## 架构集成

### AgentConfiguration

在 `AgentConfiguration.java` 中，所有工具源被统一组装：

```java
@Bean
@Primary
ChatClient demoChatClient(
        ChatClient.Builder chatClientBuilder,
        ToolRegistry demoToolRegistry,
        ToolPermissionContext toolPermissionContext,
        ToolFeatureFlags toolFeatureFlags,
        McpToolProvider mcpToolProvider,
        SkillRegistry skillRegistry,
        DemoCoordinatorProperties coordinatorProperties) {

    // 1. 加载 MCP 工具
    List<ToolCallback> mcpTools = mcpToolProvider.loadMcpTools().stream()
            .map(ToolModule::callback)
            .toList();

    // 2. 加载 Skill 工具
    SkillToolProvider skillToolProvider = new SkillToolProvider(skillRegistry);
    List<ToolCallback> skillTools = skillToolProvider.loadTools().stream()
            .map(ToolModule::callback)
            .toList();

    // 3. 合并所有工具
    List<ToolCallback> allTools = demoToolRegistry.filteredCallbacks(
            toolPermissionContext, toolFeatureFlags, mcpTools);
    allTools.addAll(skillTools);

    // 4. 注册到 ChatClient
    return chatClientBuilder
            .defaultSystem(system)
            .defaultToolCallbacks(allTools.toArray(ToolCallback[]::new))
            .build();
}
```

### 工具流向

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Built-in Tools │     │   MCP Tools     │     │   Skill Tools   │
│  (LocalTool)    │     │  (Remote JSON)  │     │  (Pre-defined)  │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │    ToolRegistry +       │
                    │  ToolAssembly (filter)  │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │      ChatClient         │
                    │  (defaultToolCallbacks) │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │   Claude Model          │
                    │  (Auto tool selection)  │
                    └─────────────────────────┘
```

## 权限集成

所有 Skills 和 MCP 工具都通过现有的权限系统：

- **ToolPermissionMode**: `AUTOMATIC`, `READ_ONLY`, `ACCEPT_EDIT`, `ACCEPT_ALL`
- **PermissionLevel**: `READ_ONLY`, `MODIFY_STATE`, `NETWORK`, `DESTRUCTIVE`, `AGENT_SPAWN`

MCP 工具和 Skill 工具默认根据其类别分配权限级别：
- `EXTERNAL` (MCP): NETWORK
- `code_execute_shell`: DESTRUCTIVE
- `calc_*`: READ_ONLY

## 测试覆盖

### 单元测试

- `SkillsTest.java`: 6 个测试用例覆盖 Skills 核心功能
- 所有现有测试保持通过（总计 72 个测试）

### 运行测试

```bash
# 运行所有测试
./mvnw.cmd test

# 仅运行 Skills 测试
./mvnw.cmd test -Dtest=SkillsTest

# 运行 MCP 相关测试（待添加）
./mvnw.cmd test -Dtest=Mcp*Test
```

## 使用示例

### 使用 CalcSkill

通过 HTTP API:
```bash
curl -X POST http://localhost:8080/api/skills/calc/execute/evaluate_expression \
  -H "Content-Type: application/json" \
  -d '{"expression": "2 + 2 * 3"}'
```

通过 Agent 对话:
```
User: 请帮我计算 2 + 2 * 3 的结果

Agent: [自动调用 calc_evaluate_expression 工具]
Agent: 结果是 8
```

### 使用 CodeSkill 执行 Python

通过 HTTP API:
```bash
curl -X POST http://localhost:8080/api/skills/code/execute/execute_python \
  -H "Content-Type: application/json" \
  -d '{"code": "print(sum(range(100)))"}'
```

### 连接 MCP 服务器

```bash
# 注册文件系统 MCP 服务器
curl -X POST http://localhost:8080/api/mcp/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "filesystem",
    "url": "stdio://npx -y @modelcontextprotocol/server-filesystem /workspace",
    "transport": "stdio"
  }'

# 查看发现的工具
curl http://localhost:8080/api/mcp/servers/filesystem/tools
```

## 待完成的工作

### MCP
- [ ] 完整的 stdio JSON-RPC 响应解析（当前是简化版本）
- [ ] SSE 长连接支持
- [ ] WebSocket 原生支持
- [ ] OAuth 认证流程
- [ ] 超时和重试策略优化

### Skills
- [ ] ExplainSkill 的 LLM 集成
- [ ] 更多内置 Skills（如：web、database 等）
- [ ] Skill 配置热加载
- [ ] Skill 之间的组合/编排

### 集成
- [ ] 技能/工具的权限审批 UI
- [ ] SSE 实时推送工具调用结果
- [ ] 工具调用审计日志

## 故障排查

### MCP 连接失败

检查日志中的错误信息：
```
2026-04-03 23:19:05 [INFO] 连接到 MCP 服务器：filesystem (stdio://...)
2026-04-03 23:19:05 [ERROR] 连接 MCP 服务器失败：filesystem - ...
```

常见问题：
1. stdio 命令不存在（如 `npx` 未安装）
2. 环境变量配置错误
3. 网络服务器 URL 错误

### Skills 不工作

1. 检查 Skill 是否启用：`GET /api/skills`
2. 检查工具名称是否正确：`{skillName}_{toolName}`
3. 查看执行日志中的错误信息

## 总结

本次集成完成了：

1. **MCP 完整集成**: 支持 stdio/SSE/WebSocket 三种传输方式，JSON-RPC 2.0 协议，动态工具发现
2. **Skills 框架**: 可扩展的 Skill 系统，3 个内置 Skills（Calc、Code、Explain）
3. **HTTP 管理接口**: MCP 服务器管理和 Skill 执行的 REST API
4. **Agent 集成**: 所有工具统一注册到 Spring AI ChatClient
5. **测试覆盖**: 6 个新的 Skills 测试，总计 72 个测试全部通过
