# Happy Protocol 工具样式与接口参考文档

**项目：** ai-agent-server  
**参考：** happy (happy-app + happy-server)  
**目标：** 实现与 Claude Code 像素级对齐  
**更新日期：** 2026-04-08

---

## 一、Happy Protocol 核心接口

### 1.1 Artifact API

后端接口：`/api/v1/artifacts`

| 方法 | 端点 | 功能 |
|------|------|------|
| GET | `/api/v1/artifacts?accountId=xxx` | 获取所有 artifacts |
| GET | `/api/v1/artifacts/:id?accountId=xxx` | 获取单个 artifact |
| POST | `/api/v1/artifacts` | 创建 artifact |
| POST | `/api/v1/artifacts/:id` | 更新 artifact（乐观锁） |
| DELETE | `/api/v1/artifacts/:id?accountId=xxx` | 删除 artifact |

### 1.2 Artifact 数据结构

```typescript
interface HappyArtifact {
  id: string;                    // 唯一 ID (cuid2 格式)
  accountId: string;             // 账户 ID
  sessionId: string;             // 会话 ID
  header: string;                // Base64(加密的 header JSON)
  body: string;                  // Base64(加密的 body JSON)
  dataEncryptionKey: string;     // 数据加密密钥
  headerVersion: number;         // 乐观锁版本号
  bodyVersion: number;           // 乐观锁版本号
  seq: number;                   // 序列号
  createdAt: number;             // 创建时间戳
  updatedAt: number;             // 更新时间戳
}
```

### 1.3 Artifact 类型

| Type | Subtype | 功能 | 前端组件 |
|------|---------|------|----------|
| `message` | `user-message` | 用户消息 | MessageView |
| `message` | `assistant-message` | 助手消息 | MessageView |
| `tool-call` | varies | 工具调用 | ToolCallView / TaskToolView / TodoListView |
| `permission` | `permission-request` | 权限请求 | PermissionDialog |
| `todo` | `todo-add/update/complete` | 待办事项 | TodoListView |

---

## 二、工具调用协议

### 2.1 Tool Call Artifact Header

```typescript
interface ToolCallHeader {
  type: 'tool-call';
  subtype: string;           // 工具名称，如 'bash', 'file_read', 'TaskCreate'
  title?: string;            // 工具标题
  toolName?: string;         // 工具名称
  toolDisplayName?: string;  // 工具显示名称
  icon?: string;             // 图标（emoji 或 SVG）
  status?: string;           // 状态：started, running, completed, failed
  timestamp?: number;        // 时间戳
}
```

### 2.2 Tool Call Artifact Body

```typescript
interface ToolCallBody {
  type?: string;
  status?: 'started' | 'in_progress' | 'completed' | 'failed' | 'error';
  input?: Record<string, unknown>;     // 输入参数
  output?: unknown;                     // 输出结果
  error?: string;                       // 错误信息
  durationMs?: number;                  // 执行时长
  timestamp?: number;
}
```

### 2.3 转换后的 ToolCall（前端使用）

```typescript
interface ToolCall {
  id: string;
  name: string;              // 工具名称
  title: string;             // 显示标题
  description: string;       // 描述
  args: Record<string, unknown>;  // 输入参数
  result?: unknown;          // 执行结果
  state: 'running' | 'completed' | 'error';
  startTime: number;
  endTime?: number;
  durationMs?: number;
}
```

---

## 三、工具样式规范（像素级对齐）

### 3.1 通用工具卡片样式

```
┌─────────────────────────────────────┐
│ [图标] 工具标题          [状态] [展开]│
│        副标题/描述                    │
├─────────────────────────────────────┤
│ [展开后显示输入/输出/错误]            │
└─────────────────────────────────────┘
```

**样式属性：**
- 边框：`1px solid #e5e7eb`
- 圆角：`8px`
- 背景：`#fff`
- 头部背景：`#f9fafb`
- 图标尺寸：`20px`
- 状态指示器：Running=🔄, Completed=✅, Error=❌

### 3.2 Task 工具样式（Claude Code 风格）

#### TaskCreate - 创建任务

```
┌─────────────────────────────────────┐
│ ◻  任务主题                          │
│    任务描述                          │
│    进行中的动作（activeForm）         │
│    ID: task-xxxxx                   │
└─────────────────────────────────────┘
```

**Checkbox 状态：**
- `pending`: ◻ (灰色 #9ca3af)
- `in_progress`: ◻ (蓝色 #3b82f6)
- `completed`: ✓ (绿色 #22c55e)

#### TaskUpdate - 更新任务

```
┌─────────────────────────────────────┐
│ [状态]  任务主题                      │
│         ID: task-xxxxx  [状态标签]   │
└─────────────────────────────────────┘
```

#### TaskList - 任务列表

```
┌─────────────────────────────────────┐
│ Tasks                        3 total│
├─────────────────────────────────────┤
│ ◻  任务 1                           │
│ ✓  任务 2                           │
│ ◻  任务 3                           │
└─────────────────────────────────────┘
```

### 3.3 Bash 工具样式

```
┌─────────────────────────────────────┐
│ 💻  执行命令              ✅ 129ms  │
│        Command: cat test.txt        │
├─────────────────────────────────────┤
│ Input:                              │
│   command: "cat test.txt"           │
│                                     │
│ Output:                             │
│   这是个正式文件                     │
└─────────────────────────────────────┘
```

**最小化模式：**
- 仅显示命令名和描述
- 不展开输入/输出

### 3.4 File 工具样式

```
┌─────────────────────────────────────┐
│ 📄  test.txt                       │
│        读取文件内容                  │
├─────────────────────────────────────┤
│ [文件内容预览]                       │
└─────────────────────────────────────┘
```

### 3.5 TodoWrite 样式（旧版，已废弃）

```
┌─────────────────────────────────────┐
│ 📋  Task List               2/3     │
├─────────────────────────────────────┤
│ ☐  待办事项 1                        │
│ ☐  进行中的任务 2                    │
│ ☑  已完成事项 3                      │
└─────────────────────────────────────┘
```

---

## 四、工具注册表

### 4.1 Known Tools 定义位置

前端：`src/tools/registry/known-tools.ts`

每个工具定义包含：
- `title`: 标题（字符串或函数）
- `icon`: 图标（SVG 路径）
- `minimal`: 是否最小化显示
- `noStatus`: 是否隐藏状态指示器
- `hideDefaultError`: 是否隐藏默认错误
- `extractDescription`: 描述提取函数
- `extractSubtitle`: 副标题提取函数
- `extractStatus`: 状态提取函数

### 4.2 完整工具列表

| 工具名 | 图标 | Minimal | 专用视图 |
|--------|------|---------|----------|
| bash | terminal | ✅ | BashView |
| file_read | file | ✅ | FileReadView |
| file_write | edit | ❌ | EditView |
| file_edit | edit | ❌ | EditView |
| glob | search | ✅ | - |
| grep | search | ✅ | - |
| ls | file | ✅ | LSView |
| multi_edit | edit | ❌ | MultiEditView |
| TaskCreate | bulb | ❌ | TaskToolView |
| TaskUpdate | check | ❌ | TaskToolView |
| TaskList | list | ✅ | TaskToolView |
| todo_write | bulb | ❌ | TodoListView |
| AskUserQuestion | question | ❌ | AskUserQuestionView |
| ExitPlanMode | bulb | ❌ | ExitPlanModeView |
| mcp_connect | globe | ✅ | McpServerView |
| skill_install | bulb | ✅ | SkillsView |

---

## 五、消息类型（Happy Protocol）

### 5.1 Message 结构

```typescript
interface Message {
  kind: 'tool-call' | 'text' | 'service' | 'file';
  id: string;
  time: number;           // Unix 时间戳 (毫秒)
  role: 'user' | 'agent';
  turn?: string;          // turn ID
  tool?: ToolCall;        // 工具调用（当 kind='tool-call'）
  text?: string;          // 文本内容（当 kind='text'）
  thinking?: boolean;     // 是否思考中
  file?: {                // 文件信息（当 kind='file'）
    ref: string;
    name: string;
    size: number;
    image?: { width; height; thumbhash };
  };
}
```

### 5.2 Happy Event 类型

```typescript
type HappyEventType =
  | 'text'              // 文本消息
  | 'service'           // 服务消息
  | 'tool-call-start'   // 工具调用开始
  | 'tool-call-end'     // 工具调用结束
  | 'file'              // 文件消息
  | 'turn-start'        // 回合开始
  | 'turn-end'          // 回合结束
  | 'start'             // 会话开始
  | 'stop';             // 会话结束
```

---

## 六、前端组件层次结构

```
App.vue
├── ChatView.vue
│   ├── MessageList.vue
│   │   ├── MessageView.vue
│   │   │   └── ToolCallView.vue
│   │   │       ├── TaskToolView.vue (Task 专用)
│   │   │       ├── TodoListView.vue (Todo 专用)
│   │   │       ├── BashView.vue (Bash 专用)
│   │   │       ├── EditView.vue (FileEdit 专用)
│   │   │       ├── FileReadView.vue
│   │   │       ├── AskUserQuestionView.vue
│   │   │       ├── ExitPlanModeView.vue
│   │   │       ├── LSView.vue
│   │   │       ├── MultiEditView.vue
│   │   │       ├── McpServerView.vue
│   │   │       └── SkillsView.vue
│   │   └── PermissionDialog.vue
│   └── ChatInput.vue
└── SettingsPage.vue
```

---

## 七、后端接口对齐清单

### 7.1 已实现接口

| 接口 | 状态 | 备注 |
|------|------|------|
| `POST /api/v1/artifacts` | ✅ | 创建 artifact |
| `GET /api/v1/artifacts` | ✅ | 获取 artifact 列表 |
| `GET /api/v1/artifacts/:id` | ✅ | 获取单个 artifact |
| `POST /api/v1/artifacts/:id` | ✅ | 更新 artifact |
| `DELETE /api/v1/artifacts/:id` | ✅ | 删除 artifact |
| `POST /api/chat` | ✅ | 有状态对话 |
| `GET /api/permissions/pending` | ✅ | 获取待确认权限 |
| `POST /api/permissions/respond` | ✅ | 提交权限响应 |

### 7.2 待实现接口

| 接口 | 功能 | 优先级 |
|------|------|--------|
| `GET /api/v1/turns/:sessionId` | 获取会话 turn 历史 | 中 |
| `POST /api/v1/turns` | 创建新 turn | 中 |
| `WS /api/ws` | WebSocket 实时更新 | 高 |

---

## 八、功能差距分析

### 8.1 与 happy-server 对比

| 功能 | happy-server | ai-agent-server | 差距 |
|------|--------------|-----------------|------|
| Artifact 加密 | ✅ 真实加密 | ❌ Base64 占位 | 高 |
| WebSocket 推送 | ✅ | ❌ 轮询 | 高 |
| 流式输出 | ✅ SSE | ✅ 回调更新 | 已对齐 |
| 权限确认 | ✅ | ✅ | 已对齐 |
| Turn 管理 | ✅ | ❌ | 中 |

### 8.2 与 Claude Code 对比

| 功能 | Claude Code | ai-agent-server | 差距 |
|------|-------------|-----------------|------|
| Task 工具集 | ✅ | ✅ | 已对齐 |
| 权限确认对话框 | ✅ | ✅ | 已对齐 |
| 工具调用流式输出 | ✅ | ✅ | 已对齐 |
| Markdown 渲染 | ✅ | ✅ | 已对齐 |
| 子 Agent 委派 | ✅ | ⚠️ 部分 | 中 |
| MCP 工具调用 | ✅ | ✅ | 已对齐 |
| Skills 插件 | ✅ | ✅ | 已对齐 |

---

## 九、测试方法

### 9.1 模拟用户发送消息

```javascript
// 在前端控制台执行
const artifact = {
  id: 'test-' + Date.now(),
  accountId: localStorage.getItem('happy-account-id'),
  sessionId: localStorage.getItem('happy-session-id'),
  header: btoa(JSON.stringify({
    type: 'message',
    subtype: 'user-message',
    title: 'User Message',
    timestamp: Date.now()
  })),
  body: btoa(JSON.stringify({
    type: 'user-message',
    content: '创建一个测试文件',
    timestamp: Date.now()
  })),
  dataEncryptionKey: '',
  headerVersion: 1,
  bodyVersion: 1,
  seq: 0,
  createdAt: Date.now(),
  updatedAt: Date.now()
};

fetch('/api/v1/artifacts', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-API-Key': localStorage.getItem('happy-api-key')
  },
  body: JSON.stringify(artifact)
});
```

### 9.2 观察前后端日志

**后端日志位置：**
```bash
# 实时查看
tail -f target/minimal-k8s-agent-demo-0.1.0-SNAPSHOT.jar.log
```

**前端日志：**
- 打开浏览器开发者工具
- Console 标签查看前端日志
- Network 标签查看 API 请求

---

## 十、下一步行动

1. **完善 TaskToolView** - 确保 checkbox 样式与 Claude Code 一致
2. **实现 WebSocket 推送** - 替代轮询，提高实时性
3. **实现 Turn 管理** - 支持会话历史查看
4. **完善错误处理** - 统一错误展示样式
5. **添加更多工具视图** - NotebookEdit/Diff 等

---

## 参考资料

- [happy-app knownTools.tsx](g:/projects/happy/packages/happy-app/sources/components/tools/knownTools.tsx)
- [happy-server Artifact API](g:/projects/happy/packages/happy-server/src/artifact/)
- [Claude Code Documentation](https://code.claude.com/docs)
- [Claude Code Changelog](https://code.claude.com/docs/en/changelog)
