package demo.k8s.agent.commandsystem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对齐 {@code commands.ts} 中 {@code loadAllCommands} 的拼接顺序（短路实现）：
 *
 * <pre>
 *   bundled → builtinPluginSkills → skillDir → workflow → plugin → pluginSkills → COMMANDS
 * </pre>
 *
 * <p>去重策略与 {@code getCommands} 中「后出现的动态技能不覆盖已有名」一致：先遍历的分区优先保留（先注册者优先）。
 */
public final class SlashCommandAssembly {

    private SlashCommandAssembly() {}

    /**
     * 同名只保留第一次出现（先注册者优先）。
     */
    public static List<SlashCommand> mergeFirstWins(List<SlashCommand> inOrder) {
        Map<String, SlashCommand> byName = new LinkedHashMap<>();
        for (SlashCommand c : inOrder) {
            byName.putIfAbsent(c.name(), c);
        }
        return List.copyOf(byName.values());
    }

    /**
     * 与 {@code loadAllCommands} 拼接顺序一致时：靠后的分区（如 {@code COMMANDS()}）覆盖同名命令。
     */
    public static List<SlashCommand> mergeLastWins(List<SlashCommand> inOrder) {
        Map<String, SlashCommand> byName = new LinkedHashMap<>();
        for (SlashCommand c : inOrder) {
            byName.put(c.name(), c);
        }
        return List.copyOf(byName.values());
    }

    @SafeVarargs
    public static List<SlashCommand> mergeFirstWins(List<SlashCommand>... partitions) {
        List<SlashCommand> flat = new java.util.ArrayList<>();
        for (List<SlashCommand> part : partitions) {
            if (part != null) {
                flat.addAll(part);
            }
        }
        return mergeFirstWins(flat);
    }

    /** 对应 {@code meetsAvailabilityRequirement && isCommandEnabled} 的简化：仅 {@link SlashCommand#enabled()} */
    public static List<SlashCommand> filterEnabled(List<SlashCommand> commands) {
        return commands.stream().filter(SlashCommand::enabled).toList();
    }
}
