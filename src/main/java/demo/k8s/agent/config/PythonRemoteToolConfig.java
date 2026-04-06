package demo.k8s.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.remote.PythonRemoteToolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Python 远程工具执行器配置
 */
@Configuration
@ConditionalOnProperty(prefix = "remote.tools", name = "enabled", havingValue = "true")
public class PythonRemoteToolConfig {

    @Value("${remote.tools.base-url:}")
    private String baseUrl;

    @Value("${remote.tools.api-key:}")
    private String apiKey;

    @Value("${remote.tools.timeout-seconds:60}")
    private int timeoutSeconds;

    @Bean
    public HttpClient remoteHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ExecutorService remoteExecutorService() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public PythonRemoteToolExecutor pythonRemoteToolExecutor(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Qualifier("remoteExecutorService") ExecutorService executorService) {
        return new PythonRemoteToolExecutor(
                baseUrl,
                apiKey,
                timeoutSeconds,
                httpClient,
                objectMapper,
                executorService
        );
    }
}
