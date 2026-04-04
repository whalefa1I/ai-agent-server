package demo.k8s.agent.web;

import java.util.List;

import demo.k8s.agent.commandsystem.SlashCommand;
import demo.k8s.agent.commandsystem.SlashCommandService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自省用：列出当前合并后的斜杠命令元数据（无执行器）。生产可关或加鉴权。
 */
@RestController
@RequestMapping("/api/commands")
public class SlashCommandController {

    private final SlashCommandService slashCommandService;

    public SlashCommandController(SlashCommandService slashCommandService) {
        this.slashCommandService = slashCommandService;
    }

    @GetMapping("/slash")
    public List<SlashCommand> listSlashCommands() {
        return slashCommandService.getCommands();
    }
}
