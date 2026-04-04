package demo.k8s.agent.tools.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP 远程工具执行器实现 - 通过 HTTP API 调用远程工具服务。
 * <p>
 * 使用示例：
 * <pre>{@code
 * HttpRemoteToolExecutor executor = new HttpRemoteToolExecutor();
 * LocalToolResult result = executor.executeRemoteSync(
 *     tool,
 *     Map.of("path", "/etc/hosts"),
 *     "http://remote-server:8080",
 *     "Bearer token123"
 * );
 * }</pre>
 */
public class HttpRemoteToolExecutor implements RemoteToolExecutor {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public HttpRemoteToolExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newCachedThreadPool();
    }

    public HttpRemoteToolExecutor(HttpClient httpClient, ObjectMapper objectMapper,
                                   ExecutorService executorService) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
    }

    @Override
    public CompletableFuture<LocalToolResult> executeRemote(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            String baseUrl,
            String authToken) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 构建请求
                String endpoint = baseUrl + "/api/tools/" + tool.name() + "/execute";

                Map<String, Object> requestBody = Map.of(
                        "toolName", tool.name(),
                        "input", input
                );

                String jsonBody = objectMapper.writeValueAsString(requestBody);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120));

                // 添加认证头
                if (authToken != null && !authToken.isEmpty()) {
                    requestBuilder.header("Authorization", authToken);
                }

                HttpRequest request = requestBuilder.build();

                // 发送请求
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                // 解析响应
                if (response.statusCode() == 200) {
                    LocalToolResult result = objectMapper.readValue(response.body(),
                            LocalToolResult.class);
                    return result;
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
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
