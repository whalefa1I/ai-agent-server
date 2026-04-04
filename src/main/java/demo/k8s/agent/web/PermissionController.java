package demo.k8s.agent.web;

import demo.k8s.agent.toolsystem.PermissionGrant;
import demo.k8s.agent.toolsystem.PermissionManager;
import demo.k8s.agent.toolsystem.PermissionRequest;
import demo.k8s.agent.toolsystem.PermissionResponse;
import demo.k8s.agent.toolsystem.PermissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 权限管理 HTTP 端点，提供前端权限确认对话框所需 API。
 * <p>
 * 对应 Claude Code 的 PermissionDialog + permissions.ts API。
 */
@RestController
@RequestMapping("/api/permissions")
@CrossOrigin(origins = "*")
public class PermissionController {

    private static final Logger log = LoggerFactory.getLogger(PermissionController.class);

    private final PermissionManager permissionManager;

    // 等待用户确认的 Future 映射：requestId -> CompletableFuture
    private final Map<String, CompletableFuture<PermissionResult>> pendingConfirmations = new java.util.concurrent.ConcurrentHashMap<>();

    // SSE 发射器列表（推送权限请求到前端）
    private final Set<SseEmitter> sseEmitters = ConcurrentHashMap.newKeySet();

    public PermissionController(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    /**
     * 获取待确认的权限请求列表
     */
    @GetMapping("/pending")
    public List<PermissionRequest> getPendingRequests() {
        return permissionManager.getPendingRequests();
    }

    /**
     * 提交用户权限响应
     */
    @PostMapping("/respond")
    public Map<String, Object> respond(@RequestBody PermissionResponse response) {
        log.info("收到权限响应：requestId={}, choice={}", response.requestId(), response.choice());

        PermissionResult result = permissionManager.handlePermissionResponse(response);

        // 通知等待的 Future
        CompletableFuture<PermissionResult> future = pendingConfirmations.remove(response.requestId());
        if (future != null) {
            future.complete(result);
        }

        // 推送更新到所有 SSE 连接
        broadcastPermissionUpdate();

        return Map.of(
                "success", true,
                "result", resultType(result),
                "requestId", response.requestId()
        );
    }

    /**
     * 等待权限确认（阻塞调用）
     * 用于工具执行时同步等待用户确认
     */
    @PostMapping("/wait")
    public PermissionResult waitForConfirmation(@RequestBody Map<String, String> request) throws Exception {
        String requestId = request.get("requestId");
        long timeoutMs = Long.parseLong(request.getOrDefault("timeoutMs", "300000")); // 默认 5 分钟

        CompletableFuture<PermissionResult> future = new CompletableFuture<>();
        pendingConfirmations.put(requestId, future);

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            pendingConfirmations.remove(requestId);
            permissionManager.clearPendingRequest(requestId);
            return PermissionResult.deny("等待用户确认超时");
        }
    }

    /**
     * SSE 推送：实时推送权限请求到前端
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter permissionStream() {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 分钟超时

        sseEmitters.add(emitter);

        // 发送初始数据
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(permissionManager.getPendingRequests()));
        } catch (IOException e) {
            log.warn("发送初始 SSE 数据失败", e);
            emitter.completeWithError(e);
            return emitter;
        }

        // 完成时清理
        emitter.onCompletion(() -> sseEmitters.remove(emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE 连接超时");
            sseEmitters.remove(emitter);
        });
        emitter.onError(e -> {
            log.warn("SSE 连接错误", e);
            sseEmitters.remove(emitter);
        });

        return emitter;
    }

    /**
     * 获取当前会话的所有授权
     */
    @GetMapping("/grants")
    public List<PermissionGrant> getSessionGrants() {
        return permissionManager.getSessionGrants();
    }

    /**
     * 获取始终允许的工具列表
     */
    @GetMapping("/always-allowed")
    public Set<String> getAlwaysAllowedTools() {
        return permissionManager.getAlwaysAllowedTools();
    }

    /**
     * 撤销始终允许的授权
     */
    @PostMapping("/revoke")
    public Map<String, Object> revoke(@RequestBody Map<String, String> request) {
        String toolName = request.get("toolName");
        permissionManager.revokeAlwaysAllowed(toolName);
        return Map.of("success", true, "toolName", toolName);
    }

    /**
     * 清除所有会话授权
     */
    @PostMapping("/clear-session")
    public Map<String, Object> clearSessionGrants() {
        permissionManager.clearSessionGrants();
        return Map.of("success", true);
    }

    // ===== 内部方法 =====

    private void broadcastPermissionUpdate() {
        List<PermissionRequest> pending = permissionManager.getPendingRequests();

        for (SseEmitter emitter : List.copyOf(sseEmitters)) {
            try {
                emitter.send(SseEmitter.event()
                        .name("update")
                        .data(Map.of(
                                "type", "pending_updated",
                                "requests", pending
                        )));
            } catch (IOException e) {
                log.warn("推送 SSE 更新失败", e);
                sseEmitters.remove(emitter);
            }
        }
    }

    private String resultType(PermissionResult result) {
        if (result instanceof PermissionResult.Allow) {
            return "allow";
        } else if (result instanceof PermissionResult.Deny) {
            return "deny";
        } else if (result instanceof PermissionResult.NeedsConfirmation) {
            return "needs_confirmation";
        }
        return "unknown";
    }
}
