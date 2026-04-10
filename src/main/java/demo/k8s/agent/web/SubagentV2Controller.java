package demo.k8s.agent.web;

import demo.k8s.agent.subagent.SpawnGatekeeper;
import demo.k8s.agent.subagent.SubagentBatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 子 Agent 产品侧 API v2。
 * <p>
 * 与主会话鉴权一致（基于当前登录用户的会话归属校验）。
 * 提供批量派生、级联取消、批次查询。
 */
@RestController
@RequestMapping("/api/v2/subagent")
@CrossOrigin(origins = "*")
public class SubagentV2Controller {

    private static final Logger log = LoggerFactory.getLogger(SubagentV2Controller.class);

    private final SubagentBatchService batchService;
    private final SpawnGatekeeper gatekeeper;
    private final SubagentSessionAuthHelper sessionAuthHelper;

    public SubagentV2Controller(SubagentBatchService batchService,
                                 SpawnGatekeeper gatekeeper,
                                 SubagentSessionAuthHelper sessionAuthHelper) {
        this.batchService = batchService;
        this.gatekeeper = gatekeeper;
        this.sessionAuthHelper = sessionAuthHelper;
        log.info("[SubagentV2Controller] Initialized");
    }

    @PostMapping("/batch-spawn")
    public ResponseEntity<?> batchSpawn(HttpServletRequest request,
                                         @RequestBody(required = false) BatchSpawnRequest body) {
        BatchSpawnRequest b = body != null ? body : new BatchSpawnRequest();
        log.info("[SubagentV2Controller] batch-spawn request: sessionId={}, taskCount={}, mainRunId={}",
                b.sessionId, b.tasks != null ? b.tasks.size() : 0, b.mainRunId);

        if (b.sessionId == null || b.sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "sessionId is required",
                    "errorCode", "BAD_REQUEST"
            ));
        }

        if (b.tasks == null || b.tasks.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "tasks is required and must not be empty",
                    "errorCode", "BAD_REQUEST"
            ));
        }

        ResponseEntity<?> authResult = sessionAuthHelper.validateSessionOwnership(request, b.sessionId);
        if (authResult != null) {
            return authResult;
        }

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                sessionAuthHelper.getCurrentUserPrincipal(request),
                b.sessionId,
                null
        );

        Set<String> allowedTools = gatekeeper.globalSafeToolNames();

        SubagentBatchService.BatchSpawnResponse response = batchService.spawnBatch(
                ctx,
                b.sessionId,
                b.mainRunId,
                toTaskRequests(b.tasks),
                0,
                allowedTools
        );

        if (response.success()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", response.error(),
                    "errorCode", response.errorCode()
            ));
        }
    }

    @PostMapping("/batch/cancel")
    public ResponseEntity<?> batchCancel(HttpServletRequest request,
                                          @RequestBody(required = false) BatchCancelRequest body) {
        BatchCancelRequest b = body != null ? body : new BatchCancelRequest();
        log.info("[SubagentV2Controller] batch-cancel request: sessionId={}, batchId={}",
                b.sessionId, b.batchId);
        if (b.sessionId == null || b.sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId is required"));
        }
        if (b.batchId == null || b.batchId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "batchId is required"));
        }
        ResponseEntity<?> authResult = sessionAuthHelper.validateSessionOwnership(request, b.sessionId);
        if (authResult != null) {
            return authResult;
        }

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                sessionAuthHelper.getCurrentUserPrincipal(request),
                b.sessionId,
                "user_cancel"
        );

        SubagentBatchService.BatchCancelResponse response = batchService.cancelBatch(
                ctx,
                b.sessionId,
                b.batchId,
                b.reason != null ? b.reason : "user_cancel"
        );

        if (response.success()) {
            return ResponseEntity.ok(response);
        } else if ("BATCH_ALREADY_TERMINAL".equals(response.error())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", response.error(),
                    "batchId", response.batchId(),
                    "status", response.status()
            ));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "error", response.error(),
                    "batchId", response.batchId()
            ));
        }
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<?> batchQuery(HttpServletRequest request,
                                         @PathVariable String batchId,
                                         @RequestParam String sessionId) {
        ResponseEntity<?> authResult = sessionAuthHelper.validateSessionOwnership(request, sessionId);
        if (authResult != null) {
            return authResult;
        }

        SubagentBatchService.InvocationContext ctx = new SubagentBatchService.InvocationContext(
                sessionAuthHelper.getCurrentUserPrincipal(request),
                sessionId,
                null
        );

        SubagentBatchService.BatchQueryResponse response = batchService.queryBatch(ctx, sessionId, batchId);

        if (response.success()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "error", response.error(),
                    "batchId", batchId
            ));
        }
    }

    private List<SubagentBatchService.BatchTaskRequest> toTaskRequests(
            List<BatchTaskRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(r -> new SubagentBatchService.BatchTaskRequest(r.taskName, r.goal, r.agentType))
                .toList();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleBadJson(HttpMessageNotReadableException ex) {
        log.error("[SubagentV2Controller] JSON parse failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Invalid JSON request body",
                "errorCode", "BAD_JSON",
                "message", ex.getMostSpecificCause() != null
                        ? ex.getMostSpecificCause().getMessage()
                        : ex.getMessage()
        ));
    }

    public static class BatchSpawnRequest {
        public String sessionId;
        public String mainRunId;
        public List<BatchTaskRequest> tasks;
    }

    public static class BatchTaskRequest {
        public String taskName;
        public String goal;
        public String agentType = "worker";
    }

    public static class BatchCancelRequest {
        public String sessionId;
        public String batchId;
        public String reason;
    }
}
