package demo.k8s.agent.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 沙盒服务 - 通过 HTTP 调用远程 AgentScope Runtime 沙盒服务。
 * <p>
 * 支持功能：
 * <ul>
 *     <li>创建和管理沙盒会话</li>
 *     <li>执行 Python 代码和 Shell 命令</li>
 *     <li>文件系统操作</li>
 *     <li>浏览器自动化</li>
 *     <li>GUI 操作</li>
 *     <li>MCP 服务器集成</li>
 * </ul>
 * <p>
 * 使用方式：
 * <ol>
 *     <li>启动远程 AgentScope sandbox-server: runtime-sandbox-server</li>
 *     <li>配置服务地址：agentscope.sandbox.base-url</li>
 *     <li>通过本服务调用沙盒 API</li>
 * </ol>
 *
 * @deprecated 暂时禁用，修复 bean 冲突问题
 */
// @Service  // 已禁用 - 注释掉以避免 bean 冲突
@org.springframework.context.annotation.Profile("sandbox")  // 仅在 sandbox profile 启用
public class AgentScopeSandboxService {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeSandboxService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String defaultBaseUrl;

    // 会话管理
    private final Map<String, SandboxSessionInfo> activeSessions = new ConcurrentHashMap<>();

    /**
     * 构造注入。
     *
     * @param properties 沙盒配置属性
     */
    public AgentScopeSandboxService(AgentScopeSandboxProperties properties) {
        this.defaultBaseUrl = properties.getBaseUrl();
        this.webClient = WebClient.builder()
                .baseUrl(this.defaultBaseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("AgentScopeSandboxService 初始化完成，远程地址：{}", this.defaultBaseUrl);
    }

    /**
     * 创建新的沙盒会话。
     *
     * @param sandboxType 沙盒类型：base, gui, filesystem, browser, mobile
     * @param sessionId   会话 ID（可选，为空则自动生成）
     * @param userId      用户 ID
     * @return 会话信息
     */
    public SandboxSessionInfo createSession(String sandboxType, String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "session_" + System.currentTimeMillis();
        }

        log.info("创建沙盒会话：type={}, sessionId={}, userId={}", sandboxType, sessionId, userId);

        try {
            var requestBody = Map.of(
                    "session_id", sessionId,
                    "user_id", userId,
                    "sandbox_type", sandboxType);

            webClient.post()
                    .uri("/sandboxes/create")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.info("沙盒会话创建成功：{}", sessionId);

            var session = SandboxSessionInfo.of(sessionId, userId, sandboxType, defaultBaseUrl);
            activeSessions.put(sessionId, session);
            return session;

        } catch (Exception e) {
            log.error("创建沙盒会话失败：{}", e.getMessage());
            // 降级模式：返回本地会话信息
            var session = SandboxSessionInfo.of(sessionId, userId, sandboxType, defaultBaseUrl);
            activeSessions.put(sessionId, session);
            return session;
        }
    }

    /**
     * 获取或创建会话（如果会话已存在则复用）。
     */
    public SandboxSessionInfo getOrCreateSession(String sandboxType, String sessionId, String userId) {
        var existing = activeSessions.get(sessionId);
        if (existing != null) {
            log.debug("复用已有会话：{}", sessionId);
            return existing;
        }
        return createSession(sandboxType, sessionId, userId);
    }

    /**
     * 关闭沙盒会话。
     */
    public void closeSession(String sessionId) {
        log.info("关闭沙盒会话：{}", sessionId);

        try {
            webClient.post()
                    .uri("/sandboxes/close")
                    .bodyValue(Map.of("session_id", sessionId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            activeSessions.remove(sessionId);
            log.info("沙盒会话已关闭：{}", sessionId);

        } catch (Exception e) {
            log.warn("关闭沙盒会话时出错（已清理本地状态）：{}", e.getMessage());
            activeSessions.remove(sessionId);
        }
    }

    /**
     * 在沙盒中执行 Python 代码。
     *
     * @param sessionId 会话 ID
     * @param code      Python 代码
     * @return 执行结果
     */
    public SandboxToolResult executePython(String sessionId, String code) {
        log.debug("执行 Python 代码，会话：{}", sessionId);

        var requestBody = Map.of(
                "session_id", sessionId,
                "code", code);

        try {
            var response = webClient.post()
                    .uri("/sandboxes/execute/python")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            var result = OBJECT_MAPPER.readTree(response);
            var output = result.has("output") ? result.get("output").asText() : response;
            return SandboxToolResult.success(output);

        } catch (Exception e) {
            log.error("执行 Python 代码失败：{}", e.getMessage());
            return SandboxToolResult.error("Python 执行失败：" + e.getMessage());
        }
    }

    /**
     * 在沙盒中执行 Shell 命令。
     *
     * @param sessionId 会话 ID
     * @param command   Shell 命令
     * @return 执行结果
     */
    public SandboxToolResult executeShell(String sessionId, String command) {
        log.debug("执行 Shell 命令，会话：{}", sessionId);

        var requestBody = Map.of(
                "session_id", sessionId,
                "command", command);

        try {
            var response = webClient.post()
                    .uri("/sandboxes/execute/shell")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            var result = OBJECT_MAPPER.readTree(response);
            var output = result.has("output") ? result.get("output").asText() : response;
            return SandboxToolResult.success(output);

        } catch (Exception e) {
            log.error("执行 Shell 命令失败：{}", e.getMessage());
            return SandboxToolResult.error("Shell 执行失败：" + e.getMessage());
        }
    }

    /**
     * 列出沙盒中可用的工具。
     */
    public List<SandboxToolDefinition> listTools(String sessionId) {
        log.debug("列出工具，会话：{}", sessionId);

        try {
            var response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sandboxes/tools")
                            .queryParam("session_id", sessionId)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            var toolsNode = OBJECT_MAPPER.readTree(response);
            var tools = new java.util.ArrayList<SandboxToolDefinition>();

            if (toolsNode.isArray()) {
                for (JsonNode toolNode : toolsNode) {
                    var name = toolNode.has("name") ? toolNode.get("name").asText() : "unknown";
                    var description = toolNode.has("description") ? toolNode.get("description").asText() : "";
                    var schema = OBJECT_MAPPER.convertValue(
                            toolNode.get("input_schema"), Map.class);

                    tools.add(new SandboxToolDefinition(name, description, schema));
                }
            }

            return tools;

        } catch (Exception e) {
            log.error("列出工具失败：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 调用沙盒工具。
     *
     * @param sessionId 会话 ID
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 执行结果
     */
    public SandboxToolResult callTool(String sessionId, String toolName, Map<String, Object> arguments) {
        log.debug("调用工具：{}, 会话：{}", toolName, sessionId);

        var requestBody = new java.util.HashMap<String, Object>();
        requestBody.put("session_id", sessionId);
        requestBody.put("tool_name", toolName);
        requestBody.put("arguments", arguments);

        try {
            var response = webClient.post()
                    .uri("/sandboxes/tool/call")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            var result = OBJECT_MAPPER.readTree(response);

            if (result.has("error")) {
                return SandboxToolResult.error(result.get("error").asText());
            }

            var output = result.has("output") ? result.get("output").asText() : response;
            var content = result.has("content") ?
                    OBJECT_MAPPER.convertValue(result.get("content"), List.class) : List.of();
            var metadata = result.has("metadata") ?
                    OBJECT_MAPPER.convertValue(result.get("metadata"), Map.class) : Map.of();

            return SandboxToolResult.success(output, content);

        } catch (Exception e) {
            log.error("调用工具失败：{}", e.getMessage());
            return SandboxToolResult.error("工具调用失败：" + e.getMessage());
        }
    }

    /**
     * 添加 MCP 服务器到沙盒
     *
     * @param sessionId    会话 ID
     * @param serverConfig MCP 服务器配置
     * @return 是否成功
     */
    public boolean addMcpServer(String sessionId, Map<String, Object> serverConfig) {
        log.info("添加 MCP 服务器到沙盒，会话：{}", sessionId);

        Map<String, Object> requestBody = Map.of(
                "session_id", sessionId,
                "server_config", serverConfig
        );

        try {
            webClient.post()
                    .uri("/sandboxes/mcp/add")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return true;

        } catch (Exception e) {
            log.error("添加 MCP 服务器失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取活跃会话列表
     */
    public List<SandboxSessionInfo> getActiveSessions() {
        return List.copyOf(activeSessions.values());
    }

    /**
     * 检查服务是否可用
     */
    public boolean isHealthy() {
        try {
            String response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return response != null && response.contains("ok");
        } catch (Exception e) {
            log.warn("健康检查失败：{}", e.getMessage());
            return false;
        }
    }
}
