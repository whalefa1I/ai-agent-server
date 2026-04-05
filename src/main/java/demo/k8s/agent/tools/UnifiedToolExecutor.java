package demo.k8s.agent.tools;

import demo.k8s.agent.tools.local.LocalToolExecutor;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.tools.remote.RemoteToolExecutor;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import demo.k8s.agent.toolstate.ToolArtifactRepository;
import demo.k8s.agent.toolstate.ToolStateService;
import demo.k8s.agent.toolstate.ToolStatus;
import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.privacykit.PrivacyKitService;
import demo.k8s.agent.toolstate.ToolArtifact;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 统一工具执行器 - 支持本地执行和未来远程执行。
 * <p>
 * 执行模式：
 * - LOCAL: 在本地直接执行工具
 * - REMOTE: 通过 HTTP 调用远程服务
 * - AUTO: 根据工具配置自动选择
 */
public class UnifiedToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(UnifiedToolExecutor.class);

    private final ExecutionMode mode;
    private final LocalToolExecutor localExecutor;
    private final RemoteToolExecutor remoteExecutor;
    private final String remoteBaseUrl;
    private final String remoteAuthToken;
    private final ToolStateService toolStateService;
    private final ToolArtifactRepository repository;
    private final PrivacyKitService privacyKitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 工具图标映射
    private static final Map<String, String> TOOL_ICONS = Map.of(
        "file_read", "📄", "file_write", "✏️", "file_edit", "📝",
        "bash", "💻", "glob", "🔍", "grep", "🔎",
        "Task", "🤖", "k8s_sandbox_run", "☸️"
    );

    // 工具显示名称映射
    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.of(
        "file_read", "读取文件", "file_write", "写入文件", "file_edit", "编辑文件",
        "bash", "执行命令", "glob", "文件搜索", "grep", "文本搜索",
        "Task", "委派任务", "k8s_sandbox_run", "K8s 沙盒"
    );

    public enum ExecutionMode {
        /** 仅在本地执行 */
        LOCAL,
        /** 仅在远程执行 */
        REMOTE,
        /** 自动选择：本地优先，失败时尝试远程 */
        AUTO
    }

    private UnifiedToolExecutor(Builder builder) {
        this.mode = builder.mode;
        this.localExecutor = builder.localExecutor;
        this.remoteExecutor = builder.remoteExecutor;
        this.remoteBaseUrl = builder.remoteBaseUrl;
        this.remoteAuthToken = builder.remoteAuthToken;
        this.toolStateService = builder.toolStateService;
        this.repository = builder.repository;
        this.privacyKitService = builder.privacyKitService;
    }

    /**
     * 同步执行工具
     */
    public LocalToolResult execute(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            ToolPermissionContext ctx) {

        switch (mode) {
            case LOCAL:
                return executeLocal(tool, input, ctx);

            case REMOTE:
                return executeRemoteSync(tool, input);

            case AUTO:
                return executeAuto(tool, input, ctx);

            default:
                return LocalToolResult.error("Unknown execution mode: " + mode);
        }
    }

    /**
     * 异步执行工具
     */
    public CompletableFuture<LocalToolResult> executeAsync(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            ToolPermissionContext ctx) {

        switch (mode) {
            case LOCAL:
                return CompletableFuture.completedFuture(executeLocal(tool, input, ctx));

            case REMOTE:
                return executeRemoteAsync(tool, input);

            case AUTO:
                return executeAutoAsync(tool, input, ctx);

            default:
                return CompletableFuture.completedFuture(
                        LocalToolResult.error("Unknown execution mode: " + mode));
        }
    }

    /**
     * 本地执行
     */
    private LocalToolResult executeLocal(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            ToolPermissionContext ctx) {

        // 在工具执行之前创建 artifact（如果 TraceContext 中有会话信息）
        String sessionId = TraceContext.getSessionId();
        String userId = TraceContext.getUserId();
        if (sessionId != null && userId != null && toolStateService != null) {
            log.info("工具调用：{} (sessionId={}, userId={})", tool.name(), sessionId, userId);
            try {
                // 创建 tool-call artifact，使用与 HappyChatService 相同的 header 格式
                String icon = getToolIcon(tool.name());
                String displayName = getToolDisplayName(tool.name());

                Map<String, Object> body = new HashMap<>();
                body.put("status", "started");
                body.put("timestamp", System.currentTimeMillis());
                if (input != null) {
                    body.put("input", input);
                }

                var artifact = toolStateService.createToolArtifact(
                    sessionId, userId, tool.name(), "tool-call",
                    ToolStatus.TODO, body, null);

                // 更新 header 为 Happy 兼容格式
                Map<String, Object> header = new HashMap<>();
                header.put("type", "tool-call");
                header.put("subtype", tool.name());
                header.put("toolName", tool.name());
                header.put("toolDisplayName", displayName);
                header.put("icon", icon);
                header.put("status", "started");
                header.put("inputSummary", input != null ? input.toString() : "");
                header.put("timestamp", System.currentTimeMillis());

                // 使用 base64 编码 header（与 Happy 兼容）
                String headerJson = objectMapper.writeValueAsString(header);
                String encryptedHeader = privacyKitService.encodeBase64(headerJson.getBytes());
                artifact.setHeader(encryptedHeader);
                artifact = repository.save(artifact);

                log.info("创建 tool-call artifact: id={}, tool={}", artifact.getId(), tool.name());
            } catch (Exception e) {
                log.warn("创建 tool-call artifact 失败：{}", e.getMessage());
            }
        }

        log.debug("Executing tool locally: {}", tool.name());
        return localExecutor.execute(tool, input, ctx);
    }

    /**
     * 远程执行（同步）
     */
    private LocalToolResult executeRemoteSync(
            ClaudeLikeTool tool,
            Map<String, Object> input) {
        log.debug("Executing tool remotely: {}", tool.name());
        return remoteExecutor.executeRemoteSync(tool, input, remoteBaseUrl, remoteAuthToken);
    }

    /**
     * 远程执行（异步）
     */
    private CompletableFuture<LocalToolResult> executeRemoteAsync(
            ClaudeLikeTool tool,
            Map<String, Object> input) {
        log.debug("Executing tool remotely (async): {}", tool.name());
        return remoteExecutor.executeRemote(tool, input, remoteBaseUrl, remoteAuthToken);
    }

    /**
     * 自动模式执行（本地优先）
     */
    private LocalToolResult executeAuto(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            ToolPermissionContext ctx) {
        // 首先尝试本地执行
        LocalToolResult localResult = executeLocal(tool, input, ctx);

        if (localResult.isSuccess()) {
            return localResult;
        }

        // 本地失败且有远程配置时，尝试远程
        if (remoteBaseUrl != null && !remoteBaseUrl.isEmpty()) {
            log.warn("Local execution failed for {}, trying remote: {}",
                    tool.name(), localResult.getError());
            return executeRemoteSync(tool, input);
        }

        return localResult;
    }

    /**
     * 自动模式执行（异步）
     */
    private CompletableFuture<LocalToolResult> executeAutoAsync(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            ToolPermissionContext ctx) {

        LocalToolResult localResult = executeLocal(tool, input, ctx);

        if (localResult.isSuccess()) {
            return CompletableFuture.completedFuture(localResult);
        }

        if (remoteBaseUrl != null && !remoteBaseUrl.isEmpty()) {
            log.warn("Local execution failed for {}, trying remote: {}",
                    tool.name(), localResult.getError());
            return executeRemoteAsync(tool, input);
        }

        return CompletableFuture.completedFuture(localResult);
    }

    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    private String getToolIcon(String toolName) {
        return TOOL_ICONS.getOrDefault(toolName, "🔧");
    }

    private String getToolDisplayName(String toolName) {
        return TOOL_DISPLAY_NAMES.getOrDefault(toolName, toolName);
    }

    public static class Builder {
        private ExecutionMode mode = ExecutionMode.LOCAL;
        private LocalToolExecutor localExecutor = new LocalToolExecutor();
        private RemoteToolExecutor remoteExecutor;
        private String remoteBaseUrl;
        private String remoteAuthToken;
        private ToolStateService toolStateService;
        private ToolArtifactRepository repository;
        private PrivacyKitService privacyKitService;

        public Builder mode(ExecutionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder localExecutor(LocalToolExecutor executor) {
            this.localExecutor = executor;
            return this;
        }

        public Builder remoteExecutor(RemoteToolExecutor executor) {
            this.remoteExecutor = executor;
            return this;
        }

        public Builder remoteConfig(String baseUrl, String authToken) {
            this.remoteBaseUrl = baseUrl;
            this.remoteAuthToken = authToken;
            return this;
        }

        public Builder toolStateService(ToolStateService service) {
            this.toolStateService = service;
            return this;
        }

        public Builder repository(ToolArtifactRepository repo) {
            this.repository = repo;
            return this;
        }

        public Builder privacyKitService(PrivacyKitService service) {
            this.privacyKitService = service;
            return this;
        }

        public UnifiedToolExecutor build() {
            // 如果是 REMOTE 模式，必须有 remoteExecutor
            if (mode == ExecutionMode.REMOTE && remoteExecutor == null) {
                this.remoteExecutor = new demo.k8s.agent.tools.remote.HttpRemoteToolExecutor();
            }
            return new UnifiedToolExecutor(this);
        }
    }
}
