package demo.k8s.agent.commandsystem;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 短路版 {@code getCommands(cwd)}：按 {@link SlashCommandProvider} 的 Spring {@code @Order} 顺序拼接，
 * 再 {@link SlashCommandAssembly#mergeFirstWins(List)} 与 {@link SlashCommandAssembly#filterEnabled}。
 * 不读磁盘、不扫 {@code cwd}；需要技能目录命令时实现独立 Provider 即可。
 */
@Service
public class SlashCommandService {

    private final List<SlashCommandProvider> providers;

    public SlashCommandService(List<SlashCommandProvider> providers) {
        this.providers = providers;
    }

    /** 同步、无 I/O；与 TS 中「每次调用过滤 availability」相比，此处仅 {@link SlashCommand#enabled}。 */
    public List<SlashCommand> getCommands() {
        List<SlashCommand> flat = new ArrayList<>();
        for (SlashCommandProvider p : providers) {
            List<SlashCommand> loaded = p.loadCommands();
            if (loaded != null && !loaded.isEmpty()) {
                flat.addAll(loaded);
            }
        }
        return SlashCommandAssembly.filterEnabled(SlashCommandAssembly.mergeLastWins(flat));
    }
}
