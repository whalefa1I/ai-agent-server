# ToolState 实现总结

## 已完成的工作

### 1. Java 后端代码 (ai-agent-server)

已创建以下文件：

```
src/main/java/demo/k8s/agent/
├── toolstate/
│   ├── ToolArtifact.java                    # 实体类 (JPA Entity)
│   ├── ToolArtifactRepository.java          # 数据访问层 (Repository)
│   ├── ToolStatus.java                      # 状态枚举
│   ├── ToolStateUpdateEvent.java            # 事件类
│   ├── ToolArtifactHeader.java              # Header 类型
│   ├── ToolArtifactBody.java                # Body 类型
│   ├── ToolEventRouter.java                 # 事件路由器 (WebSocket 广播)
│   ├── ToolStateService.java                # 业务逻辑层 (Service)
│   ├── ToolStateController.java             # HTTP API (REST Controller)
│   ├── ToolStateWebSocketHandler.java       # WebSocket 处理器
│   ├── ToolStateHandshakeInterceptor.java   # 握手拦截器
│   ├── ToolStateWebSocketConfig.java        # WebSocket 配置
│   └── ToolStateExample.java                # 使用示例代码
└── privacykit/
    └── PrivacyKitService.java               # Base64 编码/解码工具
```

### 2. 前端代码 (ai-agent-web)

已创建以下文件：

```
src/
├── types/tool-state.ts                      # TypeScript 类型定义
├── composables/useToolStateWebSocket.ts     # WebSocket composable
└── components/ToolStateDisplay.vue          # Vue 展示组件
```

### 3. 配置文件

```
ai-agent-server/
├── pom.xml                                  # 添加了 JPA + H2 依赖
├── src/main/resources/application.yml       # 添加了 H2 数据库配置
└── src/main/resources/db/migration/
    └── V1__create_tool_artifact_table.sql   # 数据库初始化脚本
```

### 4. 文档

```
ai-agent-server/docs/
├── tool-state-implementation.md             # 完整实现文档
├── tool-state-quick-reference.md            # 快速参考
└── tool-state-summary.md                    # 本文件 (总结)
```

## 核心功能

### 1. 工具状态管理

| 状态 | 说明 | Body 结构 |
|------|------|----------|
| todo | 待执行 | `{ "todo": "description" }` |
| plan | 计划中 | `{ "plan": ["step1", "step2"] }` |
| pending_confirmation | 待确认 | `{ "input": {...}, "confirmation": { "requested": true } }` |
| executing | 执行中 | `{ "input": {...}, "progress": "..." }` |
| completed | 已完成 | `{ "input": {...}, "output": {...} }` |
| failed | 已失败 | `{ "input": {...}, "error": "..." }` |

### 2. HTTP API

| 端点 | 说明 |
|------|------|
| `POST /api/v2/tool-state` | 创建工具状态 |
| `GET /api/v2/tool-state/{id}` | 获取工具状态 |
| `GET /api/v2/tool-state/session/{sessionId}` | 获取会话所有工具状态 |
| `PUT /api/v2/tool-state/{id}` | 更新工具状态 |
| `DELETE /api/v2/tool-state/{id}` | 删除工具状态 |

### 3. WebSocket 实时推送

- 端点：`/ws/tool-state?userId={userId}`
- 事件类型：
  - `new-tool-artifact` - 新建工具状态
  - `update-tool-artifact` - 更新工具状态
  - `delete-tool-artifact` - 删除工具状态

### 4. 乐观并发控制

通过 `bodyVersion` 字段实现：
- 更新时检查 `expectedVersion == currentVersion`
- 返回 0 表示版本冲突
- 前端需刷新本地状态后重试

## 技术栈对比

| 功能 | happy-server | Java 实现 |
|------|--------------|-----------|
| 通信协议 | Socket.IO | Spring WebSocket |
| 实时推送 | Socket.IO | WebSocket + SSE |
| ORM | Prisma | Spring Data JPA |
| 数据库 | PostgreSQL | H2 (开发) / PostgreSQL (生产) |
| 事件总线 | Redis Pub/Sub | Spring Application Events |
| 并发控制 | version 字段 | version 字段 + JPQL |

## 使用示例

### 后端集成

```java
@Autowired
private ToolStateService toolStateService;

// 创建工具状态
ToolArtifact artifact = toolStateService.createToolArtifact(
    sessionId, userId, "BashTool", "tool",
    ToolStatus.TODO, Map.of("todo", "Execute ls -la"),
    null
);

// 更新工具状态
toolStateService.updateToolArtifact(
    artifact.getId(), userId, ToolStatus.EXECUTING,
    Map.of("progress", "Running..."),
    artifact.getBodyVersion(), // 乐观锁版本号
    null
);
```

### 前端集成

```typescript
import { useToolStateWebSocket } from '@/composables/useToolStateWebSocket'

const { artifacts, updateArtifact } = useToolStateWebSocket(userId)

// 更新工具状态（如确认执行）
await updateArtifact(
  artifactId,
  'executing',
  { progress: 'running' },
  bodyVersion
)
```

## 下一步

### 1. 数据库初始化

```bash
cd ai-agent-server
# 启动应用时会自动创建 H2 数据库和表
./mvnw spring-boot:run
```

### 2. 测试 HTTP API

```bash
# 创建工具状态
curl -X POST http://localhost:8081/api/v2/tool-state \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session",
    "accountId": "test-user",
    "toolName": "BashTool",
    "toolType": "tool",
    "initialStatus": "todo",
    "body": { "todo": "Execute ls -la" }
  }'

# 获取工具状态
curl http://localhost:8081/api/v2/tool-state/session/test-session
```

### 3. 测试 WebSocket 连接

使用浏览器控制台或 wscat：

```bash
npm install -g wscat
wscat -c "ws://localhost:8081/ws/tool-state?userId=test-user"
```

### 4. 前端集成

在 Vue 组件中引入：

```vue
<script setup lang="ts">
import ToolStateDisplay from '@/components/ToolStateDisplay.vue'
</script>

<template>
  <ToolStateDisplay :sessionId="currentSessionId" :userId="currentUserId" />
</template>
```

## 注意事项

1. **数据库选择**：
   - 开发环境：H2 (内嵌，无需额外部署)
   - 生产环境：建议改用 PostgreSQL 或 MySQL

2. **加密存储**：
   - 当前 `PrivacyKitService` 仅实现 Base64 编码
   - 生产环境建议使用 AES 加密

3. **WebSocket 跨域**：
   - 当前配置了 `setAllowedOrigins("*")`
   - 生产环境应限制为具体域名

4. **版本冲突处理**：
   - 前端需要实现重试逻辑
   - 检测到版本冲突时，先刷新本地状态再重试

## 与 happy-server 的差异

| 方面 | happy-server | Java 实现 | 说明 |
|------|--------------|-----------|------|
| 加密 | privacy-kit (AES) | PrivacyKitService (Base64) | Java 实现可扩展为 AES |
| 连接管理 | Socket.IO 房间 | 手动管理 Set | Spring WebSocket 更底层 |
| 事件格式 | 二进制 + 文本 | 纯文本 JSON | Java 使用 JSON 序列化 |
| 数据库 | PostgreSQL (生产) | H2 (开发) + 可选 PostgreSQL | 开发更方便 |

## 技术难点解决

### 1. 乐观并发控制

**问题**：多个客户端同时更新同一工具状态

**解决**：
- 使用 `version` 字段进行 CAS 操作
- JPQL 更新时检查版本号
- 返回受影响行数判断是否成功

### 2. WebSocket 连接管理

**问题**：如何按用户分组广播

**解决**：
- `ConcurrentHashMap<String, Set<WebSocketSession>>` 存储用户连接
- 握手拦截器提取 userId
- 广播时遍历对应用户的所有连接

### 3. 版本冲突处理

**问题**：前端如何知道版本冲突

**解决**：
- 返回 409 Conflict 状态码
- Response body 包含 `reason: "version-mismatch"` 和 `currentVersion`
- 前端刷新后重试

## 总结

本实现方案成功复现了 happy-server 的核心架构：
- ✅ Artifact 系统（ToolArtifact）
- ✅ EventRouter（ToolEventRouter）
- ✅ 乐观并发控制
- ✅ WebSocket 实时推送
- ✅ 加密存储（Base64，可扩展为 AES）

**实现难度**：中等（需要 Spring Boot、JPA、WebSocket 知识）

**工作量估算**：
- 后端代码：~10 个类
- 前端代码：~3 个文件
- 配置和文档：~4 个文件
- 联调测试：1-2 天

**推荐部署方式**：
1. 本地开发：H2 数据库 + WebSocket
2. 生产部署：PostgreSQL + WebSocket（考虑使用 Redis 做分布式事件总线）
