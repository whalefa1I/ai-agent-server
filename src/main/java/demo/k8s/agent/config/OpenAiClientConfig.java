package demo.k8s.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom OpenAI client configuration for Alibaba Bailian (DashScope) API.
 * Uses Authorization: Bearer header format for API key authentication.
 *
 * Configuration via environment variables:
 * - DASHSCOPE_API_KEY: API key for Bailian (required)
 * - DASHSCOPE_BASE_URL: Base URL (optional, defaults to https://coding.dashscope.aliyuncs.com)
 * - DASHSCOPE_MODEL: Model name (optional, defaults to qwen3.5-plus)
 */
@Configuration
public class OpenAiClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiClientConfig.class);

    @Value("${DASHSCOPE_API_KEY:}")
    private String apiKey;

    @Value("${DASHSCOPE_BASE_URL:https://coding.dashscope.aliyuncs.com}")
    private String baseUrl;

    @Value("${DASHSCOPE_MODEL:qwen3.5-plus}")
    private String model;

    @Bean
    @Primary
    public OpenAiApi openAiApi() {
        logger.info("Creating custom OpenAiApi bean with baseUrl={}, model={}", baseUrl, model);

        // Create custom RestClient with proper Authorization header
        RestClient.Builder restClientBuilder = RestClient.builder()
                .defaultHeaders(headers -> {
                    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
                });

        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    @Primary
    public ChatModel openAiChatModel(OpenAiApi openAiApi) {
        logger.info("Creating custom OpenAiChatModel bean with model={}", model);

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .build();
    }
}
