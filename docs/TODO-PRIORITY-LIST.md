# 功能差距待办清单

本文档基于功能对比分析，列出需要补齐的功能和优先级。

---

## P0 - 核心工具缺失（必须补齐）

### 1. FileEditTool - 差异补丁编辑工具
**优先级**: P0  
**工作量**: 3-5 天  
**复杂度**: 高

**功能需求:**
- [ ] 基于字符串匹配的查找替换
- [ ] 支持多处匹配定位
- [ ] 差异对比生成
- [ ] 文件意外修改检测
- [ ] 引号/行尾风格保持
- [ ] 大文件分块处理

**参考实现:**
- `src/tools/FileEditTool/FileEditTool.ts` (Claude Code)

**实现文件:**
- [ ] `tools/file/FileEditTool.java`
- [ ] `tools/file/DiffMatcher.java`
- [ ] `tools/file/PatchApplier.java`

---

### 2. FileReadTool - 文件读取工具
**优先级**: P0  
**工作量**: 1-2 天  
**复杂度**: 中

**功能需求:**
- [ ] 读取文件内容
- [ ] 支持范围读取（行号）
- [ ] 大文件分页
- [ ] 文件元数据返回
- [ ] 二进制文件检测

**参考实现:**
- `src/tools/FileReadTool/FileReadTool.ts` (Claude Code)

**实现文件:**
- [ ] `tools/file/FileReadTool.java`
- [ ] `tools/file/RangeReader.java`

---

### 3. FileWriteTool - 文件写入工具
**优先级**: P0  
**工作量**: 1-2 天  
**复杂度**: 中

**功能需求:**
- [ ] 创建新文件
- [ ] 覆盖写入
- [ ] 目录自动创建
- [ ] 文件存在检查
- [ ] 安全写入（临时文件 + 重命名）

**参考实现:**
- `src/tools/FileWriteTool/FileWriteTool.ts` (Claude Code)

**实现文件:**
- [ ] `tools/file/FileWriteTool.java`

---

### 4. BashTool 增强 - 本地命令执行
**优先级**: P0  
**工作量**: 3-4 天  
**复杂度**: 高

**功能需求:**
- [ ] 本地 Shell 执行（非 K8s）
- [ ] 工作目录管理
- [ ] 超时控制
- [ ] 进程组管理
- [ ] 危险命令检测
- [ ] 输出流式处理
- [ ] 环境变量继承

**参考实现:**
- `src/tools/BashTool/BashTool.tsx` (Claude Code)

**实现文件:**
- [ ] `tools/shell/BashTool.java`
- [ ] `tools/shell/ShellExecutor.java`
- [ ] `tools/shell/DangerousCommandDetector.java`

---

### 5. GrepTool - 内容搜索工具
**优先级**: P0  
**工作量**: 2-3 天  
**复杂度**: 中

**功能需求:**
- [ ] 正则表达式搜索
- [ ] 递归目录搜索
- [ ] 文件类型过滤
- [ ] 上下文行显示
- [ ] 结果高亮

**参考实现:**
- `src/tools/GrepTool/GrepTool.ts` (Claude Code)

**实现文件:**
- [ ] `tools/search/GrepTool.java`

---

### 6. GlobTool - 文件匹配工具
**优先级**: P0  
**工作量**: 1 天  
**复杂度**: 低

**功能需求:**
- [ ] Glob 模式匹配
- [ ] 递归搜索
- [ ] 排除规则

**参考实现:**
- `src/tools/GlobTool/GlobTool.ts` (Claude Code)

**实现文件:**
- [ ] `tools/search/GlobTool.java`

---

## P1 - 权限系统增强

### 7. 项目级权限配置
**优先级**: P1  
**工作量**: 2-3 天  
**复杂度**: 中

**功能需求:**
- [ ] `.claude/settings.local.json` 项目配置
- [ ] 配置文件加载
- [ ] 规则合并逻辑
- [ ] 优先级顺序

**实现文件:**
- [ ] `toolsystem/PermissionRule.java`
- [ ] `toolsystem/PermissionConfigLoader.java`
- [ ] `.claude/settings.local.json` 示例

---

### 8. 通配符规则支持
**优先级**: P1  
**工作量**: 2 天  
**复杂度**: 中

**功能需求:**
- [ ] 命令通配符解析
- [ ] 路径通配符匹配
- [ ] 规则优先级

**实现文件:**
- [ ] `toolsystem/WildcardMatcher.java`

---

### 9. CLI 参数覆盖
**优先级**: P1  
**工作量**: 1 天  
**复杂度**: 低

**功能需求:**
- [ ] `--permission-mode` 参数
- [ ] `--allow` / `--deny` 参数
- [ ] 参数解析

---

## P1 - Agent 能力增强

### 10. TaskGetTool - 获取任务详情
**优先级**: P1  
**工作量**: 1 天  
**复杂度**: 低

**实现文件:**
- [ ] `coordinator/TaskGetTool.java`

---

### 11. TaskListTool - 任务列表
**优先级**: P1  
**工作量**: 1 天  
**复杂度**: 低

**实现文件:**
- [ ] `coordinator/TaskListTool.java`

---

### 12. TaskUpdateTool - 更新任务
**优先级**: P1  
**工作量**: 1-2 天  
**复杂度**: 中

**实现文件:**
- [ ] `coordinator/TaskUpdateTool.java`

---

### 13. SendMessageTool - 任务通信
**优先级**: P1  
**工作量**: 2 天  
**复杂度**: 中

**实现文件:**
- [ ] `coordinator/SendMessageTool.java`
- [ ] `coordinator/WorkerMailbox.java` 增强

---

## P2 - 高级集成

### 14. MCP 资源发现与读取
**优先级**: P2  
**工作量**: 3-4 天  
**复杂度**: 高

**功能需求:**
- [ ] ListMcpResourcesTool
- [ ] ReadMcpResourceTool
- [ ] MCP 服务器连接管理

**实现文件:**
- [ ] `mcp/ListMcpResourcesTool.java`
- [ ] `mcp/ReadMcpResourceTool.java`

---

### 15. WebSearchTool - 网络搜索
**优先级**: P2  
**工作量**: 2-3 天  
**复杂度**: 中

**功能需求:**
- [ ] 搜索引擎 API 集成
- [ ] 搜索结果分页
- [ ] 摘要生成

**实现文件:**
- [ ] `tools/web/WebSearchTool.java`

---

### 16. WebFetchTool - 网页抓取
**优先级**: P2  
**工作量**: 2-3 天  
**复杂度**: 中

**功能需求:**
- [ ] HTML 内容提取
- [ ] Markdown 转换
- [ ] 链接解析

**实现文件:**
- [ ] `tools/web/WebFetchTool.java`

---

### 17. LSPTool - 语言服务器协议
**优先级**: P2  
**工作量**: 5-7 天  
**复杂度**: 高

**功能需求:**
- [ ] LSP 客户端集成
- [ ] 诊断追踪
- [ ] 代码跳转
- [ ] 符号查找

**实现文件:**
- [ ] `lsp/LSPTool.java`
- [ ] `lsp/LanguageServerClient.java`

---

### 18. Git 深度集成
**优先级**: P2  
**工作量**: 4-5 天  
**复杂度**: 高

**功能需求:**
- [ ] Git 状态检测
- [ ] Diff 生成
- [ ] 提交历史
- [ ] 分支管理

**实现文件:**
- [ ] `git/GitTool.java`
- [ ] `git/GitDiffTool.java`
- [ ] `git/GitStatusTool.java`

---

## P3 - 辅助工具

### 19. TodoWriteTool - 待办管理
**优先级**: P3  
**工作量**: 1 天  
**复杂度**: 低

**实现文件:**
- [ ] `tools/todo/TodoWriteTool.java`

---

### 20. ConfigTool - 配置管理
**优先级**: P3  
**工作量**: 1-2 天  
**复杂度**: 低

**实现文件:**
- [ ] `tools/config/ConfigTool.java`

---

### 21. BriefTool - 简报生成
**优先级**: P3  
**工作量**: 2 天  
**复杂度**: 中

**实现文件:**
- [ ] `tools/brief/BriefTool.java`

---

### 22. Cron 工具 - 计划任务
**优先级**: P3  
**工作量**: 2-3 天  
**复杂度**: 中

**实现文件:**
- [ ] `tools/schedule/CronCreateTool.java`
- [ ] `tools/schedule/CronListTool.java`
- [ ] `tools/schedule/CronDeleteTool.java`

---

### 23. NotebookEditTool - Notebook 编辑
**优先级**: P3  
**工作量**: 2 天  
**复杂度**: 中

**实现文件:**
- [ ] `tools/notebook/NotebookEditTool.java`

---

## 统计

| 优先级 | 功能数量 | 预估工作量 |
|--------|---------|-----------|
| P0 | 6 | 10-15 天 |
| P1 | 7 | 10-12 天 |
| P2 | 5 | 15-20 天 |
| P3 | 5 | 8-10 天 |
| **总计** | **23** | **43-57 天** |

---

## 里程碑

### M1 - 核心工具补齐（P0）
**时间**: 2-3 周  
**目标**: 具备基本文件操作和命令执行能力

### M2 - 权限和 Agent 增强（P1）
**时间**: 2 周  
**目标**: 企业级权限管理和多 Agent 协作

### M3 - 高级集成（P2）
**时间**: 3-4 周  
**目标**: MCP、Web、LSP、Git 完整支持

### M4 - 辅助工具（P3）
**时间**: 1-2 周  
**目标**: UX 优化和功能完善

---

## 立即开始

推荐从以下工具开始（按顺序）：

1. **GlobTool** (1 天) - 最简单，快速建立信心
2. **FileReadTool** (1-2 天) - 基础读取功能
3. **FileWriteTool** (1-2 天) - 基础写入功能
4. **FileEditTool** (3-5 天) - 核心编辑能力
5. **GrepTool** (2-3 天) - 内容搜索
6. **BashTool 增强** (3-4 天) - 本地执行

完成这 6 个工具后，minimal-k8s-agent-demo 将具备与 Claude Code 基本相当的代码操作能力。
