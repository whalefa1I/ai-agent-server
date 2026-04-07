# OpenClaw Skills 改造进度

本文件用于同步”以 OpenClaw 为目标”的 skills 改造执行进展，按里程碑记录每次变更与当前状态。

## 当前状态（最后更新：2026-04-07）

- [x] **M0** 规划文档完成：`docs/openclaw-skills-migration-plan.md`
- [x] **M1** Skills catalog prompt（OpenClaw 风格 XML）+ 预算/compact + snapshot 缓存：已完成
- [x] **M2** Gating（always/os/bins/anyBins/env/config）：已完成
- [x] **M3** 只在命中时注入（默认仅 catalog，命中时 read SKILL.md）：已完成
- [x] **M4** 只注入必要片段（复用 `file_read` 工具）：已完成
- [x] **M5** 权限/审计/Hook 对齐补强 + 测试完善：已完成

---

## 变更日志

### 2026-04-07

- **完成（M1）**：将 `SkillService.buildSkillsPrompt()` 改为 OpenClaw 风格 `<available_skills>` catalog（含 `location`），并加入：
  - **稳定排序**（按 name 排序，减少抖动）
  - **home 路径压缩**为 `~/`
  - **prompt 预算**（30k chars）与 **compact 降级**（省略 description）
  - **超限截断**（二分截断技能数量）
  - **snapshot 缓存**（避免每轮请求重建 prompt）
- **验证**：`./mvnw.cmd test` 通过

- **完成（M1 修复）**：修正 catalog prompt 误导性指令
  - **修改前**：提示模型使用 `file_read` 工具读取 SKILL.md（但工具未注册）
  - **修改后**：明确说明两种方式：① 直接调用 `skill_<name>` 工具；② 使用 `file_read` 读取 SKILL.md
  - **新增**：`SkillsSnapshot` 内部类，缓存 version/eligibleSkills/fullCatalogPrompt/compactCatalogPrompt

- **完成（M2）**：补齐 `metadata.openclaw` 解析 + gating（always/os/bins/anyBins/env/config）
  - **已完成**：
    - 支持从 YAML frontmatter 中读取 `metadata.openclaw`（对象）或 `metadata.openclaw`（单行 JSON 字符串）
    - 若不存在 `metadata.openclaw`，兼容读取平铺 `metadata.*`（用于当前仓库已有 skills）
    - catalog 构建时应用 gating：`always` / `os` / `requires.env` / `requires.bins` / `requires.anyBins` / `requires.config`
  - **新增配置**：`SkillsProperties.entries` 支持 `demo.skills.entries.<key>` 配置项，用于 config gating
  - **新增方法**：`SkillsProperties.isConfigTruthy(String key)` 判断配置是否为 truthy

- **完成（M3）**：只在命中时注入（默认仅 catalog）
  - **说明**：`file_read` 工具已在 `DemoToolRegistryConfiguration` 中注册（第 65 行），模型可直接使用

- **完成（M4 简化）**：删除 `skill_read` 工具，复用现有 `file_read`
  - **理由**：
    - OpenClaw 官方实现只使用通用的 `read` tool，没有专门的 `skill_read`
    - `skill_read` 的功能（按名查找、section 抽取、maxChars 截断）都可以用 `glob` + `file_read` 组合替代
    - 减少工具数量，降低模型选择复杂度
  - **修改内容**：
    - 从 `DemoToolRegistryConfiguration` 移除 `skill_read` 注册
    - 从 `DemoToolSpecs` 删除 `skillRead()` 方法和 `SKILL_READ_SCHEMA` 常量
    - 删除 `LocalSkillReadTool.java` 及相关目录
    - 从 `LocalToolRegistry` 和 `LocalToolExecutor` 移除对 `LocalSkillReadTool` 的引用

- **完成（M4 B 方案）**：对齐 OpenClaw XML Catalog 格式
  - **修改内容**：
    - `buildCatalogXml()` 改为 OpenClaw 风格 XML 输出（`<available_skills><skill>...`）
    - 移除 `skill_<name>` 工具调用说明，改为 "Use the file_read tool to load a skill's file"
    - 保留 `skill_<name>` 工具注册作为快捷方式（未来可移除）
    - 移除 compact 模式对 description 的省略（与 OpenClaw 一致，始终输出 description）
  - **参考实现**：`G:\datasets\openclaw\src\agents\skills\skill-contract.ts::formatSkillsForPrompt()`

- **待完成（M5）**：权限/审计/测试补强
  - **已完成**：
    - ToolCallback 层接入 `PermissionManager.requiresPermission(...)`（只读工具自动放行；需要确认的返回 `permission_required` + `requestId`）
    - **上下文压缩集成测试**（`CompactionPipelineTest.java`，10 个测试）
      - Tier 1: microcompact（截断过长 ToolResponse）
      - Tier 2: time-based（保留最近 N 个工具结果）
      - Tier 3: autocompact（LLM 摘要，失败降级处理）
      - 完整流程测试（级联压缩、代码审查对话、多轮对话）
    - **Skills 集成测试**（`SkillsIntegrationTest.java`，10 个测试）
      - Skill Catalog XML 格式验证
      - Skill 调用流程（Calculator、JSON、Markdown）
      - Gating 逻辑验证（always=true、requires bins）
      - 完整流程测试（用户请求→Catalog 匹配→Skill 调用）
    - **Task 集成测试**（`TaskIntegrationTest.java`，16 个测试）
      - 基础流程（TaskCreate、TaskList、TaskGet、TaskUpdate、TaskOutput）
      - 完整生命周期（创建→列表→详情→更新→完成）
      - 自然语言场景（创建任务、询问进度、查看详情、完成任务）
      - 多任务场景（状态过滤、并行管理）
      - 边缘情况（任务不存在、状态转换、必填字段验证、参数兼容性）
  - **验证**：`./mvnw.cmd test -Dtest=CompactionPipelineTest,SkillsIntegrationTest,TaskIntegrationTest` 通过（36 个测试全部通过）

