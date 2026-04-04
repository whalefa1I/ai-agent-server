package demo.k8s.agent.toolstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.privacykit.PrivacyKitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ToolStateService - 工具状态管理服务
 *
 * 参考 happy-server 的 artifactUpdateHandler 设计：
 * - 创建/读取/更新/删除 ToolArtifact
 * - 乐观并发控制（通过 version 检查）
 * - 加密存储 header/body
 * - 广播事件到 WebSocket 客户端
 */
@Service
public class ToolStateService {

    private static final Logger log = LoggerFactory.getLogger(ToolStateService.class);

    @Autowired
    private ToolArtifactRepository repository;

    @Autowired
    private ToolEventRouter eventRouter;

    @Autowired
    private PrivacyKitService privacyKitService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 创建工具状态 ====================

    /**
     * 创建新的工具状态
     *
     * @param sessionId 会话 ID
     * @param accountId 账户 ID
     * @param toolName 工具名称
     * @param toolType 工具类型
     * @param initialStatus 初始状态
     * @param body 初始 body 内容
     * @param session 发起创建的 WebSocket 会话（用于跳过 echo）
     * @return 创建的 ToolArtifact
     */
    @Transactional
    public ToolArtifact createToolArtifact(
        String sessionId,
        String accountId,
        String toolName,
        String toolType,
        ToolStatus initialStatus,
        Map<String, Object> body,
        WebSocketSession session
    ) {
        String artifactId = UUID.randomUUID().toString();

        // 构建 header JSON
        String headerJson = toJson(Map.of(
            "name", toolName,
            "type", toolType,
            "status", initialStatus.getValue(),
            "version", 1
        ));

        // 构建 body JSON
        String bodyJson = body != null ? toJson(body) : null;

        // 加密存储
        String encryptedHeader = privacyKitService.encodeBase64(headerJson.getBytes());
        String encryptedBody = bodyJson != null ? privacyKitService.encodeBase64(bodyJson.getBytes()) : null;

        // 创建实体
        ToolArtifact artifact = new ToolArtifact();
        artifact.setId(artifactId);
        artifact.setSessionId(sessionId);
        artifact.setAccountId(accountId);
        artifact.setHeader(headerJson);
        artifact.setHeaderVersion(1);
        artifact.setBody(bodyJson);
        artifact.setBodyVersion(1);
        artifact.setSeq(0);
        artifact.setCreatedAt(Instant.now());
        artifact.setUpdatedAt(Instant.now());

        ToolArtifact saved = repository.save(artifact);

        // 广播创建事件
        eventRouter.emitToolStateCreated(accountId, saved, session);

        log.info("创建工具状态：artifactId={}, toolName={}, status={}", artifactId, toolName, initialStatus);
        return saved;
    }

    // ==================== 读取工具状态 ====================

    /**
     * 获取工具状态
     */
    @Transactional(readOnly = true)
    public Optional<ToolArtifact> getToolArtifact(String artifactId, String accountId) {
        return repository.findByIdAndAccountId(artifactId, accountId);
    }

    /**
     * 获取会话的所有工具状态
     */
    @Transactional(readOnly = true)
    public List<ToolArtifact> getSessionToolArtifacts(String sessionId) {
        return repository.findBySessionIdOrderBySeq(sessionId);
    }

    // ==================== 更新工具状态 ====================

    /**
     * 更新工具状态（乐观并发控制）
     *
     * @param artifactId 工具状态 ID
     * @param accountId 账户 ID
     * @param status 新状态
     * @param body 新 body 内容
     * @param expectedVersion 期望的当前版本号（用于乐观锁检查）
     * @param session 发起更新的 WebSocket 会话
     * @return 更新结果（成功/版本冲突）
     */
    @Transactional
    public UpdateResult updateToolArtifact(
        String artifactId,
        String accountId,
        ToolStatus status,
        Map<String, Object> body,
        int expectedVersion,
        WebSocketSession session
    ) {
        Optional<ToolArtifact> optional = repository.findByIdAndAccountId(artifactId, accountId);
        if (optional.isEmpty()) {
            return UpdateResult.notFound();
        }

        ToolArtifact artifact = optional.get();

        // 检查版本冲突
        if (artifact.getBodyVersion() != expectedVersion) {
            log.warn("版本冲突：artifactId={}, expected={}, current={}",
                artifactId, expectedVersion, artifact.getBodyVersion());
            return UpdateResult.versionMismatch(artifact.getBodyVersion(), artifact.getBody());
        }

        // 更新 header（状态变更）
        String headerJson;
        try {
            var headerTree = objectMapper.readTree(artifact.getHeader());
            headerJson = toJson(Map.of(
                "name", headerTree.get("name").asText(),
                "type", headerTree.get("type").asText(),
                "status", status.getValue(),
                "version", artifact.getHeaderVersion() + 1
            ));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("解析 header JSON 失败", e);
        }

        // 更新 body
        String bodyJson = body != null ? toJson(body) : null;
        long newSeq = artifact.getSeq() + 1;

        int updated = repository.updateBodyOptimistic(
            artifactId, accountId, bodyJson, expectedVersion, newSeq
        );

        if (updated == 0) {
            // 并发更新导致版本冲突
            log.warn("乐观锁更新失败：artifactId={}", artifactId);
            ToolArtifact refreshed = repository.findByIdAndAccountId(artifactId, accountId).orElse(artifact);
            return UpdateResult.versionMismatch(refreshed.getBodyVersion(), refreshed.getBody());
        }

        // 重新读取更新后的实体
        ToolArtifact updatedArtifact = repository.findByIdAndAccountId(artifactId, accountId).orElseThrow();

        // 广播更新事件
        eventRouter.emitToolStateUpdate(accountId, updatedArtifact, session);

        log.info("更新工具状态：artifactId={}, newStatus={}, newVersion={}", artifactId, status, updatedArtifact.getBodyVersion());
        return UpdateResult.success(updatedArtifact);
    }

    // ==================== 删除工具状态 ====================

    /**
     * 删除工具状态
     */
    @Transactional
    public boolean deleteToolArtifact(String artifactId, String accountId, WebSocketSession session) {
        Optional<ToolArtifact> optional = repository.findByIdAndAccountId(artifactId, accountId);
        if (optional.isEmpty()) {
            return false;
        }

        repository.delete(optional.get());

        // 广播删除事件
        eventRouter.emitToolStateDeleted(accountId, artifactId, session);

        log.info("删除工具状态：artifactId={}", artifactId);
        return true;
    }

    // ==================== 工具方法 ====================

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    // ==================== 更新结果类 ====================

    public static class UpdateResult {
        private final boolean success;
        private final boolean versionMismatch;
        private final ToolArtifact artifact;
        private final Integer currentVersion;
        private final String currentBody;

        private UpdateResult(boolean success, boolean versionMismatch, ToolArtifact artifact, Integer currentVersion, String currentBody) {
            this.success = success;
            this.versionMismatch = versionMismatch;
            this.artifact = artifact;
            this.currentVersion = currentVersion;
            this.currentBody = currentBody;
        }

        public static UpdateResult success(ToolArtifact artifact) {
            return new UpdateResult(true, false, artifact, null, null);
        }

        public static UpdateResult notFound() {
            return new UpdateResult(false, false, null, null, null);
        }

        public static UpdateResult versionMismatch(int currentVersion, String currentBody) {
            return new UpdateResult(false, true, null, currentVersion, currentBody);
        }

        public boolean isSuccess() { return success; }
        public boolean isVersionMismatch() { return versionMismatch; }
        public Optional<ToolArtifact> getArtifact() { return Optional.ofNullable(artifact); }
        public Optional<Integer> getCurrentVersion() { return Optional.ofNullable(currentVersion); }
        public Optional<String> getCurrentBody() { return Optional.ofNullable(currentBody); }
    }
}
