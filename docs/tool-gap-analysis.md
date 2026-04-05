# Claude Code 工具对比分析

## 已对齐的工具 ✅

| 工具名 | Claude Code | ai-agent-server | 状态 |
|--------|-------------|-----------------|------|
| Bash | ✅ | ✅ LocalBashTool | 已对齐 |
| FileRead | ✅ | ✅ LocalFileReadTool | 已对齐 |
| FileWrite | ✅ | ✅ LocalFileWriteTool | 已对齐 |
| FileEdit | ✅ | ✅ LocalFileEditTool | 已对齐 |
| TodoWrite | ✅ | ✅ TodoWriteTool | 已对齐 |
| Glob | ✅ | ✅ LocalGlobTool | 已对齐 |
| Grep | ✅ | ✅ LocalGrepTool | 已对齐 |

## 缺失的工具 ❌

### 核心工具

| 工具名 | 功能描述 | 优先级 |
|--------|----------|--------|
| **Task 工具集** | 任务管理工具集 | 高 |
| ├─ TaskCreate | 创建新任务 | 高 |
| ├─ TaskGet | 获取任务详情 | 中 |
| ├─ TaskList | 列出所有任务 | 高 |
| ├─ TaskStop | 停止任务 | 高 |
| ├─ TaskUpdate | 更新任务状态 | 高 |
| ├─ TaskOutput | 获取任务输出 | 中 |
| **Agent 工具集** | Agent/子代理管理 | 高 |
| ├─ AgentTool | 创建/管理子 Agent | 高 |
| ├─ SendMessage | 发送消息到会话 | 中 |
| **AskUserQuestion** | 向用户提问并获取回答 | 高 |
| **EnterPlanMode** | 进入计划模式 | 中 |
| **ExitPlanMode** | 退出计划模式 | 中 |
| **ConfigTool** | 查看/修改配置 | 中 |

### 文件操作工具

| 工具名 | 功能描述 | 优先级 |
|--------|----------|--------|
| **FileCopy** | 复制文件 (已有 LocalFileCopyTool 但未完全对齐) | 中 |
| **FileMove** | 移动文件 (已有 LocalFileMoveTool 但未完全对齐) | 中 |
| **FileDelete** | 删除文件 | 高 |
| **FileStat** | 获取文件信息 (已有 LocalFileStatTool 但需完善) | 低 |
| **Mkdir** | 创建目录 (已有 LocalMkdirTool 但需完善) | 中 |
| **NotebookEdit** | 编辑 Jupyter Notebook | 低 |

### 搜索工具

| 工具名 | 功能描述 | 优先级 |
|--------|----------|--------|
| **ToolSearch** | 搜索可用工具 | 低 |
| **LSPTool** | 语言服务器协议支持 | 低 |

### Web 工具

| 工具名 | 功能描述 | 优先级 |
|--------|----------|--------|
| **WebFetch** | 获取网页内容 (已有 WebFetchTool 但需完善) | 中 |
| **WebSearch** | 网络搜索 (已有 WebSearchTool 但需完善) | 中 |

### 计划/定时工具

| 工具名 | 功能描述 | 优先级 |
|--------|----------|--------|
| **CronCreate** | 创建定时任务 | 低 |
| **CronList** | 列出定时任务 | 低 |
| **CronDelete** | 删除定时任务 | 低 |

### 团队协作工具

| 工具名 | 功能描述 | 优先级 |
|--------|----------|--------|
| **TeamCreate** | 创建团队 | 低 |
| **TeamDelete** | 删除团队 | 低 |
| **WorkflowTool** | 工作流管理 | 低 |

### 其他工具

| 工具名 | 功能描述 | 优先级 |
|--------|----------|--------|
| **SkillTool** | Skills 插件系统 | 中 |
| **MCPTool** | MCP 协议支持 | 中 |
| **ListMcpResources** | 列出 MCP 资源 | 中 |
| **ReadMcpResource** | 读取 MCP 资源 | 中 |
| **MonitorTool** | 监控后台任务 | 中 |
| **RemoteTriggerTool** | 远程触发 | 低 |
| **BriefTool** | 生成简报 | 低 |
| **ReviewArtifactTool** | 审查工件 | 低 |

## 缺失的斜杠命令 ❌

| 命令 | 功能描述 | 优先级 |
|------|----------|--------|
| **/clear** | 清除会话上下文 | 高 |
| **/model** | 切换模型 | 高 |
| **/fast** | 快速模式切换 | 中 |
| **/usage** | 查看使用量 | 中 |
| **/agents** | 管理 Agent | 中 |
| **/kill** | 停止 Agent | 高 |
| **/export-session** | 导出会话 | 中 |
| **/memory** | 记忆管理 | 中 |
| **/skills** | Skills 管理 | 中 |
| **/plugin** | 插件管理 | 中 |
| **/reload-plugins** | 重新加载插件 | 中 |
| **/permissions** | 权限管理 | 中 |
| **/hooks** | Hooks 管理 | 低 |
| **/files** | 文件管理 | 中 |
| **/rewind** | 回滚操作 | 低 |
| **/version** | 版本信息 | 低 |
| **/init** | 初始化项目 | 中 |
| **/login** | 登录 | 低 |
| **/logout** | 登出 | 低 |
| **/mcp** | MCP 管理 | 中 |
| **/share** | 分享会话 | 低 |

## 建议实施优先级

### 高优先级（核心功能）
1. **Task 工具集** - 任务管理是核心功能
2. **AskUserQuestion** - 用户交互必需
3. **FileDelete** - 基本文件操作
4. **/clear**, **/kill** - 会话管理必需

### 中优先级（常用功能）
1. **AgentTool** - 子代理管理
2. **ConfigTool** - 配置管理
3. **SkillTool** - 插件系统
4. **MonitorTool** - 后台任务监控
5. **/model**, **/usage**, **/agents** - 常用命令

### 低优先级（扩展功能）
1. LSPTool - 语言服务器支持
2. NotebookEdit - Jupyter 支持
3. Cron 工具集 - 定时任务
4. 团队协作工具
