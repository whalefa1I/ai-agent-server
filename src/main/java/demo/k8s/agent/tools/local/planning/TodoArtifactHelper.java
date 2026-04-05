package demo.k8s.agent.tools.local.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.privacykit.PrivacyKitService;
import demo.k8s.agent.toolstate.ToolArtifact;
import demo.k8s.agent.toolstate.ToolArtifactRepository;
import demo.k8s.agent.toolstate.ToolStateService;
import demo.k8s.agent.toolstate.ToolStatus;
import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TodoArtifactHelper - 为 todo_write 工具创建和管理 artifact
 *
 * 这个类帮助将 todo 事项保存为 artifact，使得前端可以在聊天流中显示待办事项。
 * 使用 TraceContext 获取会话信息，通过静态方法访问依赖服务。
 */
public class TodoArtifactHelper {

    private static final Logger log = LoggerFactory.getLogger(TodoArtifactHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 静态字段存储服务实例（通过 setServices 注入）
    private static ToolStateService toolStateService;
    private static ToolArtifactRepository repository;
    private static PrivacyKitService privacyKitService;

    /**
     * 设置依赖服务（在应用启动时调用）
     */
    public static void setServices(ToolStateService service, ToolArtifactRepository repo, PrivacyKitService privacy) {
        toolStateService = service;
        repository = repo;
        privacyKitService = privacy;
    }

    /**
     * 创建 todo artifact
     *
     * @param todoContent todo 内容
     * @param todoId todo ID
     * @param assignee 负责人
     * @return 创建的 artifact ID，失败时返回 null
     */
    public static String createTodoArtifact(String todoContent, String todoId, String assignee) {
        if (repository == null || privacyKitService == null) {
            log.warn("依赖服务未初始化");
            return null;
        }

        // 从 TraceContext 获取 sessionId
        String sessionId = TraceContext.getSessionId();

        // 如果 TraceContext 中没有，尝试从最近的 artifact 获取
        if (sessionId == null) {
            var all = repository.findAll();
            if (all != null && !all.isEmpty()) {
                sessionId = all.get(all.size() - 1).getSessionId();
                log.info("从最近 artifact 获取 sessionId: {}", sessionId);
            }
        }

        if (sessionId == null) {
            log.warn("无法获取 sessionId");
            return null;
        }

        // 使用 sessionId 作为 accountId（支持匿名会话）
        String accountId = sessionId;

        try {
            String artifactId = "todo-" + UUID.randomUUID().toString().substring(0, 8);

            // 构建 header JSON（Happy 兼容格式）
            Map<String, Object> header = new HashMap<>();
            header.put("type", "todo");
            header.put("subtype", "todo-item");
            header.put("toolName", "todo_write");
            header.put("toolDisplayName", "待办事项");
            header.put("icon", "📝");
            header.put("status", "todo");
            header.put("todoId", todoId);
            header.put("content", todoContent);
            header.put("timestamp", System.currentTimeMillis());

            // 构建 body JSON
            Map<String, Object> body = new HashMap<>();
            body.put("type", "todo-item");
            body.put("status", "pending");
            body.put("content", todoContent);
            body.put("todoId", todoId);
            if (assignee != null) {
                body.put("assignee", assignee);
            }
            body.put("timestamp", System.currentTimeMillis());

            // Base64 编码
            String headerJson = objectMapper.writeValueAsString(header);
            String bodyJson = objectMapper.writeValueAsString(body);
            String encryptedHeader = privacyKitService.encodeBase64(headerJson.getBytes());
            String encryptedBody = privacyKitService.encodeBase64(bodyJson.getBytes());

            // 创建实体
            ToolArtifact artifact = new ToolArtifact();
            artifact.setId(artifactId);
            artifact.setSessionId(sessionId);
            artifact.setAccountId(accountId);
            artifact.setHeader(encryptedHeader);
            artifact.setHeaderVersion(1);
            artifact.setBody(encryptedBody);
            artifact.setBodyVersion(1);
            artifact.setSeq(0);
            artifact.setCreatedAt(Instant.now());
            artifact.setUpdatedAt(Instant.now());

            repository.save(artifact);
            log.info("创建 todo artifact: id={}, todoId={}, content={}", artifactId, todoId, todoContent);

            return artifactId;

        } catch (Exception e) {
            log.error("创建 todo artifact 失败", e);
            return null;
        }
    }

    /**
     * 更新 todo artifact 状态
     *
     * @param todoId todo ID
     * @param status 新状态
     * @param content 新内容
     * @return 是否更新成功
     */
    public static boolean updateTodoArtifact(String todoId, String status, String content) {
        String sessionId = TraceContext.getSessionId();

        // 使用 sessionId 作为 accountId（支持匿名会话）
        String accountId = sessionId;

        if (sessionId == null || repository == null) {
            return false;
        }

        try {
            // 查找对应的 artifact
            var artifacts = repository.findBySessionIdOrderBySeq(sessionId);
            for (ToolArtifact artifact : artifacts) {
                try {
                    String headerJson = new String(java.util.Base64.getDecoder().decode(artifact.getHeader()));
                    var header = objectMapper.readTree(headerJson);

                    if ("todo".equals(header.get("type").asText()) &&
                        todoId.equals(header.get("todoId").asText())) {

                        // 更新 body
                        Map<String, Object> body = new HashMap<>();
                        body.put("type", "todo-item");
                        body.put("status", status);
                        body.put("content", content != null ? content : header.get("content").asText());
                        body.put("todoId", todoId);
                        if (header.has("assignee")) {
                            body.put("assignee", header.get("assignee").asText());
                        }
                        body.put("timestamp", System.currentTimeMillis());

                        String bodyJson = objectMapper.writeValueAsString(body);
                        String encryptedBody = privacyKitService.encodeBase64(bodyJson.getBytes());

                        artifact.setBody(encryptedBody);
                        artifact.setBodyVersion(artifact.getBodyVersion() + 1);
                        artifact.setUpdatedAt(Instant.now());

                        repository.save(artifact);
                        log.info("更新 todo artifact: todoId={}, status={}", todoId, status);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("解析 artifact header 失败，跳过：{}", e.getMessage());
                }
            }

            log.warn("未找到 todo artifact: todoId={}", todoId);
            return false;

        } catch (Exception e) {
            log.error("更新 todo artifact 失败：todoId={}", todoId, e);
            return false;
        }
    }

    /**
     * 根据 artifact ID 删除 todo artifact
     */
    public static boolean deleteTodoArtifact(String artifactId) {
        String sessionId = TraceContext.getSessionId();

        // 使用 sessionId 作为 accountId（支持匿名会话）
        String accountId = sessionId;

        if (sessionId == null || repository == null) {
            return false;
        }

        try {
            var optional = repository.findByIdAndAccountId(artifactId, accountId);
            if (optional.isPresent()) {
                repository.delete(optional.get());
                log.info("删除 todo artifact: id={}", artifactId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("删除 todo artifact 失败：id={}", artifactId, e);
            return false;
        }
    }
}
