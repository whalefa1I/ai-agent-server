package demo.k8s.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 带权限审批的工具执行器包装器。
 * <p>
 * 在工具执行前进行权限检查，支持：
 * - 自动放行（只读工具、已授权工具）
 * - 同步等待用户确认
 * - 超时自动拒绝
 */
public class PermissionCheckingToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckingToolExecutor.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UnifiedToolExecutor delegate;
    private final PermissionManager permissionManager;
    private final ToolPermissionContext permissionContext;

    public PermissionCheckingToolExecutor(
            UnifiedToolExecutor delegate,
            PermissionManager permissionManager,
            ToolPermissionContext permissionContext) {
        this.delegate = delegate;
        this.permissionManager = permissionManager;
        this.permissionContext = permissionContext;
    }

    /**
     * 执行工具（带权限检查）
     * <p>
     * 流程：
     * 1. 检查是否需要权限确认
     * 2. 如果需要，同步等待用户响应
     * 3. 用户批准后执行工具
     * 4. 用户拒绝则返回错误
     */
    public LocalToolResult executeWithPermission(
            ClaudeLikeTool tool,
            Map<String, Object> input) {

        log.debug("Executing tool with permission check: {}", tool.name());

        // 1. 检查是否需要权限确认
        JsonNode inputJson = OBJECT_MAPPER.valueToTree(input);
        PermissionRequest request = permissionManager.requiresPermission(tool, inputJson, permissionContext);

        if (request == null) {
            // 无需确认，直接执行
            log.debug("No permission required for tool {}, executing directly", tool.name());
            return delegate.execute(tool, input, permissionContext);
        }

        // 2. 需要用户确认，同步等待响应
        log.info("Permission required for tool {}: {} (level: {})",
                tool.name(), request.id(), request.level());

        PermissionResult result = waitForPermissionConfirmation(request);

        if (!result.isAllowed()) {
            log.warn("Permission denied for tool {}: {}", tool.name(), result.getDenyReason());
            return LocalToolResult.error("Permission denied: " + result.getDenyReason());
        }

        // 3. 用户已批准，执行工具
        log.info("Permission granted for tool {}, executing...", tool.name());
        return delegate.execute(tool, input, permissionContext);
    }

    /**
     * 异步执行工具（带权限检查）
     */
    public CompletableFuture<LocalToolResult> executeWithPermissionAsync(
            ClaudeLikeTool tool,
            Map<String, Object> input) {

        log.debug("Executing tool with permission check (async): {}", tool.name());

        JsonNode inputJson = OBJECT_MAPPER.valueToTree(input);
        PermissionRequest request = permissionManager.requiresPermission(tool, inputJson, permissionContext);

        if (request == null) {
            // 无需确认，直接执行
            return CompletableFuture.completedFuture(
                    delegate.execute(tool, input, permissionContext));
        }

        // 需要用户确认，异步等待
        return waitForPermissionConfirmationAsync(request)
                .thenCompose(result -> {
                    if (!result.isAllowed()) {
                        log.warn("Permission denied for tool {}: {}", tool.name(), result.getDenyReason());
                        return CompletableFuture.completedFuture(
                                LocalToolResult.error("Permission denied: " + result.getDenyReason()));
                    }
                    return CompletableFuture.completedFuture(
                            delegate.execute(tool, input, permissionContext));
                });
    }

    /**
     * 同步等待权限确认
     */
    private PermissionResult waitForPermissionConfirmation(PermissionRequest request) {
        try {
            // 创建等待 Future
            CompletableFuture<PermissionResult> future = new CompletableFuture<>();

            // 将 future 注册到 PermissionManager（通过 HTTP 端点可访问）
            // 注意：这里需要在 PermissionController 中注册等待的 Future
            // 实际使用时，通过 HTTP 端点 /api/permissions/wait 等待

            // 轮询检查权限状态（每 500ms 检查一次）
            long timeoutMs = 300000; // 5 分钟超时
            long elapsed = 0;
            long checkInterval = 500;

            while (elapsed < timeoutMs) {
                // 检查请求是否已被处理
                var pendingRequests = permissionManager.getPendingRequests();
                boolean stillPending = pendingRequests.stream()
                        .anyMatch(r -> r.id().equals(request.id()));

                if (!stillPending) {
                    // 请求已被处理，检查会话授权
                    var grants = permissionManager.getSessionGrants();
                    boolean granted = grants.stream()
                            .anyMatch(g -> g.toolName().equals(request.toolName()) && g.isValid());

                    if (granted) {
                        return PermissionResult.allow();
                    } else {
                        // 检查是否被拒绝（从始终允许列表中移除）
                        var alwaysAllowed = permissionManager.getAlwaysAllowedTools();
                        if (!alwaysAllowed.contains(request.toolName())) {
                            return PermissionResult.deny("用户拒绝了权限请求");
                        }
                    }
                }

                TimeUnit.MILLISECONDS.sleep(checkInterval);
                elapsed += checkInterval;
            }

            // 超时
            log.warn("Permission confirmation timeout for request {}", request.id());
            permissionManager.clearPendingRequest(request.id());
            return PermissionResult.deny("等待用户确认超时");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Permission confirmation interrupted for request {}", request.id());
            permissionManager.clearPendingRequest(request.id());
            return PermissionResult.deny("等待用户确认被中断");
        }
    }

    /**
     * 异步等待权限确认
     */
    private CompletableFuture<PermissionResult> waitForPermissionConfirmationAsync(
            PermissionRequest request) {

        CompletableFuture<PermissionResult> future = new CompletableFuture<>();

        // 启动异步任务轮询
        CompletableFuture.runAsync(() -> {
            PermissionResult result = waitForPermissionConfirmation(request);
            future.complete(result);
        });

        return future;
    }

    /**
     * 获取待确认的权限请求
     */
    public java.util.List<PermissionRequest> getPendingRequests() {
        return permissionManager.getPendingRequests();
    }

    /**
     * 获取权限上下文
     */
    public ToolPermissionContext getPermissionContext() {
        return permissionContext;
    }
}
