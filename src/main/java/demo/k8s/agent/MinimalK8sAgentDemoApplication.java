package demo.k8s.agent;

import demo.k8s.agent.config.DemoCoordinatorProperties;
import demo.k8s.agent.config.DemoQueryProperties;
import demo.k8s.agent.config.DemoToolsProperties;
import demo.k8s.agent.k8s.DemoK8sProperties;
import demo.k8s.agent.quota.QuotaConfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    DemoK8sProperties.class,
    DemoToolsProperties.class,
    DemoQueryProperties.class,
    DemoCoordinatorProperties.class,
    QuotaConfig.class
})
public class MinimalK8sAgentDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinimalK8sAgentDemoApplication.class, args);
    }
}
