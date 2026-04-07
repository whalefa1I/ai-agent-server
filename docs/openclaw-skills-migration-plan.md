# OpenClaw 目标 Skills 改造计划（上下文友好 / 按需注入）

> 目标：将当前项目的 skills 从“skill-as-tool（每个 skill 注册为独立工具并执行脚本）”迁移为更接近 OpenClaw 的“skill-as-prompt（skills 目录 catalog + gating + 按需 read/按需注入）”架构，并与现有权限/Hook/审计体系对齐。
>
> 核心原则：
> - **默认只注入 catalog**（技能列表 + 位置），不注入全文。
> - **只在命中时注入**（命中 skill/用户显式请求/模型明确选择）。
> - **只注入必要片段**（预算控制、compact 降级、片段抽取）。
> - **gating 在加载阶段过滤**（os/bins/env/config/always/allowlist/skillFilter），并形成 **snapshot**，保证会话内稳定。
> - skills 本质是“教模型如何使用已有工具”，不是新增执行入口；高风险动作仍走现有工具权限系统。

---

## 0. 现状与目标差异（需要改的点）

### 0.1 当前实现（项目现状）
- `SkillService` 扫描 `skills/**/SKILL.md`（含 `~/.agents/skills`、`~/.openclaw/skills`）并解析 frontmatter。
- 每个 skill 会注册一个工具：`skill_<skillName>`，并绑定执行器：
  - `json/markdown` 用 Java 内置执行器
  - 其它 skill 走 `GenericSkillExecutor.execute(skillDirectory, args)` 执行脚本
- 系统提示词中注入 `buildSkillsPrompt()`，内容强调“直接调用 skill_* 工具”。

### 0.2 OpenClaw 目标形态（对齐点）
- skills 注入的是 **catalog（name/description/location）**，并有预算/compact 降级。
- skills 通过 **gating** 过滤出“eligible skills”，并在 session 内形成 **snapshot**。
- skill 的正文 `SKILL.md` **只在需要时 read**（模型按名称匹配任务时读取），而不是预先注入。
- 可选：slash command 体系支持 `command-dispatch: tool`（绕过模型直接转发到已有工具），但这不是必须。

---

## 1. 迁移策略总览（分阶段、可回滚）

### 1.1 新增配置开关（避免一次性破坏）
建议新增：
- `demo.skills.mode`: `legacy_tool` | `openclaw_catalog` | `hybrid`
  - `legacy_tool`：保持现状（`skill_*` 工具 + 脚本执行）
  - `openclaw_catalog`：只注入 catalog + gating + snapshot，不注册 `skill_*` 工具
  - `hybrid`：同时提供 catalog（推荐默认）并保留 `skill_*`（过渡期）
- `demo.skills.limits`：
  - `max-skills-in-prompt`（默认 150）
  - `max-skills-prompt-chars`（默认 30000）
  - `max-skill-file-bytes`（默认 256000）
  - `compact-warning-overhead`（默认 150）
- `demo.skills.watch.enabled` 已有；后续扩展 watcher 触发 snapshot 版本 bump。

### 1.2 “只在命中时注入”的两种实现路径（择一为主）
- **路径 A（更贴近 OpenClaw）**：仅注入 catalog；当模型/用户需要具体 skill 时，模型使用已有 `read` 工具读取 `SKILL.md`。框架不提供 skill 执行工具。
  - 优点：上下文最干净、实现最简单、最贴近 OpenClaw“teach tools”的定位。
  - 缺点：模型必须学会 read → follow；需要 prompt 指导与测试覆盖。
- **路径 B（带一点 Claude Code 风格，但仍以 OpenClaw 为目标）**：增加一个统一的 `skill_read`（或 `skill`) 工具，只负责返回/注入 `SKILL.md`（可做片段抽取/预算控制），不执行脚本。
  - 优点：可控性更强；更容易做到“只注入必要片段”。
  - 缺点：多了一个工具表面；需与权限系统对齐。

本计划默认：**路径 A 为主**（更 OpenClaw），并在阶段 4 可选加入路径 B 作为“控制阀”。

---

## 2. 关键能力设计

### 2.1 Skills catalog prompt（默认注入内容）
输出格式建议对齐 OpenClaw（XML 简版）：
- `<available_skills>` 下每个 `<skill>`：
  - `<name>`：skill name
  - `<description>`：简短描述（可选，预算紧张时省略）
  - `<location>`：`SKILL.md` 路径（建议 home 前缀压缩为 `~/`）

并在 catalog 前添加行为规则（高优先级、短文本）：
- 当任务匹配 skill 名称/描述时：**先 read skill 文件**再行动
- skill 文档中出现相对路径：以 skill 目录（`SKILL.md` 所在目录）为 baseDir 解析
- 不要把整份 skill 文本复述到输出；按步骤执行工具并返回结果

### 2.2 Snapshot（会话稳定性）
- 目标：在一次会话/请求生命周期内保持 skills 集合稳定，避免每轮重扫导致 prompt 抖动。
- 设计：
  - `SkillsSnapshotService` 维护 `snapshotVersion`（现有），扩展为：
    - `SkillSnapshot { version, eligibleSkills[], promptString }`
  - watcher 或 install/uninstall/reload 触发 version bump
  - query loop 每次 run 使用同一 snapshot（或读取最新但只在 turn-0 固化）

### 2.3 Gating（加载时过滤 eligible skills）
对齐 OpenClaw 的 `metadata.openclaw` 字段（兼容 single-line JSON）：
- `always: true`：跳过 gating
- `os: [win32|linux|darwin]`
- `requires.bins` / `requires.anyBins`：PATH 探测
- `requires.env`：环境变量存在或由配置注入（见下）
- `requires.config`：从 `application.yml`/自定义 config bag 判断 truthy
- `allowBundled` / `skillFilter`：限制可见集合（如果需要对齐 agent allowlist，可后续加 per-agent filter）

注意：
- gating 只决定“是否进入 catalog + 是否可被使用”；不替代权限系统。
- 对第三方 skills 需保守：即使 eligible，也仍然需要工具权限审批（由现有 PermissionManager 决定）。

### 2.4 “只注入必要片段”（预算与片段策略）
策略优先级：
1. **默认仅 catalog**（最小）
2. 如果 catalog 超预算：
   - 降级为 compact catalog（省略 description）
3. 仍超预算：
   - 截断 skills 数量（保留前 N 个，按优先级/最近使用/显式 allowlist 排序）

如果走“可选 skill_read 工具（路径 B）”，则对 `SKILL.md` 注入也应有预算：
- 只返回 frontmatter + “When to use/How to use/Steps/Tools” 片段（按标题匹配）
- 或前 K 字符 + 关键段落（避免把 100KB 文档塞进上下文）

### 2.5 权限/审计/Hook 对齐（必须）
skills 本身是“文档”，但它会引导工具调用，因此要做两层控制：
- **read skill 文件**：属于只读文件访问（若你已有 FS 工具权限，沿用即可）
- **执行动作工具**：沿用现有 `PermissionManager` + `HookService` + `ToolStateService` 的审批与审计

额外建议：
- skills 变更（install/uninstall/reload）记录为事件
- skill 命中（模型 read 了某个 skill）记录为轻量事件（便于调试与统计）

---

## 3. 具体实施步骤（按里程碑逐步落地）

### 里程碑 M1：Catalog Prompt + Snapshot（无破坏）
**目标**：把系统提示词的 skills 部分改为 OpenClaw catalog，并且 prompt 稳定可控。

实现要点：
- 在 `SkillService` 增加：
  - `buildSkillsCatalogPrompt()`（替换/并行现有 `buildSkillsPrompt()`）
  - 输出 `<available_skills>`（带 location）
  - 路径压缩：将 home 前缀替换为 `~/`（win32 也做一致化展示）
- `AgentConfiguration`/`AgenticQueryLoop`/`EnhancedAgenticQueryLoop`：
  - 改为注入 catalog prompt（受 `demo.skills.mode` 控制）
  - turn-0 固化 snapshot（避免每 turn 变化）

验收：
- skills 很多时，prompt 不爆炸（有 chars 上限）
- prompt 内容不再出现“直接调用 skill_* 工具”的指令

### 里程碑 M2：Gating（load-time filter）
**目标**：catalog 中只出现 eligible skills。

实现要点：
- 完整解析 `metadata.openclaw`（优先实现 YAML frontmatter 中 `metadata:` 的结构，再支持 single-line JSON）
- 实现 gating：
  - os
  - requires.bins / anyBins（PATH 探测）
  - requires.env（env 存在或配置注入）
  - requires.config（从 `demo.skills.entries.<skillKey>.config` 或 `application.yml` 判 truthy）
- `always: true` 跳过 gating

验收：
- 缺少 bin/env/config 的技能不会进入 catalog
- `always` 技能始终可见

### 里程碑 M3：只在命中时注入（路径 A）
**目标**：不再把 skill 正文注入系统提示词；模型需要时用 `read` 工具读取。

实现要点：
- 在 catalog prompt 中明确规则：命中 skill 时先 read `SKILL.md`。
- 可选：为每个 skill 生成“推荐 read 的路径”字段（location 已足够）。
- 对 compaction：确保 catalog 作为 system 固定部分，压缩逻辑不反复抖动。

验收：
- 主 prompt 不包含任何技能正文
- 模型在需要 skill 时，会主动 read 对应文件（通过测试/手工验证）

### 里程碑 M4：只注入必要片段（可选路径 B：skill_read）
**目标**：当你需要更强可控性时，用 `skill_read` 工具返回“必要片段”而非全文。

实现要点：
- 注册一个 `skill_read` 工具（只读，权限级别 READ_ONLY）：
  - input: `{ "name": "skillName", "section": "when|steps|tools|all", "maxChars": 4000 }`
  - output: `{ "name", "baseDir", "location", "contentSnippet" }`
- 片段抽取策略：
  - 优先按 Markdown 标题（`##`/`###`）匹配
  - fallback：前 N 字符 + 去掉 frontmatter
  - 强制 maxChars

验收：
- 即使 `SKILL.md` 很大，也不会把全文注入上下文
- 片段足够指导工具调用

### 里程碑 M5：兼容/迁移 legacy skill_*（可选）
**目标**：在迁移期兼容旧的 `skill_*` 执行，逐步下线脚本执行型技能。

策略：
- `hybrid`：catalog + gating + snapshot 同时保留 `skill_*`（但 prompt 不再鼓励调用）
- 逐步将“脚本执行型 skill”迁移为“文档型 skill + 已有工具组合”
- 最终 `openclaw_catalog` 模式下禁用 `skill_*`

---

## 4. 需要修改/新增的模块清单（对照当前项目）

### 必改
- `SkillService`
  - skills prompt：从 “tool calls” 指令改为 OpenClaw catalog
  - gating：eligible skills 过滤
  - snapshot：缓存 prompt 与技能清单，watcher bump 版本
- `AgentConfiguration` / `AgenticQueryLoop` / `EnhancedAgenticQueryLoop`
  - 注入 catalog（并在 turn-0 固化 snapshot）

### 可选新增
- `SkillCatalogFormatter`（独立类：escape XML、compact、预算控制）
- `SkillGatingEvaluator`（独立类：os/bins/env/config/always）
- `skill_read` 工具（只读片段注入）

---

## 5. 测试与验收（必须覆盖）

### 单测建议
- gating：
  - os gate
  - bins/anyBins gate（可 mock PATH 探测）
  - env gate（存在/不存在/配置注入）
  - always
- prompt：
  - full vs compact
  - chars 上限与截断
  - home 路径压缩 `~/`
- snapshot：
  - watcher bump 后 version 变化导致 prompt 重建
  - 会话内稳定（turn-0 固化）

### 手工验收脚本（建议）
- skills 很多时启动：系统提示词长度稳定、没有技能正文
- 触发一个需要 skill 的任务：模型 read skill → 调用工具 → 输出结果
- 缺少依赖时：skill 不出现在 catalog

---

## 6. 风险与回滚策略

- **风险：模型不主动 read skill**  
  - 缓解：catalog prompt 加强指令；必要时引入 `skill_read` 工具（路径 B）。
- **风险：gating 误杀技能**  
  - 缓解：提供 `always: true`、调试日志、以及临时 `demo.skills.mode=hybrid` 回滚。
- **风险：prompt 抖动影响 compaction**  
  - 缓解：snapshot 固化 + watcher 触发刷新，而不是每轮重扫。

