package demo.k8s.agent.web;

import java.util.List;
import java.util.Map;

import demo.k8s.agent.commandsystem.SlashCommand;
import demo.k8s.agent.commandsystem.SlashCommandExecutor;
import demo.k8s.agent.commandsystem.SlashCommandRegistry;
import demo.k8s.agent.commandsystem.SlashCommandResult;
import demo.k8s.agent.commandsystem.SlashCommandService;
import demo.k8s.agent.state.ConversationSession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 斜杠命令控制器
 * 提供命令列表查询和命令执行功能
 */
@RestController
@RequestMapping("/api/commands")
public class SlashCommandController {

    private final SlashCommandService slashCommandService;
    private final SlashCommandRegistry slashCommandRegistry;
    private final ConversationSession conversationSession;

    public SlashCommandController(
            SlashCommandService slashCommandService,
            SlashCommandRegistry slashCommandRegistry,
            ConversationSession conversationSession) {
        this.slashCommandService = slashCommandService;
        this.slashCommandRegistry = slashCommandRegistry;
        this.conversationSession = conversationSession;
    }

    /**
     * 列出所有可用的斜杠命令
     */
    @GetMapping("/slash")
    public List<SlashCommand> listSlashCommands() {
        return slashCommandService.getCommands();
    }

    /**
     * 执行斜杠命令
     *
     * @param request 执行请求，包含命令名和参数
     * @return 执行结果
     */
    @PostMapping("/execute")
    public Map<String, Object> executeCommand(@RequestBody ExecuteCommandRequest request) {
        String sessionId = conversationSession.getSessionId();

        // 查找执行器
        SlashCommandExecutor executor = slashCommandRegistry.getExecutor(request.command());
        if (executor == null) {
            return Map.of(
                "success", false,
                "error", "Unknown command: /" + request.command(),
                "type", "error"
            );
        }

        // 执行命令
        SlashCommandResult result = executor.execute(sessionId, request.args());

        // 转换为响应格式
        return switch (result) {
            case SlashCommandResult.Text text -> Map.of(
                "success", true,
                "content", text.content(),
                "type", "text"
            );
            case SlashCommandResult.Error error -> Map.of(
                "success", false,
                "message", error.message(),
                "code", error.code(),
                "type", "error"
            );
            case SlashCommandResult.Confirmation confirmation -> Map.of(
                "success", true,
                "title", confirmation.title(),
                "message", confirmation.message(),
                "confirmLabel", confirmation.confirmLabel(),
                "cancelLabel", confirmation.cancelLabel(),
                "payload", confirmation.payload(),
                "type", "confirmation"
            );
            case SlashCommandResult.PlanMode planMode -> Map.of(
                "success", true,
                "enabled", planMode.enabled(),
                "planContent", planMode.planContent(),
                "planFilePath", planMode.planFilePath(),
                "type", "plan_mode"
            );
        };
    }

    /**
     * 执行请求记录
     */
    public record ExecuteCommandRequest(
        String command,
        String args
    ) {}
}
