package demo.k8s.agent.commandsystem;

import java.util.List;

/**
 * 命令来源之一：内置静态表、技能目录、捆绑包、插件等。短路实现下多数 Provider 返回空列表，仅 {@link BuiltinSlashCommands} 有数据。
 */
@FunctionalInterface
public interface SlashCommandProvider {

    List<SlashCommand> loadCommands();
}
