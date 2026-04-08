package demo.k8s.agent;

import demo.k8s.agent.config.DemoCoordinatorProperties;
import demo.k8s.agent.config.DemoDebugProperties;
import demo.k8s.agent.config.DemoQueryProperties;
import demo.k8s.agent.config.DemoToolsProperties;
import demo.k8s.agent.k8s.DemoK8sProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

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
    DemoDebugProperties.class
})
public class MinimalK8sAgentDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinimalK8sAgentDemoApplication.class, args);
    }
}
