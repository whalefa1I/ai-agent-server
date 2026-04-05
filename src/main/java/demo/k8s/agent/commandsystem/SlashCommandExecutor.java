package demo.k8s.agent.commandsystem;

/**
 * 斜杠命令执行器接口
 * 与 claude-code 的 CommandExecutor 对齐
 */
@FunctionalInterface
public interface SlashCommandExecutor {

    /**
     * 执行斜杠命令
     *
     * @param sessionId 会话 ID
     * @param args 命令参数
     * @return 执行结果
     */
    SlashCommandResult execute(String sessionId, String args);
}
