package demo.k8s.agent.config;

import demo.k8s.agent.skills.ClawhubClient;
import demo.k8s.agent.skills.SkillExecutorRegistry;
import demo.k8s.agent.skills.SkillRegistry;
import demo.k8s.agent.skills.SkillService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Skills 系统配置
 */
@Configuration
@EnableConfigurationProperties(SkillsProperties.class)
public class SkillsConfiguration {

    @Bean
    public SkillExecutorRegistry skillExecutorRegistry() {
        return new SkillExecutorRegistry();
    }

    @Bean
    public SkillRegistry skillRegistry() {
        return new SkillRegistry();
    }

    @Bean
    public SkillService skillService(SkillRegistry skillRegistry, SkillExecutorRegistry skillExecutorRegistry) {
        return new SkillService(skillRegistry, skillExecutorRegistry);
    }

    @Bean
    public ClawhubClient.ClawhubProperties clawhubProperties() {
        return new ClawhubClient.ClawhubProperties();
    }

    @Bean
    public ClawhubClient clawhubClient(ClawhubClient.ClawhubProperties properties) {
        return new ClawhubClient(properties);
    }
}
