package demo.k8s.agent.commandsystem;

import jakarta.annotation.PostConstruct;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Configuration;

import demo.k8s.agent.commandsystem.executors.CompactCommandExecutor;
import demo.k8s.agent.commandsystem.executors.ContextCommandExecutor;
import demo.k8s.agent.commandsystem.executors.PlanCommandExecutor;
import demo.k8s.agent.state.ConversationSession;

/**
 * 斜杠命令执行器配置
 * 注册所有内置命令的执行器
 */
@Configuration
public class SlashCommandExecutorConfiguration {

    private final SlashCommandRegistry registry;
    private final ConversationSession conversationSession;
    private final ChatModel chatModel;

    public SlashCommandExecutorConfiguration(
            SlashCommandRegistry registry,
            ConversationSession conversationSession,
            ChatModel chatModel) {
        this.registry = registry;
        this.conversationSession = conversationSession;
        this.chatModel = chatModel;
    }

    @PostConstruct
    public void registerExecutors() {
        // 注册 /compact 命令
        registry.register("compact", new CompactCommandExecutor(conversationSession, chatModel));

        // 注册 /context 命令
        registry.register("context", new ContextCommandExecutor(conversationSession));

        // 注册 /plan 命令
        registry.register("plan", new PlanCommandExecutor(conversationSession));

        // 注册 /help 命令
        registry.register("help", (sessionId, args) -> {
            String helpText = """
                ## 可用命令

                ### 会话管理
                - `/compact [instructions]` - 压缩对话上下文，保留摘要
                - `/context` - 查看上下文使用情况
                - `/clear` - 清除当前会话

                ### 计划模式
                - `/plan` - 启用计划模式或查看当前计划
                - `/plan open` - 在编辑器中打开计划文件
                - `/plan off` - 禁用计划模式
                - `/plan <description>` - 设置计划描述

                ### 其他
                - `/help` - 显示此帮助信息
                - `/config` - 查看或修改配置

                提示：输入 / 后按 Tab 可以看到所有可用命令的列表。
                """;
            return SlashCommandResult.Text.of(helpText);
        });

        // 注册 /clear 命令
        registry.register("clear", (sessionId, args) -> {
            // TODO: 实现清除会话逻辑
            return SlashCommandResult.Text.of("清除会话功能开发中...");
        });

        // 注册 /config 命令
        registry.register("config", (sessionId, args) -> {
            // TODO: 实现配置查看/修改逻辑
            return SlashCommandResult.Text.of("配置功能开发中...");
        });

        // 注册 /model 命令
        registry.register("model", (sessionId, args) -> {
            // TODO: 实现模型切换逻辑
            return SlashCommandResult.Text.of("模型切换功能开发中...");
        });

        // 注册 /fast 命令
        registry.register("fast", (sessionId, args) -> {
            // TODO: 实现快速模式切换逻辑
            return SlashCommandResult.Text.of("快速模式功能开发中...");
        });
    }
}
