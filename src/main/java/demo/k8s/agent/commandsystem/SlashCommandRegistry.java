package demo.k8s.agent.commandsystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 斜杠命令注册表
 * 管理命令名称到执行器的映射
 */
@Service
public class SlashCommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandRegistry.class);

    /**
     * 命令执行器注册表：命令名 -> 执行器
     */
    private final Map<String, SlashCommandExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 注册命令执行器
     *
     * @param commandName 命令名称（不带 /）
     * @param executor 执行器
     */
    public void register(String commandName, SlashCommandExecutor executor) {
        if (commandName == null || commandName.isBlank()) {
            throw new IllegalArgumentException("Command name cannot be empty");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }

        executors.put(commandName.toLowerCase(), executor);
        log.info("注册斜杠命令执行器：/{}", commandName);
    }

    /**
     * 获取命令执行器
     *
     * @param commandName 命令名称
     * @return 执行器，如果不存在则返回 null
     */
    public SlashCommandExecutor getExecutor(String commandName) {
        if (commandName == null) {
            return null;
        }
        return executors.get(commandName.toLowerCase());
    }

    /**
     * 检查命令是否已注册
     *
     * @param commandName 命令名称
     * @return true = 已注册
     */
    public boolean hasExecutor(String commandName) {
        return executors.containsKey(commandName.toLowerCase());
    }

    /**
     * 注销命令执行器
     *
     * @param commandName 命令名称
     */
    public void unregister(String commandName) {
        if (commandName != null) {
            executors.remove(commandName.toLowerCase());
            log.debug("注销斜杠命令：/{}", commandName);
        }
    }

    /**
     * 获取所有已注册的命令名称
     *
     * @return 命令名称列表
     */
    public java.util.Set<String> getRegisteredCommands() {
        return executors.keySet();
    }
}
