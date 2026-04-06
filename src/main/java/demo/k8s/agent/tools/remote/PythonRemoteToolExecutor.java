package demo.k8s.agent.tools.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.observability.tracing.TraceContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Python 远程工具执行器
 * 适配 Python FastAPI 服务的 API 格式
 */
public class PythonRemoteToolExecutor implements RemoteToolExecutor {

    private final String baseUrl;
    private final String apiKey;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public PythonRemoteToolExecutor(
            String baseUrl,
            String apiKey,
            int timeoutSeconds,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ExecutorService executorService) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
    }

    @Override
    public CompletableFuture<LocalToolResult> executeRemote(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            String overrideBaseUrl,
            String overrideAuthToken) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 使用覆盖的 URL 或配置的默认 URL
                String effectiveBaseUrl = overrideBaseUrl != null ? overrideBaseUrl : baseUrl;
                String effectiveAuthToken = overrideAuthToken != null ? overrideAuthToken : apiKey;

                if (effectiveBaseUrl == null || effectiveBaseUrl.isEmpty()) {
                    return LocalToolResult.error("Remote tools not configured: base-url is missing");
                }

                // Python 服务 API: POST /tools/execute
                String endpoint = effectiveBaseUrl + "/tools/execute";

                // 请求体格式：{"tool_name": "xxx", "input": {...}}
                Map<String, Object> requestBody = Map.of(
                        "tool_name", tool.name(),
                        "input", input
                );

                String jsonBody = objectMapper.writeValueAsString(requestBody);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(timeoutSeconds));

                // 添加认证头和用户标识头
                if (effectiveAuthToken != null && !effectiveAuthToken.isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + effectiveAuthToken);
                }

                // 添加用户标识（从 TraceContext 获取）
                String userId = TraceContext.getUserId();
                String sessionId = TraceContext.getSessionId();
                if (userId != null) {
                    requestBuilder.header("X-User-ID", userId);
                } else {
                    requestBuilder.header("X-User-ID", "anonymous");
                }
                if (sessionId != null) {
                    requestBuilder.header("X-Session-ID", sessionId);
                } else {
                    requestBuilder.header("X-Session-ID", "default");
                }

                HttpRequest request = requestBuilder.build();

                // 发送请求
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                // 解析响应
                if (response.statusCode() == 200) {
                    PythonToolResponse pythonResponse = objectMapper.readValue(
                            response.body(),
                            PythonToolResponse.class
                    );

                    if (pythonResponse.success) {
                        return LocalToolResult.success(pythonResponse.output);
                    } else {
                        return LocalToolResult.error(pythonResponse.error);
                    }
                } else {
                    return LocalToolResult.error(
                            "HTTP Error: " + response.statusCode() + " - " + response.body());
                }

            } catch (IOException e) {
                return LocalToolResult.error("Network error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return LocalToolResult.error("Request interrupted");
            } catch (Exception e) {
                return LocalToolResult.error("Unexpected error: " + e.getMessage());
            }
        }, executorService);
    }

    /**
     * Python 工具响应格式
     */
    private static class PythonToolResponse {
        public boolean success;
        public String output;
        public String error;
        public Integer duration_ms;
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
