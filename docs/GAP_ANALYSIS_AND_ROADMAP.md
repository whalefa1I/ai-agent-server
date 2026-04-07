# ai-agent-server 功能差距分析与对齐计划

**目标：** 实现与 Claude Code / Happy Protocol 像素级对齐  
**更新日期：** 2026-04-08  
**当前版本：** 0.1.0-SNAPSHOT

---

## 一、执行摘要

### 1.1 当前状态

| 领域 | 完成度 | 状态 |
|------|--------|------|
| 工具系统 | 90% | 🟡 基本对齐 |
| 前端样式 | 70% | 🟡 部分对齐 |
| Artifact API | 80% | 🟡 缺少 WebSocket |
| 权限确认 | 90% | 🟡 基本对齐 |
| 记忆系统 | 60% | 🔴 需要完善 |
| MCP 集成 | 90% | 🟡 基本对齐 |
| Skills 插件 | 80% | 🟡 基本对齐 |

### 1.2 关键差距

1. **前端工具样式** - Task 工具未完全对齐 Claude Code 风格
2. **错误信息展示** - 未在工具卡片中正确显示
3. **WebSocket 推送** - 当前使用轮询，需要实时推送
4. **Turn 管理** - 缺少会话历史管理
5. **加密实现** - 当前使用 Base64 占位，需要真实加密

---

## 二、前端差距分析

### 2.1 工具视图组件

| 组件 | 状态 | 差距 | 优先级 |
|------|------|------|--------|
| ToolCallView | ✅ | 无 | - |
| TaskToolView | 🟡 | checkbox 样式需优化 | 高 |
| TodoListView | ✅ | 无 | - |
| BashView | 🟡 | 输出格式需优化 | 中 |
| EditView | ✅ | 无 | - |
| FileReadView | ✅ | 无 | - |
| AskUserQuestionView | ✅ | 无 | - |
| ExitPlanModeView | ✅ | 无 | - |
| LSView | ✅ | 无 | - |
| MultiEditView | ✅ | 无 | - |
| McpServerView | ✅ | 无 | - |
| SkillsView | ✅ | 无 | - |
| PermissionDialog | ✅ | 无 | - |

### 2.2 样式对齐问题

#### 问题 1: Task checkbox 渲染

**当前状态：** TaskToolView 已创建但未完全集成

**目标样式（Claude Code）：**
```
┌─────────────────────────────────┐
│ ◻  创建文件 test.txt            │
│    创建新文件并写入内容          │
│    ID: task-abc123              │
└─────────────────────────────────┘
```

**修复方案：**
1. 在 `ToolCalls.vue` 中集成 `TaskToolView`
2. 确保 checkbox 符号正确：◻ (pending) / ✓ (completed)
3. 添加状态颜色：灰色/蓝色/绿色

#### 问题 2: 错误信息展示

**当前状态：** 错误信息未正确展示在工具卡片中

**目标样式：**
```
┌─────────────────────────────────┐
│ 🔧  执行命令            ❌ 失败 │
│        Command: invalid_cmd     │
├─────────────────────────────────┤
│ ❌ 错误：command not found      │
└─────────────────────────────────┘
```

**修复方案：**
1. 在 `ToolCallView.vue` 中添加错误区域
2. 使用红色背景和错误图标
3. 确保错误文本清晰可读

### 2.3 已知工具注册表

**文件：** `src/tools/registry/known-tools.ts`

**缺失的工具定义：**
- [ ] TaskCreate - 已添加，需测试
- [ ] TaskUpdate - 已添加，需测试
- [ ] TaskList - 已添加，需测试
- [ ] TaskGet - 已添加，需测试
- [ ] TaskStop - 已添加，需测试
- [ ] TaskOutput - 已添加，需测试

---

## 三、后端差距分析

### 3.1 Artifact API

| 接口 | 状态 | 差距 |
|------|------|------|
| `GET /api/v1/artifacts` | ✅ | 无 |
| `GET /api/v1/artifacts/:id` | ✅ | 无 |
| `POST /api/v1/artifacts` | ✅ | 无 |
| `POST /api/v1/artifacts/:id` | ✅ | 无 |
| `DELETE /api/v1/artifacts/:id` | ✅ | 无 |
| `WS /api/v1/artifacts` | ❌ | 缺少 WebSocket 推送 |

### 3.2 工具实现状态

| 工具类别 | 已实现 | 总计 | 完成度 |
|----------|--------|------|--------|
| 文件操作 | 9 | 9 | 100% |
| Shell | 1 | 1 | 100% |
| 搜索 | 2 | 2 | 100% |
| 规划 | 7 | 8 | 88% |
| 交互 | 1 | 1 | 100% |
| Web | 2 | 2 | 100% |
| MCP | 1 | 1 | 100% |
| Skills | 1 | 1 | 100% |

### 3.3 提示词对齐

| 工具 | 提示词状态 | 差距 |
|------|------------|------|
| TaskCreate | ✅ | 无 |
| TaskUpdate | ✅ | 已添加 task_id 获取说明 |
| TaskGet | ✅ | 已添加 task_id 获取说明 |
| TaskStop | ✅ | 已添加 task_id 获取说明 |
| TaskOutput | ✅ | 已添加 task_id 获取说明 |
| AskUserQuestion | ✅ | 无 |
| ExitPlanMode | ✅ | 无 |
| Bash | ✅ | 无 |
| FileRead | ✅ | 无 |
| FileWrite | ✅ | 无 |
| FileEdit | ✅ | 无 |

---

## 四、实施计划

### 4.1 第一阶段：前端样式对齐（高优先级）

#### 任务 1.1: 修复 TaskToolView 集成
- [x] 创建 TaskToolView.vue 组件
- [x] 在 ToolCallView.vue 中集成 TaskToolView
- [x] 在 known-tools.ts 中添加 Task 工具定义
- [ ] 测试 TaskCreate/TaskUpdate/TaskList 渲染
- [ ] 修复 checkbox 样式问题

**预计时间：** 2 小时

#### 任务 1.2: 修复错误信息展示
- [ ] 在 ToolCallView.vue 中添加错误区域
- [ ] 添加错误样式（红色背景、错误图标）
- [ ] 测试错误场景展示

**预计时间：** 1 小时

#### 任务 1.3: 优化 BashView 输出
- [ ] 改进输出格式（使用 pre 标签）
- [ ] 添加执行时长显示
- [ ] 添加退出码显示

**预计时间：** 1 小时

### 4.2 第二阶段：后端接口完善（中优先级）

#### 任务 2.1: 实现 WebSocket 推送
- [ ] 添加 WebSocket 配置
- [ ] 实现 Artifact 变更推送
- [ ] 前端实现 WebSocket 监听
- [ ] 关闭轮询

**预计时间：** 4 小时

#### 任务 2.2: 实现 Turn 管理
- [ ] 设计 Turn 数据结构
- [ ] 实现 Turn API
- [ ] 前端实现 Turn 历史查看

**预计时间：** 4 小时

#### 任务 2.3: 实现数据加密
- [ ] 使用 Web Crypto API 实现前端加密
- [ ] 后端实现解密逻辑
- [ ] 测试加密/解密流程

**预计时间：** 4 小时

### 4.3 第三阶段：功能完善（低优先级）

#### 任务 3.1: 完善记忆系统
- [ ] 修复 Embedding 记忆集成
- [ ] 优化记忆搜索 UI
- [ ] 添加记忆管理界面

**预计时间：** 4 小时

#### 任务 3.2: 完善 MCP 集成
- [ ] 添加更多 MCP 工具
- [ ] 优化 MCP 工具发现
- [ ] 添加 MCP 配置界面

**预计时间：** 4 小时

---

## 五、测试计划

### 5.1 前端测试

#### 测试用例 1: Task 工具渲染
```javascript
// 1. 发送消息创建任务
happyApi.sendMessage('创建一个任务，内容是测试文件');

// 2. 观察前端渲染
// 预期：显示 checkbox、任务主题、描述、task ID

// 3. 验证状态变化
// 预期：pending → in_progress → completed
```

#### 测试用例 2: 错误信息展示
```javascript
// 1. 执行错误命令
happyApi.sendMessage('执行一个不存在的命令');

// 2. 观察错误展示
// 预期：红色背景、错误图标、错误信息清晰
```

### 5.2 后端测试

#### 测试用例 1: Artifact API
```bash
# 创建 artifact
curl -X POST http://localhost:8080/api/v1/artifacts \
  -H "Content-Type: application/json" \
  -H "X-API-Key: xxx" \
  -d '{"id":"test-1","accountId":"acc-1","sessionId":"sess-1","header":"xxx","body":"xxx"}'

# 获取 artifacts
curl http://localhost:8080/api/v1/artifacts?accountId=acc-1

# 更新 artifact
curl -X POST http://localhost:8080/api/v1/artifacts/test-1 \
  -H "Content-Type: application/json" \
  -H "X-API-Key: xxx" \
  -d '{"body":"xxx","expectedBodyVersion":1}'
```

### 5.3 集成测试

#### 测试流程：用户发送消息 → AI 回复 → 工具调用 → 工具完成

```
1. 前端发送 user-message artifact
   ↓
2. 后端 HappyChatService 处理
   ↓
3. agenticQueryLoop.runWithCallbacks 调用 AI
   ↓
4. 工具调用回调创建 tool-call artifact
   ↓
5. 工具执行更新 artifact 状态
   ↓
6. 前端轮询/Wesocket 接收更新
   ↓
7. 前端渲染工具卡片
```

---

## 六、进度跟踪

### 2026-04-08 完成
- [x] 创建 Happy Protocol 参考文档
- [x] 创建功能差距分析文档
- [x] 创建 TaskToolView 组件
- [x] 在 known-tools.ts 中添加 Task 工具定义
- [x] 修改 ToolCallView.vue 集成 TaskToolView
- [x] 修复 TaskUpdate 提示词（添加 task_id 获取说明）
- [x] 修复 TaskGet/TaskStop/TaskOutput 提示词
- [x] 重启前端服务

### 待完成
- [ ] 测试 Task 工具渲染
- [ ] 修复错误信息展示
- [ ] 优化 BashView 输出
- [ ] 实现 WebSocket 推送
- [ ] 实现 Turn 管理
- [ ] 实现数据加密

---

## 七、参考资料

- [Happy Protocol Reference](./HAPPY_PROTOCOL_REFERENCE.md)
- [Tool Gap Analysis](./tool-gap-analysis.md)
- [happy-app knownTools.tsx](g:/projects/happy/packages/happy-app/sources/components/tools/knownTools.tsx)
- [Claude Code Documentation](https://code.claude.com/docs)
