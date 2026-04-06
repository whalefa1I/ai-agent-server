package demo.k8s.agent.tools.remote;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 远程工具测试端点 - 用于验证远程工具集成
 */
@RestController
@RequestMapping("/api/remote-tools")
public class RemoteToolTestController {

    private final PythonRemoteToolExecutor remoteToolExecutor;
    private final String baseUrl;
    private final String apiKey;

    public RemoteToolTestController(
            @Value("${remote.tools.base-url:}") String baseUrl,
            @Value("${remote.tools.api-key:}") String apiKey,
            PythonRemoteToolExecutor remoteToolExecutor) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.remoteToolExecutor = remoteToolExecutor;
    }

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return Map.of(
            "baseUrl", baseUrl != null ? baseUrl : "NOT_CONFIGURED",
            "apiKey", apiKey != null ? "***" : "NOT_CONFIGURED",
            "executorEnabled", remoteToolExecutor != null ? "true" : "false"
        );
    }

    @PostMapping("/test/bash")
    public Map<String, Object> testBash(@RequestBody Map<String, String> request) throws Exception {
        String command = request.getOrDefault("command", "echo test");

        var result = remoteToolExecutor.executeRemote(
            new demo.k8s.agent.toolsystem.ClaudeLikeTool() {
                @Override public String name() { return "bash"; }
                @Override public demo.k8s.agent.toolsystem.ToolCategory category() {
                    return demo.k8s.agent.toolsystem.ToolCategory.SHELL;
                }
                @Override public String description() { return "Test bash"; }
                @Override public String inputSchemaJson() { return "{}"; }
            },
            Map.of("command", command),
            baseUrl,
            apiKey
        ).join();

        return Map.of(
            "success", result.isSuccess() ? "true" : "false",
            "content", result.getContent() != null ? result.getContent() : "",
            "error", result.getError() != null ? result.getError() : ""
        );
    }
}
