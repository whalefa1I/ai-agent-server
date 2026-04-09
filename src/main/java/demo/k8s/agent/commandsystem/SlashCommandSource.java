package demo.k8s.agent.commandsystem;

/**
 * 与 Claude Code 中命令 {@code loadedFrom} / 来源分区对齐的粗粒度枚举（短路版）。
 */
public enum SlashCommandSource {

    /** 对应 {@code COMMANDS()} 静态表 */
    BUILTIN,

    /** 随应用发布的捆绑技能 */
    BUNDLED,

    /** 项目目录 {@code .claude/skills/} 等发现 */
    SKILL_DIR,

    /** 工作流脚本生成的命令 */
    WORKFLOW,

    /** 插件 / 扩展 */
    PLUGIN
}
