# Java 实现工具调用状态展示 - 完整方案

## 一、架构概述

### happy-server 架构参考

| 组件 | happy-server (TypeScript) | Java/Spring Boot 实现 |
|------|---------------------------|----------------------|
| 通信协议 | Socket.IO (WebSocket) | Spring WebSocket + SSE |
| ORM | Prisma | Spring Data JPA |
| 事件总线 | Redis Pub/Sub + eventRouter | Spring Application Events + ToolEventRouter |
| 状态持久化 | Artifact (header/body) | ToolArtifact (header/body) |
| 并发控制 | 乐观锁 (version 字段) | 乐观锁 (version 字段 + JPQL) |

### 架构设计图

```
┌─────────────┐     HTTP REST      ┌──────────────────┐
│   Frontend  │ ◄────────────────► │ ToolStateController │
│  (Vue.js)   │                    └──────────────────┘
└─────────────┘                           │
       │                                  ▼
       │                    ┌──────────────────┐
       │ WebSocket          │ ToolStateService │
       ◄───────────────────►│                  │
       │                    └──────────────────┘
       │                           │
       │                           ▼
       │                    ┌──────────────────┐
       │                    │ ToolEventRouter  │
       │                    └──────────────────┘
       │                           │
       │                           ▼
       │                    ┌──────────────────┐
       └───────────────────►│ ToolArtifact     │
             广播更新            │ Repository       │
                            └──────────────────┘
                                   │
                                   ▼
                            ┌──────────────────┐
                            │  PostgreSQL DB   │
                            └──────────────────┘
```

## 二、数据库 Schema

```sql
CREATE TABLE tool_artifact (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    
    -- Header: 工具元数据 (JSON)
    header TEXT NOT NULL,
    header_version INT DEFAULT 0,
    
    -- Body: 工具状态详情 (JSON)
    body TEXT,
    body_version INT DEFAULT 0,
    
    seq BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_account (account_id),
    INDEX idx_session (session_id),
    INDEX idx_updated (account_id, updated_at DESC)
);
```

## 三、Java 核心类

### 1. ToolArtifact (实体类)
- `id`, `sessionId`, `accountId`
- `header`, `headerVersion` - 工具元数据
- `body`, `bodyVersion` - 工具状态详情
- 支持 `@PreUpdate` 自动更新时间戳

### 2. ToolArtifactRepository (数据访问层)
- `updateHeaderOptimistic()` - 乐观并发更新 header
- `updateBodyOptimistic()` - 乐观并发更新 body
- 返回受影响行数（0 表示版本冲突）

### 3. ToolEventRouter (事件路由器)
- 管理 WebSocket 连接（按用户分组）
- `emitToolStateUpdate()` - 广播状态更新
- 支持跳过发送者（避免 echo）

### 4. ToolStateService (业务逻辑层)
- `createToolArtifact()` - 创建工具状态
- `updateToolArtifact()` - 更新工具状态（乐观并发）
- `deleteToolArtifact()` - 删除工具状态

### 5. ToolStateController (HTTP API)
- `POST /api/v2/tool-state` - 创建
- `GET /api/v2/tool-state/{id}` - 查询
- `PUT /api/v2/tool-state/{id}` - 更新
- `DELETE /api/v2/tool-state/{id}` - 删除

### 6. ToolStateWebSocketHandler (WebSocket 处理器)
- 处理客户端连接/断开
- 注册连接到 ToolEventRouter

### 7. ToolStateWebSocketConfig (WebSocket 配置)
- 注册 WebSocket 处理器
- 配置握手拦截器

## 四、工具状态流转

```
todo → plan → pending_confirmation → executing → completed
                                    ↓
                                   failed
```

### 各状态 Body 结构

| 状态 | Body 字段 |
|------|----------|
| todo | `{ "todo": "description" }` |
| plan | `{ "plan": ["step1", "step2"] }` |
| pending_confirmation | `{ "input": {...}, "confirmation": { "requested": true } }` |
| executing | `{ "input": {...}, "progress": "running" }` |
| completed | `{ "input": {...}, "output": {...} }` |
| failed | `{ "input": {...}, "error": "message" }` |

## 五、前端集成

### TypeScript 类型定义 (`src/types/tool-state.ts`)

```typescript
type ToolStatus = 'todo' | 'plan' | 'pending_confirmation' | 'executing' | 'completed' | 'failed'

interface ToolArtifact {
  id: string
  header: { name: string; type: string; status: ToolStatus; version: number }
  body: { todo?: string; plan?: string[]; input?: any; output?: any; ... }
  bodyVersion: number
}
```

### WebSocket Composable (`src/composables/useToolStateWebSocket.ts`)

```typescript
const { artifacts, isConnected, updateArtifact } = useToolStateWebSocket(userId)
```

### Vue 组件 (`src/components/ToolStateDisplay.vue`)

```vue
<ToolStateDisplay :sessionId="currentSessionId" :userId="currentUserId" />
```

## 六、乐观并发控制示例

### Java 后端更新逻辑

```java
@Modifying
@Query("UPDATE ToolArtifact a SET a.body = :body, a.bodyVersion = a.bodyVersion + 1 " +
       "WHERE a.id = :id AND a.accountId = :accountId AND a.bodyVersion = :expectedVersion")
int updateBodyOptimistic(...);

// 返回 0 表示版本冲突（被其他客户端抢先更新）
```

### 前端处理版本冲突

```typescript
async function updateArtifact(artifactId, status, body, expectedVersion) {
  const response = await fetch(`/api/v2/tool-state/${artifactId}`, {
    method: 'PUT',
    body: JSON.stringify({ expectedVersion, ... })
  })
  
  const data = await response.json()
  if (data.reason === 'version-mismatch') {
    // 版本冲突，刷新本地状态后重试
    await fetchSessionArtifacts(sessionId)
  }
}
```

## 七、与 happy-server 对比

| 特性 | happy-server | Java 实现 | 说明 |
|------|--------------|-----------|------|
| 双向通信 | ✓ (Socket.IO) | ✓ (WebSocket) | Java 使用 Spring WebSocket |
| 单向推送 | ✓ (SSE) | ✓ (SseEmitter) | 用于 AI 响应流式输出 |
| 状态持久化 | ✓ (Prisma) | ✓ (JPA) | 都支持事务和乐观锁 |
| 事件广播 | ✓ (eventRouter) | ✓ (ToolEventRouter) | 实现逻辑相同 |
| 加密存储 | ✓ (privacy-kit) | ✓ (PrivacyKitService) | Base64 编码 |
| 乐观并发 | ✓ (version) | ✓ (version) | 都通过 version 字段检查 |

## 八、部署步骤

### 1. 数据库迁移

```sql
-- 执行上述 CREATE TABLE 语句创建 tool_artifact 表
```

### 2. 添加依赖 (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

### 3. 配置 WebSocket (application.yml)

```yaml
spring:
  websocket:
    enabled: true
```

### 4. 前端配置 (vite.config.ts)

```typescript
// WebSocket 代理（可选）
server: {
  proxy: {
    '/ws': {
      target: 'http://localhost:8081',
      ws: true  // 启用 WebSocket 代理
    }
  }
}
```

## 九、使用示例

### 后端调用（工具执行时）

```java
@Autowired
private ToolStateService toolStateService;

public void executeTool(String sessionId, String userId) {
    // 1. 创建工具状态 (todo)
    ToolArtifact artifact = toolStateService.createToolArtifact(
        sessionId, userId, "BashTool", "tool",
        ToolStatus.TODO, Map.of("todo", "Execute ls -la"),
        null
    );
    
    // 2. 更新为计划中
    toolStateService.updateToolArtifact(
        artifact.getId(), userId, ToolStatus.PLAN,
        Map.of("plan", List.of("Step 1: Run command")),
        1, null
    );
    
    // 3. 更新为待确认（如果需要）
    toolStateService.updateToolArtifact(
        artifact.getId(), userId, ToolStatus.PENDING_CONFIRMATION,
        Map.of("input", Map.of("command", "ls -la"),
               "confirmation", Map.of("requested", true)),
        2, null
    );
    
    // 4. 等待用户确认后执行...
}
```

### 前端监听（Vue 组件）

```typescript
// 自动连接 WebSocket 并接收实时更新
const { artifacts, isConnected } = useToolStateWebSocket(userId)

// 获取会话的所有工具状态
await fetchSessionArtifacts(sessionId)

// 更新工具状态（如确认执行）
await updateArtifact(artifactId, 'executing', { progress: 'running' }, bodyVersion)
```

## 十、总结

### 实现难度：⭐⭐⭐ (中等)

**主要工作量**：
1. 数据库 schema 设计
2. 实体类和 Repository 编写
3. WebSocket 连接管理
4. 工具状态机设计

**技术成熟度**：
- Spring WebSocket - 成熟稳定
- Spring Data JPA - 官方支持
- 乐观并发控制 - 标准模式

**与 happy-server 的差异**：
- 通信协议：Socket.IO vs Spring WebSocket（功能对等）
- ORM: Prisma vs JPA（功能对等）
- 事件总线：Redis Pub/Sub vs Spring Application Events（功能对等）

**推荐架构**：
- **SSE** 用于 AI 响应流式输出（单向，简单高效）
- **WebSocket** 用于工具状态更新（双向，支持用户确认）

### 下一步

1. 创建数据库表
2. 编译 Java 代码
3. 启动后端服务测试 WebSocket 连接
4. 前端集成 ToolStateDisplay 组件
5. 联调测试完整流程
