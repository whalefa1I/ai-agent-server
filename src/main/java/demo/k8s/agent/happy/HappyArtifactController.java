package demo.k8s.agent.happy;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.privacykit.PrivacyKitService;
import demo.k8s.agent.toolstate.ToolArtifact;
import demo.k8s.agent.toolstate.ToolArtifactRepository;
import demo.k8s.agent.toolstate.ToolStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * HappyArtifactController - Happy 兼容的 Artifact API 控制器
 *
 * 实现与 happy-server 相同的 API 接口，使得 Happy 前端可以直接使用 ai-agent-server
 * 作为后端服务。
 *
 * API 端点：
 * - GET    /api/v1/artifacts          - 获取所有 artifacts (仅返回 header)
 * - GET    /api/v1/artifacts/:id      - 获取单个 artifact (返回 header + body)
 * - POST   /api/v1/artifacts          - 创建新的 artifact
 * - POST   /api/v1/artifacts/:id      - 更新 artifact (带版本控制)
 * - DELETE /api/v1/artifacts/:id      - 删除 artifact
 */
@RestController
@RequestMapping("/api/v1/artifacts")
@CrossOrigin(origins = "*")
public class HappyArtifactController {

    private static final Logger log = LoggerFactory.getLogger(HappyArtifactController.class);

    @Autowired
    private ToolArtifactRepository repository;

    @Autowired
    private ToolStateService toolStateService;

    @Autowired
    private PrivacyKitService privacyKitService;

    @Autowired
    private HappyChatService happyChatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET /v1/artifacts - List all artifacts for the account
     * 返回所有 artifact 的完整信息（包含 header 和 body）
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getArtifacts(@RequestParam String accountId) {
        try {
            List<ToolArtifact> artifacts = repository.findByAccountIdOrderByUpdatedAtDesc(accountId);

            List<Map<String, Object>> response = artifacts.stream()
                .map(this::toHappyResponseFull)
                .toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取 artifacts 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /v1/artifacts/:id - Get single artifact with full body
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getArtifact(
            @PathVariable String id,
            @RequestParam String accountId) {
        try {
            Optional<ToolArtifact> optional = repository.findByIdAndAccountId(id, accountId);
            if (optional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ToolArtifact artifact = optional.get();
            Map<String, Object> response = toHappyResponseFull(artifact);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取 artifact 失败：id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /v1/artifacts - Create new artifact
     * Body: {
     *   "id": "uuid",
     *   "header": "base64(encrypted header json)",
     *   "body": "base64(encrypted body json)",
     *   "dataEncryptionKey": "base64(encrypted data key)"
     * }
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createArtifact(@RequestBody Map<String, Object> request) {
        try {
            String id = (String) request.get("id");
            String header = (String) request.get("header");
            String body = (String) request.get("body");
            String dataEncryptionKey = (String) request.get("dataEncryptionKey");
            String accountId = (String) request.get("accountId");
            String sessionId = (String) request.get("sessionId");

            if (id == null || header == null || accountId == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required fields: id, header, accountId"));
            }

            // 检查是否已存在
            Optional<ToolArtifact> existing = repository.findById(id);
            if (existing.isPresent()) {
                ToolArtifact artifact = existing.get();
                // 如果属于同一账户，返回现有 artifact（幂等）
                if (artifact.getAccountId().equals(accountId)) {
                    log.info("Artifact 已存在，返回现有：id={}", id);
                    return ResponseEntity.ok(toHappyResponseFull(artifact));
                } else {
                    // 属于其他账户，返回冲突
                    log.warn("Artifact ID 冲突：id={}, accountId={}", id, accountId);
                    return ResponseEntity.status(409)
                        .body(Map.of("error", "Artifact with this ID already exists for another account"));
                }
            }

            // 创建新的 artifact
            // 注意：header/body 已经是加密的 base64，直接存储
            ToolArtifact artifact = new ToolArtifact();
            artifact.setId(id);
            artifact.setSessionId(sessionId != null ? sessionId : "default");
            artifact.setAccountId(accountId);
            artifact.setHeader(header);  // 存储加密的 header
            artifact.setHeaderVersion(1);
            artifact.setBody(body);  // 存储加密的 body（可能为 null）
            artifact.setBodyVersion(body != null ? 1 : 0);
            artifact.setSeq(0);
            artifact.setCreatedAt(Instant.now());
            artifact.setUpdatedAt(Instant.now());

            ToolArtifact saved = repository.save(artifact);
            log.info("创建 artifact: id={}", id);

            // 异步处理 user-message artifact（自动回复）
            happyChatService.processUserMessage(saved);

            return ResponseEntity.ok(toHappyResponseFull(saved));

        } catch (Exception e) {
            log.error("创建 artifact 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /v1/artifacts/:id - Update artifact with version control
     * Body: {
     *   "header": "base64(encrypted header json)",           // optional
     *   "expectedHeaderVersion": 1,                          // optional, required if header present
     *   "body": "base64(encrypted body json)",               // optional
     *   "expectedBodyVersion": 1                             // optional, required if body present
     * }
     */
    @PostMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateArtifact(
            @PathVariable String id,
            @RequestBody Map<String, Object> request,
            @RequestParam(required = false) String accountId) {
        try {
            Optional<ToolArtifact> optional = repository.findById(id);
            if (optional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ToolArtifact current = optional.get();

            // 验证 accountId（如果提供）
            if (accountId != null && !current.getAccountId().equals(accountId)) {
                return ResponseEntity.status(403).build();
            }

            String header = (String) request.get("header");
            Integer expectedHeaderVersion = (Integer) request.get("expectedHeaderVersion");
            String body = (String) request.get("body");
            Integer expectedBodyVersion = (Integer) request.get("expectedBodyVersion");

            // 检查版本冲突
            boolean headerMismatch = header != null && expectedHeaderVersion != null &&
                                     current.getHeaderVersion() != expectedHeaderVersion;
            boolean bodyMismatch = body != null && expectedBodyVersion != null &&
                                   current.getBodyVersion() != expectedBodyVersion;

            if (headerMismatch || bodyMismatch) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "version-mismatch");
                if (headerMismatch) {
                    response.put("currentHeaderVersion", current.getHeaderVersion());
                    response.put("currentHeader", current.getHeader());
                }
                if (bodyMismatch) {
                    response.put("currentBodyVersion", current.getBodyVersion());
                    response.put("currentBody", current.getBody());
                }
                return ResponseEntity.ok(response);
            }

            // 构建更新数据
            Instant now = Instant.now();
            if (header != null) {
                current.setHeader(header);
                current.setHeaderVersion(expectedHeaderVersion + 1);
            }
            if (body != null) {
                current.setBody(body);
                current.setBodyVersion(expectedBodyVersion + 1);
            }
            current.setSeq(current.getSeq() + 1);
            current.setUpdatedAt(now);

            ToolArtifact updated = repository.save(current);
            log.info("更新 artifact: id={}", id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            if (header != null) {
                response.put("headerVersion", updated.getHeaderVersion());
            }
            if (body != null) {
                response.put("bodyVersion", updated.getBodyVersion());
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("更新 artifact 失败：id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * DELETE /v1/artifacts/:id - Delete artifact
     */
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteArtifact(
            @PathVariable String id,
            @RequestParam String accountId) {
        try {
            Optional<ToolArtifact> optional = repository.findByIdAndAccountId(id, accountId);
            if (optional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            repository.delete(optional.get());
            log.info("删除 artifact: id={}", id);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            log.error("删除 artifact 失败：id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 响应转换 ====================

    /**
     * 转换为 Happy 兼容的响应（仅 header）
     */
    private Map<String, Object> toHappyResponseHeaderOnly(ToolArtifact artifact) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", artifact.getId());
        response.put("header", artifact.getHeader());  // 已经是 base64 加密
        response.put("headerVersion", artifact.getHeaderVersion());
        response.put("seq", artifact.getSeq());
        response.put("createdAt", artifact.getCreatedAt().toEpochMilli());
        response.put("updatedAt", artifact.getUpdatedAt().toEpochMilli());
        return response;
    }

    /**
     * 转换为 Happy 兼容的响应（完整）
     */
    private Map<String, Object> toHappyResponseFull(ToolArtifact artifact) {
        Map<String, Object> response = toHappyResponseHeaderOnly(artifact);
        response.put("body", artifact.getBody() != null ? artifact.getBody() : "");
        response.put("bodyVersion", artifact.getBodyVersion());
        return response;
    }
}
