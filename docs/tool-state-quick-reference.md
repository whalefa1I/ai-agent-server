# ToolState 快速参考

## 已创建的文件

### Java 后端 (ai-agent-server)

```
src/main/java/demo/k8s/agent/toolstate/
├── ToolArtifact.java              # 实体类
├── ToolArtifactRepository.java    # 数据访问层
├── ToolStatus.java                # 状态枚举
├── ToolStateUpdateEvent.java      # 事件类
├── ToolArtifactHeader.java        # Header 类型
├── ToolArtifactBody.java          # Body 类型
├── ToolEventRouter.java           # 事件路由器
├── ToolStateService.java          # 业务逻辑层
├── ToolStateController.java       # HTTP API
├── ToolStateWebSocketHandler.java # WebSocket 处理器
├── ToolStateHandshakeInterceptor.java # 握手拦截器
└── ToolStateWebSocketConfig.java  # WebSocket 配置

src/main/java/demo/k8s/agent/privacykit/
└── PrivacyKitService.java         # Base64 编码/解码工具

docs/
└── tool-state-implementation.md   # 完整实现文档
```

### 前端 (ai-agent-web)

```
src/
├── types/tool-state.ts                      # TypeScript 类型定义
├── composables/useToolStateWebSocket.ts     # WebSocket composable
└── components/ToolStateDisplay.vue          # 工具状态展示组件
```

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v2/tool-state` | 创建工具状态 |
| GET | `/api/v2/tool-state/{id}?accountId=xxx` | 获取工具状态 |
| GET | `/api/v2/tool-state/session/{sessionId}` | 获取会话所有工具状态 |
| PUT | `/api/v2/tool-state/{id}` | 更新工具状态 |
| DELETE | `/api/v2/tool-state/{id}?accountId=xxx` | 删除工具状态 |

## WebSocket 端点

```
/ws/tool-state?userId={userId}
```

## 状态流转

```
todo → plan → pending_confirmation → executing → completed
                                    ↓
                                   failed
```

## 使用示例

### 后端创建工具状态

```java
@Autowired
private ToolStateService toolStateService;

ToolArtifact artifact = toolStateService.createToolArtifact(
    sessionId,      // 会话 ID
    userId,         // 账户 ID
    "BashTool",     // 工具名称
    "tool",         // 工具类型
    ToolStatus.TODO,// 初始状态
    Map.of("todo", "Execute command"), // body
    null            // WebSocket session (跳过 echo)
);
```

### 前端监听更新

```typescript
import { useToolStateWebSocket } from '@/composables/useToolStateWebSocket'

const { artifacts, isConnected, updateArtifact } = useToolStateWebSocket(userId)

// 更新工具状态
await updateArtifact(
  artifactId,
  'executing',
  { progress: 'running' },
  bodyVersion // 乐观锁版本号
)
```

## 乐观并发控制

```typescript
// 1. 获取当前版本 (假设 bodyVersion=1)
const artifact = artifacts.value.find(a => a.id === targetId)

// 2. 更新时指定期望版本
const success = await updateArtifact(
  targetId,
  'executing',
  { progress: 'running' },
  artifact.bodyVersion // expectedVersion = 1
)

// 3. 如果返回 version-mismatch，说明被其他客户端抢先更新
if (!success) {
  // 刷新本地状态后重试
}
```

## 数据库表结构

```sql
CREATE TABLE tool_artifact (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    header TEXT NOT NULL,           -- JSON: {name, type, status, version}
    header_version INT DEFAULT 0,
    body TEXT,                      -- JSON: {todo/plan/input/output/error, version}
    body_version INT DEFAULT 0,
    seq BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 关键代码位置

| 功能 | 文件 | 说明 |
|------|------|------|
| 乐观并发更新 | ToolArtifactRepository.java | `updateBodyOptimistic()` |
| 事件广播 | ToolEventRouter.java | `emitToolStateUpdate()` |
| WebSocket 连接管理 | ToolStateWebSocketHandler.java | `afterConnectionEstablished()` |
| 版本冲突处理 | ToolStateService.java | `UpdateResult` 类 |

## 注意事项

1. **数据库迁移**：需要先创建 `tool_artifact` 表
2. **依赖注入**：`ToolStateService` 需要 `PrivacyKitService`（已创建）
3. **WebSocket 跨域**：配置了 `setAllowedOrigins("*")`，生产环境应限制
4. **加密存储**：当前 `PrivacyKitService` 仅实现 Base64，可扩展为 AES 加密
