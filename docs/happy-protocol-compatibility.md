# Happy Protocol 兼容层实现文档

## 概述

本文档说明了为 AI Agent Server 项目实现的 Happy Protocol 兼容层，用于支持给领导汇报演示。该兼容层基于 G:\projects\happy 项目的设计，实现了完整的会话协议、工具状态管理和消息显示功能。

---

## 实现日期

2026-04-06

---

## 核心组件

### 1. 类型定义层 (`src/types/happy-protocol.ts`)

定义了所有 Happy Protocol 相关的 TypeScript 类型：

```typescript
// 9 种事件类型
export type HappyEventType =
  | 'text'
  | 'service'
  | 'tool-call-start'
  | 'tool-call-end'
  | 'file'
  | 'turn-start'
  | 'turn-end'
  | 'start'
  | 'stop';

// 消息信封格式
export interface HappyMessage {
  id: string;           // cuid2 格式
  time: number;         // Unix 时间戳 (毫秒)
  role: HappyRole;
  turn?: string;        // turn id
  subagent?: string;    // 子代理 id
  ev: HappyEvent;
}

// 工具调用
export interface ToolCall {
  id: string;
  name: string;
  title: string;
  description: string;
  args: Record<string, unknown>;
  result?: unknown;
  state: 'running' | 'completed' | 'error';
  startTime: number;
  endTime?: number;
  durationMs?: number;
}
```

### 2. HTTP API 客户端 (`src/tools/api/artifact-api.ts`)

与后端 `/api/v1/artifacts` 端点交互：

```typescript
// 获取会话的所有 artifacts
fetchArtifacts(accountId: string): Promise<BackendArtifact[]>

// 获取单个 artifact
fetchArtifact(accountId: string, artifactId: string): Promise<BackendArtifact>

// 获取会话工具状态
fetchSessionArtifacts(sessionId: string): Promise<BackendArtifact[]>

// 创建/更新/删除 artifact
createArtifact(params): Promise<BackendArtifact>
updateArtifact(artifactId, params): Promise<UpdateResponse>
deleteArtifact(accountId, artifactId): Promise<void>
```

### 3. 转换器 (`src/tools/transformers/artifact-transformer.ts`)

将后端 ToolArtifact 转换为 Happy Protocol 消息格式：

```typescript
// Artifact → Message
artifactToMessage(artifact: BackendArtifact): Message | null

// Header → ToolCall
headerToToolCall(artifact, header, body): ToolCall | null

// 批量转换
artifactsToMessages(artifacts: BackendArtifact[]): Message[]
```

### 4. 状态管理 (`src/tools/hooks/use-tool-state.ts`)

工具状态管理 Class：

```typescript
class ToolStateManager {
  setSession(sessionId, accountId): Promise<void>
  refresh(): Promise<void>
  addToolCall(toolCall: ToolCall): void
  updateToolCall(toolCallId, updates): void
  clear(): void
  loadAccountArtifacts(accountId): Promise<BackendArtifact[]>
}
```

### 5. WebSocket 客户端 (`src/tools/hooks/use-tool-state-websocket.ts`)

实时工具状态推送客户端：

```typescript
class ToolStateWebSocketClient {
  connect(token?: string): void
  disconnect(): void
  subscribeState(listener): () => void
  subscribeMessage(listener): () => void
}
```

### 6. 工具注册表 (`src/tools/registry/known-tools.ts`)

定义 50+ 个已知工具的显示属性：

```typescript
export const knownTools: Record<string, ToolDefinition> = {
  'bash': { ... },
  'glob': { ... },
  'grep': { ... },
  'file_read': { ... },
  'file_write': { ... },
  'file_edit': { ... },
  'todo_write': { ... },
  // ... 更多工具
};
```

每个工具定义包含：
- `title` - 标题（静态或动态函数）
- `icon` - 图标函数
- `minimal` - 是否最小化显示
- `extractDescription` - 提取描述
- `extractSubtitle` - 提取副标题
- `extractStatus` - 提取状态
- `isMutable` - 是否可变（可能修改文件）
- `noStatus` - 是否无状态
- `hideDefaultError` - 是否隐藏默认错误

### 7. Vue 组件

#### ToolCallView.vue
单个工具调用的视图组件：
- 支持展开/收起
- 显示输入参数和输出结果
- 状态指示器
- 自定义图标

#### StatusIndicator.vue
状态指示器组件：
- Running（黄色脉冲）
- Completed（绿色）
- Error（红色）

#### MessageView.vue
消息视图组件：
- Text Message（文本消息）
- Service Message（服务消息）
- Tool Call Message（工具调用）
- File Message（文件消息）

#### ToolCalls.vue
工具调用列表容器：
- 支持 Happy Protocol 模式
- 支持传统模式（向后兼容）

---

## 后端支持

### 已实现的后端端点

#### HTTP API

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/v1/artifacts` | GET | 获取所有 artifacts |
| `/api/v1/artifacts/:id` | GET | 获取单个 artifact |
| `/api/v1/artifacts` | POST | 创建 artifact |
| `/api/v1/artifacts/:id` | POST | 更新 artifact |
| `/api/v1/artifacts/:id` | DELETE | 删除 artifact |
| `/api/v2/tool-state/session/:sessionId` | GET | 获取会话工具状态 |

#### WebSocket

| 端点 | 描述 |
|------|------|
| `/ws/tool-state` | 工具状态推送端点 |

### ToolArtifact 数据结构

```java
@Entity
@Table(name = "tool_artifact")
public class ToolArtifact {
    @Id
    private String id;
    
    private String sessionId;
    private String accountId;
    
    /**
     * Header: Base64 编码的 JSON
     * {"type": "tool", "toolName": "BashTool", "title": "...", "status": "running"}
     */
    private String header;
    private int headerVersion;
    
    /**
     * Body: Base64 编码的 JSON
     * {"input": {...}, "output": {...}, "status": "completed"}
     */
    private String body;
    private int bodyVersion;
    
    private long seq;
    private Instant createdAt;
    private Instant updatedAt;
}
```

---

## 使用示例

### React Hook 使用方式

```typescript
import { toolStateManager } from '@/tools/hooks/use-tool-state';
import { toolStateWebSocketClient } from '@/tools/hooks/use-tool-state-websocket';

// 设置会话
await toolStateManager.setSession(sessionId, accountId);

// 连接 WebSocket
toolStateWebSocketClient.connect();

// 订阅状态变化
toolStateManager.subscribe(() => {
  const state = toolStateManager.getState();
  console.log('Tool calls:', state.toolCalls);
  console.log('Messages:', state.messages);
});

// 添加工具调用
toolStateManager.addToolCall({
  id: 'call_123',
  name: 'bash',
  title: 'Run command',
  description: 'ls -la',
  args: { command: 'ls -la' },
  state: 'running',
  startTime: Date.now()
});
```

### Vue 组件使用方式

```vue
<template>
  <div>
    <MessageView 
      v-for="message in messages" 
      :key="message.id"
      :message="message"
      :metadata="metadata"
    />
  </div>
</template>

<script setup lang="ts">
import { MessageView } from '@/components/MessageView.vue';
import type { Message, Metadata } from '@/types/happy-protocol';

const props = defineProps<{
  messages: Message[];
  metadata?: Metadata;
}>();
</script>
```

---

## 与 Happy 项目的兼容性

### 已实现的功能

| 功能 | Happy 项目 | 当前实现 | 状态 |
|------|-----------|---------|------|
| 9 种事件类型 | ✅ | ✅ | 完成 |
| Message 信封格式 | ✅ | ✅ | 完成 |
| Tool Call 生命周期 | ✅ | ✅ | 完成 |
| Artifact Header/Body | ✅ | ✅ | 完成 |
| Base64 加密存储 | ✅ | ✅ | 完成 |
| 工具注册表 | ✅ | ✅ | 完成 |
| WebSocket 推送 | ✅ | ✅ | 完成 |
| HTTP API | ✅ | ✅ | 完成 |

### 待实现的功能

1. **完整加密**：当前使用 Base64 编码，未来可实现 AES-256 加密
2. **cuid2 ID 生成**：当前使用简化实现，可集成真正的 cuid2 库
3. **更多工具类型**：可扩展 knownTools 注册表支持更多工具

---

## 演示场景

### 场景 1：文件操作演示

```
用户：查看当前目录文件
→ glob 工具调用（搜索 *.ts 文件）
→ 显示文件列表

用户：读取 package.json
→ file_read 工具调用
→ 显示文件内容

用户：修改配置
→ file_edit 工具调用
→ 显示 diff
```

### 场景 2：终端命令演示

```
用户：运行 npm install
→ bash 工具调用
→ 显示命令执行过程
→ 显示输出结果
```

### 场景 3：任务管理演示

```
用户：创建任务列表
→ todo_write 工具调用
→ 显示任务列表
→ 标记任务完成状态
```

---

## 文件清单

### 新建文件

```
src/types/happy-protocol.ts                    # 类型定义
src/tools/api/artifact-api.ts                  # HTTP API 客户端
src/tools/transformers/artifact-transformer.ts # 转换器
src/tools/hooks/use-tool-state.ts              # 状态管理 Hook
src/tools/hooks/use-tool-state-websocket.ts    # WebSocket 客户端
src/tools/registry/known-tools.ts              # 工具注册表
src/components/ToolCallView.vue                # 工具视图组件
src/components/StatusIndicator.vue             # 状态指示器
src/components/MessageView.vue                 # 消息视图组件
src/components/ToolCalls.vue                   # 工具列表（更新）
docs/happy-protocol-compatibility.md           # 本文档
```

### 修改文件

```
src/components/ToolCalls.vue                   # 集成新组件
```

---

## 验证步骤

### 1. 编译验证

```bash
cd G:\project\ai-agent-server
./mvnw.cmd clean compile
```

### 2. TypeScript 类型检查

```bash
npx tsc --noEmit
```

### 3. 功能测试

1. 启动应用
2. 访问前端页面
3. 执行工具调用（bash、glob、grep、read 等）
4. 验证工具块正确显示
5. 验证 WebSocket 连接状态

---

## 后续优化建议

1. **添加单元测试**：为转换器和工具注册表添加测试
2. **完善错误处理**：增强边界情况处理
3. **性能优化**：实现消息缓存和增量更新
4. **扩展工具类型**：支持更多 AI 工具类型（WebFetch、Diff 等）
5. **主题支持**：支持暗色/亮色主题切换

---

**文档版本**: 1.0
**最后更新**: 2026-04-06
