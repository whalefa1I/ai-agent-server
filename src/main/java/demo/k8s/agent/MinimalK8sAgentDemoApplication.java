package demo.k8s.agent;

import demo.k8s.agent.config.DemoContextObjectProperties;
import demo.k8s.agent.config.DemoContextObjectWriteProperties;
import demo.k8s.agent.config.DemoCoordinatorProperties;
import demo.k8s.agent.config.DemoDebugProperties;
import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.config.DemoOpsProperties;
import demo.k8s.agent.config.DemoQueryProperties;
import demo.k8s.agent.config.DemoToolsProperties;
import demo.k8s.agent.k8s.DemoK8sProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

@SpringBootApplication(exclude = {
    org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class
})
@EnableConfigurationProperties({
    DemoK8sProperties.class,
    DemoToolsProperties.class,
    DemoQueryProperties.class,
    DemoCoordinatorProperties.class,
    DemoMultiAgentProperties.class,
    DemoOpsProperties.class,
    DemoContextObjectProperties.class,
    DemoContextObjectWriteProperties.class,
    DemoDebugProperties.class
})
public class MinimalK8sAgentDemoApplication {

    private static final Logger log = LoggerFactory.getLogger(MinimalK8sAgentDemoApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MinimalK8sAgentDemoApplication.class);
        app.addListeners((ApplicationListener<ContextRefreshedEvent>) event -> {
            DemoMultiAgentProperties multiAgent = event.getApplicationContext().getBean(DemoMultiAgentProperties.class);
            log.info("[MultiAgentConfig] enabled={}, mode={}, maxSpawnDepth={}, maxConcurrentSpawns={}, wallclockTtl={}s",
                    multiAgent.isEnabled(),
                    multiAgent.getMode(),
                    multiAgent.getMaxSpawnDepth(),
                    multiAgent.getMaxConcurrentSpawns(),
                    multiAgent.getWallclockTtlSeconds());
        });
        app.run(args);
    }
}
