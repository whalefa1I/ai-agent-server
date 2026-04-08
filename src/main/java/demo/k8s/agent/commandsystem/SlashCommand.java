package demo.k8s.agent.commandsystem;

import java.util.List;

/**
 * 与 {@code src/commands.ts} 中 {@code Command} 的「元数据子集」对齐：名称、别名、说明、来源、是否启用。
 * 本 demo 不包含 REPL 执行器；仅做注册表 + 合并 + 过滤，供 HTTP 自省或后续终端接入。
 */
public record SlashCommand(
        String name,
        List<String> aliases,
        String description,
        SlashCommandSource source,
        boolean enabled) {

    public SlashCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    /**
     * 勿再添加 {@code isEnabled()}：record 已有 {@code enabled} 组件与 {@code enabled()} 访问器，
     * 若与 Bean 风格 {@code isEnabled()} 并存，Jackson 序列化 {@code List<SlashCommand>} 时会冲突导致 HTTP 500。
     */
}
