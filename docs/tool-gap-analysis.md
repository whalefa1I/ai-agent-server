# Claude Code 工具对比分析

**最后更新：** 2026-04-06
**参考项目：** happy (g:/projects/happy)

## 已对齐的工具 ✅

| 工具名 | Claude Code | ai-agent-server | happy 项目 | 状态 |
|--------|-------------|-----------------|------------|------|
| Bash | ✅ | ✅ LocalBashTool | ✅ Bash/CodexBash/GeminiBash | 已对齐 |
| FileRead | ✅ | ✅ LocalFileReadTool | ✅ Read/read | 已对齐 |
| FileWrite | ✅ | ✅ LocalFileWriteTool | ✅ Write | 已对齐 |
| FileEdit | ✅ | ✅ LocalFileEditTool | ✅ Edit/edit/GeminiEdit | 已对齐 |
| FileDelete | ✅ | ✅ LocalFileDeleteTool | ❌ | 已对齐（扩展） |
| FileCopy | ✅ | ✅ LocalFileCopyTool | ❌ | 已对齐（扩展） |
| FileMove | ✅ | ✅ LocalFileMoveTool | ❌ | 已对齐（扩展） |
| FileStat | ✅ | ✅ LocalFileStatTool | ❌ | 已对齐（扩展） |
| Mkdir | ✅ | ✅ LocalMkdirTool | ❌ | 已对齐（扩展） |
| Glob | ✅ | ✅ LocalGlobTool | ✅ Glob | 已对齐 |
| Grep | ✅ | ✅ LocalGrepTool | ✅ Grep | 已对齐 |
| TodoWrite | ✅ (旧) | ✅ TodoWriteTool | ✅ TodoWrite | 已对齐（可废弃） |
| Task* | ✅ (新) | ✅ TaskCreate/Get/List/Update/Stop/Output | ✅ Task/Agent | 已对齐 |
| ExitPlanMode | ✅ | ✅ ExitPlanModeTool | ✅ ExitPlanMode/exit_plan_mode | 已对齐 |
| AskUserQuestion | ✅ | ✅ AskUserQuestionTool | ✅ AskUserQuestion | 已对齐 |
| WebSearch | ✅ | ✅ WebSearchTool | ✅ WebSearch | 已对齐 |
| WebFetch | ✅ | ✅ WebFetchTool | ✅ WebFetch | 已对齐 |

## 缺失的工具 ❌（与 happy 项目对比）

| 工具名 | 功能描述 | happy  | 优先级 | 建议 |
|--------|----------|--------|--------|------|
| LS | 列出目录内容 | ✅ | 中 | 新增或使用 Mkdir list 动作 |
| MultiEdit | 多编辑操作 | ✅ | 中 | 新增 |
| NotebookRead | 读取 Jupyter Notebook | ✅ | 低 | 可选 |
| NotebookEdit | 编辑 Jupyter Notebook | ✅ | 低 | 可选 |
| CodexDiff/GeminiDiff | 差异查看 | ✅ | 低 | 可选 |

## 工具变更建议

### 废弃工具（跟 Claude Code 2026 年 1 月变更）

| 工具 | 原因 | 替代方案 | 时间线 |
|------|------|----------|--------|
| **TodoWriteTool** | Claude Code 已用 Task 取代 | Task 工具集 | 保留兼容性，默认使用 Task |

### 新增工具优先级

#### 高优先级
1. **ExitPlanModeTool** ✅ - 已完成（2026-04-06）

#### 中优先级
1. **LSTool** ✅ - 已完成（2026-04-06）
2. **MultiEditTool** ✅ - 已完成（2026-04-06）

#### 低优先级
1. **NotebookRead/NotebookEdit** - Jupyter 支持
2. **Diff 工具** - 差异查看

## 现有工具完整列表

### 文件操作工具（9 个）
- LocalGlobTool
- LocalFileReadTool
- LocalFileWriteTool
- LocalFileEditTool
- LocalFileDeleteTool
- LocalFileCopyTool
- LocalFileMoveTool
- LocalFileStatTool
- LocalMkdirTool

### Shell 工具（1 个）
- LocalBashTool

### 搜索工具（2 个）
- LocalGrepTool
- LocalGlobTool

### 版本控制工具（1 个）
- LocalGitTool

### 规划工具（8 个）
- TodoWriteTool（可废弃）
- ExitPlanModeTool ✅
- TaskCreateTool ✅
- TaskListTool ✅
- TaskGetTool ✅
- TaskUpdateTool ✅
- TaskStopTool ✅
- TaskOutputTool ✅

### LSP 工具（1 个）
- LspDiagnosticTool

### Web 工具（2 个）
- WebSearchTool
- WebFetchTool

### 记忆工具（1 个）
- MemorySearchTool

### 交互工具（1 个）
- AskUserQuestionTool

**总计：27 个工具**

## 与 happy 项目的差异

### 我们有的（happy 没有）
- FileDelete, FileCopy, FileMove, FileStat, Mkdir - 文件操作扩展
- LocalGitTool - Git 集成
- LspDiagnosticTool - LSP 支持
- MemorySearchTool - 记忆系统

### happy 有的（我们没有）
- LS - 列出目录
- MultiEdit - 多编辑
- NotebookRead/NotebookEdit - Jupyter
- CodexDiff/GeminiDiff - 差异查看
- 多种变体工具（CodexBash, GeminiBash 等）

## 建议实施优先级

### 已完成（2026-04-06）
- ✅ Task 工具集（6 个工具）
- ✅ AskUserQuestionTool
- ✅ FileDeleteTool
- ✅ ExitPlanModeTool
- ✅ LS 工具
- ✅ MultiEdit 工具
- ✅ 前端视图组件（TaskView, AskUserQuestionView, ExitPlanModeView, LSView, MultiEditView）
- ✅ MCP 集成（后端 + 前端视图 + 测试）
- ✅ Skills 集成（后端 + 前端视图 + 测试，兼容 openclaw/ClawHub）

### 下一步（可选）
1. 评估 NotebookRead/NotebookEdit 需求（Jupyter 支持）
2. 评估 Diff 工具需求（差异查看）
3. 评估 TodoWriteTool 废弃时间表

## 参考来源

- [Claude Code Tasks 取代 TodoWrite](https://medium.com/@richardhightower/claude-code-todos-to-tasks-5a1b0e351a1c)
- [happy 项目 knownTools.tsx](g:/projects/happy/packages/happy-app/sources/components/tools/knownTools.tsx)
- [Claude Code Changelog](https://code.claude.com/docs/en/changelog)
