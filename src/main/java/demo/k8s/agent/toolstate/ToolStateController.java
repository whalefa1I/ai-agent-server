package demo.k8s.agent.toolstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ToolStateController - 工具状态 HTTP 控制器
 *
 * 提供 REST API 用于：
 * - 创建/读取/更新/删除工具状态
 * - 配合 WebSocket 实现实时状态推送
 */
@RestController
@RequestMapping("/api/v2/tool-state")
@CrossOrigin(origins = "*")
public class ToolStateController {

    private static final Logger log = LoggerFactory.getLogger(ToolStateController.class);

    @Autowired
    private ToolStateService toolStateService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 创建工具状态 ====================

    /**
     * 创建新的工具状态
     *
     * POST /api/v2/tool-state
     * Body: {
     *   "sessionId": "xxx",
     *   "toolName": "BashTool",
     *   "toolType": "tool",
     *   "initialStatus": "todo",
     *   "body": { "todo": "Execute command" }
     * }
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createToolArtifact(@RequestBody Map<String, Object> request) {
        try {
            String sessionId = (String) request.get("sessionId");
            String accountId = (String) request.get("accountId");
            String toolName = (String) request.get("toolName");
            String toolType = (String) request.get("toolType");
            String initialStatus = (String) request.getOrDefault("initialStatus", "todo");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) request.get("body");

            if (sessionId == null || accountId == null || toolName == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Missing required fields"));
            }

            ToolArtifact artifact = toolStateService.createToolArtifact(
                sessionId, accountId, toolName, toolType,
                ToolStatus.valueOf(initialStatus.toUpperCase()), body, null
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("artifact", toSafeResponse(artifact));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("创建工具状态失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 获取工具状态 ====================

    /**
     * 获取工具状态详情
     *
     * GET /api/v2/tool-state/{artifactId}
     */
    @GetMapping(value = "/{artifactId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getToolArtifact(@PathVariable String artifactId,
                                                                @RequestParam String accountId) {
        try {
            Optional<ToolArtifact> artifact = toolStateService.getToolArtifact(artifactId, accountId);
            if (artifact.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("artifact", toSafeResponse(artifact.get()));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取工具状态失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 获取会话的所有工具状态
     *
     * GET /api/v2/tool-state/session/{sessionId}
     */
    @GetMapping(value = "/session/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getSessionToolArtifacts(@PathVariable String sessionId) {
        try {
            List<ToolArtifact> artifacts = toolStateService.getSessionToolArtifacts(sessionId);
            List<Map<String, Object>> responseList = artifacts.stream()
                .map(this::toSafeResponse)
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("artifacts", responseList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取会话工具状态失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 更新工具状态 ====================

    /**
     * 更新工具状态
     *
     * PUT /api/v2/tool-state/{artifactId}
     * Body: {
     *   "accountId": "xxx",
     *   "status": "executing",
     *   "body": { "input": {...}, "progress": "running" },
     *   "expectedVersion": 1
     * }
     */
    @PutMapping(value = "/{artifactId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateToolArtifact(@PathVariable String artifactId,
                                                                   @RequestBody Map<String, Object> request) {
        try {
            String accountId = (String) request.get("accountId");
            String status = (String) request.get("status");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) request.get("body");
            Integer expectedVersion = (Integer) request.get("expectedVersion");

            if (accountId == null || status == null || expectedVersion == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Missing required fields"));
            }

            ToolStateService.UpdateResult result = toolStateService.updateToolArtifact(
                artifactId, accountId, ToolStatus.valueOf(status.toUpperCase()), body,
                expectedVersion, null
            );

            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("artifact", toSafeResponse(result.getArtifact().get()));
                return ResponseEntity.ok(response);
            } else if (result.isVersionMismatch()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("reason", "version-mismatch");
                response.put("currentVersion", result.getCurrentVersion().get());
                return ResponseEntity.status(409).body(response);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("更新工具状态失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 删除工具状态 ====================

    /**
     * 删除工具状态
     *
     * DELETE /api/v2/tool-state/{artifactId}
     */
    @DeleteMapping(value = "/{artifactId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteToolArtifact(@PathVariable String artifactId,
                                                                   @RequestParam String accountId) {
        try {
            boolean deleted = toolStateService.deleteToolArtifact(artifactId, accountId, null);
            if (deleted) {
                return ResponseEntity.ok(Map.of("success", true));
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("删除工具状态失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> toSafeResponse(ToolArtifact artifact) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", artifact.getId());
        response.put("sessionId", artifact.getSessionId());
        response.put("accountId", artifact.getAccountId());
        response.put("header", parseJsonSafe(artifact.getHeader()));
        response.put("headerVersion", artifact.getHeaderVersion());
        response.put("body", parseJsonSafe(artifact.getBody()));
        response.put("bodyVersion", artifact.getBodyVersion());
        response.put("seq", artifact.getSeq());
        response.put("createdAt", artifact.getCreatedAt().toEpochMilli());
        response.put("updatedAt", artifact.getUpdatedAt().toEpochMilli());
        return response;
    }

    private Object parseJsonSafe(String json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }
}
