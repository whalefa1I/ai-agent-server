# Phase 2/3/4 实现总结

## 概览

本文档记录了 Phase 2（记忆系统核心）、Phase 3（Plugin Hooks 系统）和 Phase 4（飞书频道集成）的完整实现。

**实现日期**: 2026-04-04

**状态**: ✅ 全部完成

---

## 编译要求

本项目需要 **Java 21** 和 **Maven 3.6+** 进行编译。

```bash
# 使用 Java 21 编译
export JAVA_HOME=/path/to/jdk21
mvn clean compile

# 或使用 Maven Wrapper
./mvnw clean compile
```

**注意**: 使用 Java 8 编译会失败，因为项目使用了 Java 21 的特性（如 record、switch 表达式等）。

## Phase 2: 记忆系统核心 ✅

### 已实现组件

| 组件 | 文件路径 | 状态 |
|------|----------|------|
| EmbeddingService | `memory/embedding/EmbeddingService.java` | ✅ 完成 |
| MemoryEntry | `memory/model/MemoryEntry.java` | ✅ 完成 |
| VectorStore (接口) | `memory/store/VectorStore.java` | ✅ 完成 |
| InMemoryVectorStore | `memory/store/InMemoryVectorStore.java` | ✅ 完成 |
| MemoryIndexer | `memory/index/MemoryIndexer.java` | ✅ 完成 |
| MemorySearchService | `memory/search/MemorySearchService.java` | ✅ 完成 |
| MemorySearchTool | `tools/local/memory/MemorySearchTool.java` | ✅ 完成 |
| MemoryFileLoader | `memory/MemoryFileLoader.java` | ✅ 完成 |
| MemoryToolInitializer | `tools/local/memory/MemoryToolInitializer.java` | ✅ 完成 |

### 核心功能

1. **向量嵌入**
   - 使用 Spring AI 的 `EmbeddingModel` 将文本转换为 float[] 向量
   - 支持批量嵌入

2. **记忆存储**
   - `MemoryEntry` 包含内容、向量、来源、会话 ID、时间戳、元数据
   - 记忆来源：CONVERSATION、FILE、USER_NOTE、MEMORY_FILE
   - `InMemoryVectorStore` 提供开发/测试用存储（重启后数据丢失）

3. **语义搜索**
   - 余弦相似度计算
   - 可配置的最大结果数和相似度阈值
   - 支持按 sessionId 和 source 过滤

4. **MEMORY.md 支持**
   - 自动从 `~/.claude/MEMORY.md` 加载记忆
   - 解析 frontmatter 元数据（name、description、type）
   - 应用启动时自动索引

5. **Agent 集成**
   - `EnhancedAgenticQueryLoop` 在系统提示词中注入相关记忆
   - `memory_search` 工具允许 Agent 主动搜索记忆

### 使用示例

```bash
# 创建 MEMORY.md 文件
mkdir -p ~/.claude
cat > ~/.claude/MEMORY.md << 'EOF'
---
name: project_context
description: 项目背景和关键决策
type: project
---

这个项目是一个 Spring AI Agent 演示，主要功能包括：
- K8s Job 沙盒
- 工具调用系统
- 记忆系统

---
name: user_preferences
description: 用户偏好
type: feedback
---

用户喜欢喝拿铁咖啡，偏好简洁的代码风格。
EOF

# 启动应用后，记忆会自动加载
# Agent 会在系统提示词中收到相关记忆片段

# 使用 memory_search 工具
openclaw message send "搜索一下关于项目背景的记忆"
```

---

## Phase 3: Plugin Hooks 系统 ✅

### 已实现组件

| 组件 | 文件路径 | 状态 |
|------|----------|------|
| HookPhase (枚举) | `plugin/hook/HookPhase.java` | ✅ 完成 |
| HookType (枚举) | `plugin/hook/HookType.java` | ✅ 完成 |
| Hook (接口) | `plugin/hook/Hook.java` | ✅ 完成 |
| HookRegistry | `plugin/hook/HookRegistry.java` | ✅ 完成 |
| HookExecutor | `plugin/hook/HookExecutor.java` | ✅ 完成 |
| HookService | `plugin/hook/HookService.java` | ✅ 完成 |
| WorkspaceHookLoader | `plugin/hook/WorkspaceHookLoader.java` | ✅ 完成 |
| HookProperties | `config/HookProperties.java` | ✅ 完成 |
| Hook Events | `plugin/hook/event/*.java` | ✅ 完成 |
| ToolCallLoggingHook (示例) | `plugin/hook/examples/ToolCallLoggingHook.java` | ✅ 完成 |

### 核心功能

1. **Hook 类型和阶段**
   - `HookType`: TOOL_CALL, MODEL_CALL, MESSAGE_RECEIVED, SESSION_STARTED, AGENT_TURN, etc.
   - `HookPhase`: BEFORE, AFTER, AROUND

2. **Hook 注册表**
   - 按类型和 ID 索引
   - 优先级排序
   - 动态注册/注销

3. **Hook 执行器**
   - BEFORE Hook 可短路（阻止目标操作）
   - AFTER Hook 用于日志/指标/后处理
   - AROUND Hook 完全控制执行流程

4. **JavaScript Hook 支持**
   - 从 `.openclaw/hooks/` 目录加载 `.js` 脚本
   - 使用 GraalVM JavaScript 引擎执行
   - 元数据注释格式：`// @hook`, `// @name`, `// @phase`

5. **集成到 Query Loop**
   - 工具调用前后触发 Hook
   - Hook 可阻止工具调用执行

### JavaScript Hook 示例

```javascript
// .openclaw/hooks/before-tool-call.js
// @hook tool-call
// @name LogToolCalls
// @description Log all tool calls to console
// @phase before
// @priority 100

function execute(context) {
    const toolName = context.getData("toolName");
    const input = context.getData("input");
    console.log("Tool called: " + toolName);
    return true; // 继续执行
}
```

### Hook 配置

```yaml
demo:
  hooks:
    enabled: true
    workspace-dir: ""  # 默认 .openclaw/hooks/
    allowed-hooks: []  # 空表示允许所有
    timeout-ms: 5000
    async: false
```

---

## Phase 4: 飞书频道集成 ✅

### 已实现组件

| 组件 | 文件路径 | 状态 |
|------|----------|------|
| FeishuProperties | `channels/feishu/FeishuProperties.java` | ✅ 完成 |
| FeishuClient | `channels/feishu/FeishuClient.java` | ✅ 完成 |
| FeishuMessageAdapter | `channels/feishu/FeishuMessageAdapter.java` | ✅ 完成 |
| FeishuChannelService | `channels/feishu/FeishuChannelService.java` | ✅ 完成 |
| FeishuController | `channels/feishu/FeishuController.java` | ✅ 完成 |

### 核心功能

1. **飞书 API 客户端**
   - 自动获取/刷新 Access Token（租户级别）
   - 发送文本/富文本/卡片消息
   - 回复消息

2. **消息适配器**
   - 飞书消息 → ChatMessage 转换
   - 支持 text/post 消息类型

3. **事件处理**
   - URL 验证（`url_verification`）
   - 消息事件（`im.message.receive_v1`）
   - 会话映射（chat_id → session_id）

4. **HTTP 端点**
   - `POST /api/channels/feishu/event` - 事件接收
   - `GET /api/channels/feishu/health` - 健康检查
   - `GET /api/channels/feishu/stats` - 会话统计

### 配置

```yaml
demo:
  channels:
    feishu:
      enabled: true
      app-id: "your_app_id"
      app-secret: "your_app_secret"
      verification-token: "your_verification_token"
      encrypt-key: ""  # 可选
      api-base-url: "https://open.feishu.cn/open-apis"
```

### 部署步骤

1. **创建飞书应用**
   - 访问 https://open.feishu.cn/
   - 创建企业自建应用
   - 获取 App ID 和 App Secret

2. **配置应用权限**
   - 添加权限：`发送消息到单聊或群聊`
   - 添加权限：`获取用户 userID`

3. **配置事件订阅**
   - 启用事件订阅
   - 配置回调 URL：`https://your-domain.com/api/channels/feishu/event`
   - 使用 verification token 验证

4. **添加到群聊**
   - 将 Bot 添加到群聊
   - 配置机器人可@它接收消息

5. **启动应用**
   ```bash
   export DEMO_FEISHU_ENABLED=true
   export DEMO_FEISHU_APP_ID=your_app_id
   export DEMO_FEISHU_APP_SECRET=your_app_secret
   export DEMO_FEISHU_VERIFICATION_TOKEN=your_token
   ./mvnw spring-boot:run
   ```

---

## 依赖变更

### pom.xml 已添加的依赖

```xml
<!-- SQLite for Vector Store -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.46.0.1</version>
</dependency>

<!-- GraalVM JS for Hook Execution -->
<dependency>
    <groupId>org.graalvm.js</groupId>
    <artifactId>js</artifactId>
    <version>23.0.0</version>
</dependency>
<dependency>
    <groupId>org.graalvm.js</groupId>
    <artifactId>scriptengine</artifactId>
    <version>23.0.0</version>
</dependency>

<!-- WebFlux for Feishu Channel -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

---

## 新增工具

| 工具名称 | 描述 | 类别 |
|----------|------|------|
| `memory_search` | 搜索跨会话记忆 | MEMORY |

---

## 新增配置项

```yaml
demo:
  hooks:
    enabled: true
    workspace-dir: ""
    allowed-hooks: []
    timeout-ms: 5000
    async: false
  
  channels:
    feishu:
      enabled: false
      app-id: ""
      app-secret: ""
      verification-token: ""
      encrypt-key: ""
      api-base-url: "https://open.feishu.cn/open-apis"
```

---

## 验证方案

### Phase 2: 记忆系统验证

```bash
# 1. 创建 MEMORY.md 文件
cat > ~/.claude/MEMORY.md << 'EOF'
---
name: test_memory
description: 测试记忆
type: user
---

这是一个测试记忆条目。
EOF

# 2. 启动应用，观察日志
# 应看到：Loaded and indexed X memory entries from MEMORY.md

# 3. 测试语义搜索
openclaw message send "搜索测试记忆"

# 4. 测试跨会话记忆
# Session 1: "记住我喜欢喝拿铁"
# Session 2: "我喜欢的咖啡是什么？"
```

### Phase 3: Plugin Hooks 验证

```bash
# 1. 创建工作区 Hook
mkdir -p .openclaw/hooks
cat > .openclaw/hooks/before-tool-call.js << 'EOF'
// @hook tool-call
// @name LogToolCalls
// @description Log all tool calls
// @phase before

function execute(context) {
    const toolName = context.getData("toolName");
    console.log("Hook: Tool " + toolName + " is being called");
    return true;
}
EOF

# 2. 启动应用，观察日志
# 应看到：Registered hook: LogToolCalls

# 3. 执行工具调用
openclaw message send "列出当前目录的文件"

# 4. 观察 Hook 触发日志
```

### Phase 4: 飞书频道验证

```bash
# 1. 配置环境变量
export DEMO_FEISHU_ENABLED=true
export DEMO_FEISHU_APP_ID=cli_xxx
export DEMO_FEISHU_APP_SECRET=xxx
export DEMO_FEISHU_VERIFICATION_TOKEN=xxx

# 2. 启动应用

# 3. 在飞书配置回调 URL
# https://your-domain.com/api/channels/feishu/event

# 4. 在飞书群聊中@Bot 发送消息
# 应看到 Agent 响应

# 5. 检查健康端点
curl http://localhost:8080/api/channels/feishu/health
curl http://localhost:8080/api/channels/feishu/stats
```

---

## 注意事项

1. **InMemoryVectorStore** 重启后数据会丢失，生产环境应使用 `SQLiteVectorStore`（待实现）
2. **Hook 性能**：BEFORE Hook 会阻塞工具调用执行，建议保持 Hook 逻辑轻量
3. **飞书 Token**：Access Token 自动刷新（2 小时有效期），无需手动干预
4. **会话映射**：飞书会话使用 `chat_id` 作为键，长期记忆需实现持久化

---

## 后续改进建议

1. **记忆系统**
   - 实现 `SQLiteVectorStore` 支持持久化存储
   - 添加混合搜索（向量 + 关键词 BM25）
   - 支持记忆过期/归档策略

2. **Hook 系统**
   - 添加 Hook 配置 UI Schema
   - 支持 TypeScript Hook（编译后执行）
   - 添加 Hook 性能监控和超时熔断

3. **飞书频道**
   - 支持卡片消息模板
   - 支持消息加密/解密
   - 添加频道级别的会话管理
