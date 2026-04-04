package demo.k8s.agent.commandsystem;

import java.util.ArrayList;
import java.util.List;

import demo.k8s.agent.config.DemoToolsProperties;

/**
 * 对应 {@code commands.ts} 中 {@code COMMANDS()} 的<strong>极小</strong>子集（占位名与说明），非全量 80+。
 * 特性门控命令用 {@link #withFeatureGated(DemoToolsProperties)} 按需追加，类比 TS 中 {@code feature('...') ? require(...) : null}。
 */
public final class BuiltinSlashCommands {

    private BuiltinSlashCommands() {}

    public static List<SlashCommand> baseline() {
        return List.of(
                new SlashCommand("clear", List.of(), "清除当前会话上下文", SlashCommandSource.BUILTIN, true),
                new SlashCommand("compact", List.of(), "压缩上下文窗口", SlashCommandSource.BUILTIN, true),
                new SlashCommand("help", List.of(), "显示命令帮助", SlashCommandSource.BUILTIN, true),
                new SlashCommand("config", List.of(), "查看或修改配置", SlashCommandSource.BUILTIN, true),
                new SlashCommand("review", List.of(), "代码审查流程", SlashCommandSource.BUILTIN, true));
    }

    /**
     * 与 TS 中有条件 {@code ultraplan}、{@code proactive} 等类似：由配置决定是否暴露占位项。
     */
    public static List<SlashCommand> withFeatureGated(DemoToolsProperties props) {
        List<SlashCommand> out = new ArrayList<>(baseline());
        DemoToolsProperties.Feature f = props.getFeature() != null ? props.getFeature() : new DemoToolsProperties.Feature();
        if (f.isExperimental()) {
            out.add(
                    new SlashCommand(
                            "ultraplan",
                            List.of(),
                            "[实验] 超长程规划（占位）",
                            SlashCommandSource.BUILTIN,
                            true));
        }
        return List.copyOf(out);
    }
}
