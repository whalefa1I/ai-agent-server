package demo.k8s.agent.config;

import demo.k8s.agent.toolsystem.McpToolProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * MCP Tool 配置。
 * <p>
 * 默认提供 noop 实现（空操作），避免在没有 MCP 服务器时启动失败。
 * 如果需要 MCP 功能，请在具体实现类上添加 @Primary。
 */
@Configuration
public class McpToolConfiguration {

    @Bean
    @Primary
    McpToolProvider noopMcpToolProvider() {
        return List::of;
    }
}
