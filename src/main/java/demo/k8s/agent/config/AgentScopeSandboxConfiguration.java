package demo.k8s.agent.config;

import demo.k8s.agent.sandbox.AgentScopeSandboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 沙盒配置
 *
 * @deprecated 暂时禁用，修复 bean 冲突问题
 */
// @Configuration  // 已禁用 - 注释掉以避免 bean 冲突
@EnableConfigurationProperties(AgentScopeSandboxProperties.class)
public class AgentScopeSandboxConfiguration {

    // AgentScopeSandboxService 和 Controller 会自动注册为 Bean
}
