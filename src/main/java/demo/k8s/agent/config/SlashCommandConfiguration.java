package demo.k8s.agent.config;

import java.util.List;

import demo.k8s.agent.commandsystem.BuiltinSlashCommands;
import demo.k8s.agent.commandsystem.SlashCommandProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 短路命令来源：空分区 + 内置表。顺序对齐 {@code loadAllCommands}：bundled → … → COMMANDS（内置放最后优先级最高可覆盖同名）。
 */
@Configuration
public class SlashCommandConfiguration {

    @Bean
    @Order(10)
    SlashCommandProvider bundledSlashCommandProvider() {
        return List::of;
    }

    @Bean
    @Order(20)
    SlashCommandProvider skillDirSlashCommandProvider() {
        return List::of;
    }

    @Bean
    @Order(30)
    SlashCommandProvider workflowSlashCommandProvider() {
        return List::of;
    }

    @Bean
    @Order(40)
    SlashCommandProvider pluginSlashCommandProvider() {
        return List::of;
    }

    @Bean
    @Order(100)
    SlashCommandProvider builtinSlashCommandProvider(DemoToolsProperties props) {
        return () -> BuiltinSlashCommands.withFeatureGated(props);
    }
}
