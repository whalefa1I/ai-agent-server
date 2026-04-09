package demo.k8s.agent.config;

import demo.k8s.agent.contextobject.ContextObjectReadService;
import demo.k8s.agent.tools.local.planning.SpawnSubagentTool;
import demo.k8s.agent.tools.local.planning.TaskCreateMultiAgentRouter;
import demo.k8s.agent.tools.UnifiedToolExecutor;
import demo.k8s.agent.tools.local.LocalToolExecutor;
import demo.k8s.agent.tools.local.LocalToolRegistry;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.DemoToolSpecs;
import demo.k8s.agent.toolsystem.PermissionManager;
import demo.k8s.agent.toolsystem.McpToolProvider;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import demo.k8s.agent.toolsystem.ToolModule;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import demo.k8s.agent.toolsystem.ToolRegistry;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 简化工具注册配置 - 仅注册本地工具，避免循环依赖。
 * 包含 MCP 工具提供者配置。
 */
@Configuration
public class DemoToolRegistryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DemoToolRegistryConfiguration.class);

    @Autowired
    private demo.k8s.agent.toolstate.ToolStateService toolStateService;

    @Autowired
    private demo.k8s.agent.toolstate.ToolArtifactRepository repository;

    @Autowired
    private demo.k8s.agent.privacykit.PrivacyKitService privacyKitService;

    @PostConstruct
    public void init() {
        // Nothing to initialize for now
    }

    @Bean
    ToolRegistry demoToolRegistry(
            UnifiedToolExecutor unifiedToolExecutor,
            PermissionManager permissionManager,
            ToolPermissionContext toolPermissionContext,
            SpawnSubagentTool spawnSubagentTool) {
        // 注册本地工具（基本工具 + Task 工具集）
        ToolRegistry full = new ToolRegistry();
        full.register(new ToolModule(DemoToolSpecs.glob(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "glob")));
        full.register(new ToolModule(DemoToolSpecs.fileRead(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "file_read")));
        full.register(new ToolModule(DemoToolSpecs.fileWrite(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "file_write")));
        full.register(new ToolModule(DemoToolSpecs.fileEdit(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "file_edit")));
        full.register(new ToolModule(DemoToolSpecs.bash(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "bash")));
        full.register(new ToolModule(DemoToolSpecs.grep(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "grep")));
        full.register(new ToolModule(DemoToolSpecs.readContextObject(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "read_context_object")));
        // Task 工具集（单独注册每个子工具）
        full.register(new ToolModule(createTaskCreateToolSpec(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "TaskCreate")));
        full.register(new ToolModule(createTaskListToolSpec(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "TaskList")));
        full.register(new ToolModule(createTaskGetToolSpec(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "TaskGet")));
        full.register(new ToolModule(createTaskUpdateToolSpec(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "TaskUpdate")));
        full.register(new ToolModule(createTaskStopToolSpec(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "TaskStop")));
        full.register(new ToolModule(createTaskOutputToolSpec(), createToolCallback(unifiedToolExecutor, permissionManager, toolPermissionContext, "TaskOutput")));
        // spawn_subagent 工具（直接派生子 Agent）
        full.register(new ToolModule(
                spawnSubagentTool.createSpawnSubagentToolSpec(),
                (Map<String, Object> input) -> {
                    log.info("[TOOL CALLBACK] spawn_subagent 调用开始：input={}", input);
                    var result = spawnSubagentTool.executeSpawnSubagent(input);
                    log.info("[TOOL CALLBACK] spawn_subagent 执行完成：success={}", result.isSuccess());
                    var resultMap = new java.util.HashMap<String, Object>();
                    resultMap.put("success", result.isSuccess());
                    if (result.getContent() != null) {
                        resultMap.put("content", result.getContent());
                    }
                    if (result.getExecutionLocation() != null) {
                        resultMap.put("location", result.getExecutionLocation());
                    }
                    if (result.getError() != null) {
                        resultMap.put("error", result.getError());
                    }
                    if (result.getMetadata() != null) {
                        resultMap.put("metadata", result.getMetadata());
                    }
                    return resultMap;
                }));

        return full;
    }

    // Task 工具集的 ToolSpec 定义
    private static ClaudeLikeTool createTaskCreateToolSpec() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskCreate", ToolCategory.PLANNING, "创建新任务",
                        "{\"type\":\"object\",\"properties\":{\"subject\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"}},\"required\":[\"subject\",\"description\"]}",
                        null, false));
    }

    private static ClaudeLikeTool createTaskListToolSpec() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskList", ToolCategory.PLANNING, "列出所有任务",
                        "{\"type\":\"object\",\"properties\":{\"filter\":{\"type\":\"string\"}}}",
                        null, true));
    }

    private static ClaudeLikeTool createTaskGetToolSpec() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskGet", ToolCategory.PLANNING, "获取任务详情",
                        "{\"type\":\"object\",\"properties\":{\"taskId\":{\"type\":\"string\"}},\"required\":[\"taskId\"]}",
                        null, true));
    }

    private static ClaudeLikeTool createTaskUpdateToolSpec() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskUpdate", ToolCategory.PLANNING, "更新任务状态",
                        "{\"type\":\"object\",\"properties\":{\"taskId\":{\"type\":\"string\"},\"status\":{\"type\":\"string\"}},\"required\":[\"taskId\",\"status\"]}",
                        null, false));
    }

    private static ClaudeLikeTool createTaskStopToolSpec() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskStop", ToolCategory.PLANNING, "停止任务",
                        "{\"type\":\"object\",\"properties\":{\"taskId\":{\"type\":\"string\"}},\"required\":[\"taskId\"]}",
                        null, false));
    }

    private static ClaudeLikeTool createTaskOutputToolSpec() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial("TaskOutput", ToolCategory.PLANNING, "获取任务输出",
                        "{\"type\":\"object\",\"properties\":{\"taskId\":{\"type\":\"string\"}},\"required\":[\"taskId\"]}",
                        null, true));
    }

    /**
     * MCP Tool 提供者 - 默认空实现
     */
    @Bean
    @Primary
    McpToolProvider mcpToolProvider() {
        return List::of;
    }

    /**
     * 创建工具回调适配器
     */
    private ToolCallback createToolCallback(
            UnifiedToolExecutor executor,
            PermissionManager permissionManager,
            ToolPermissionContext toolPermissionContext,
            String toolName) {
        return FunctionToolCallback.builder(
                toolName,
                (Map<String, Object> input) -> {
                    log.info("[TOOL CALLBACK] 工具调用开始：toolName={}, input={}", toolName, input);
                    var tool = LocalToolRegistry.getToolByName(toolName);
                    if (tool == null) {
                        log.warn("[TOOL CALLBACK] 工具未找到：{}", toolName);
                        return Map.of("success", false, "error", "Tool not found: " + toolName);
                    }
                    // 权限检查（最小对齐）：read-only 工具（如 skill_read）会在 PermissionManager 内自动放行
                    try {
                        var inputNode = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(input);
                        var request = permissionManager.requiresPermission(tool, inputNode, toolPermissionContext);
                        if (request != null) {
                            // 这里不阻塞等待用户确认（ToolCallback 无交互通道），仅返回 pending 信息
                            log.info("[TOOL CALLBACK] 工具 {} 需要用户确认：requestId={}", toolName, request.id());
                            return Map.of(
                                    "success", false,
                                    "error", "permission_required",
                                    "requestId", request.id(),
                                    "toolName", toolName,
                                    "level", request.level().name(),
                                    "message", "Permission required. Approve via /api/permissions/respond then retry."
                            );
                        }
                    } catch (Exception e) {
                        log.warn("[TOOL CALLBACK] 权限检查异常，继续执行：{}", e.getMessage());
                    }
                    // 确保 TraceContext 已设置（从当前线程获取或使用默认值）
                    String sessionId = demo.k8s.agent.observability.tracing.TraceContext.getSessionId();
                    String userId = demo.k8s.agent.observability.tracing.TraceContext.getUserId();
                    log.info("[TOOL CALLBACK] TraceContext - sessionId={}, userId={}", sessionId, userId);
                    if (sessionId == null) {
                        // 如果 TraceContext 为空，生成一个临时 sessionId（用于匿名会话）
                        sessionId = "anon-session-" + System.currentTimeMillis();
                        log.info("[TOOL CALLBACK] TraceContext 为空，生成临时 sessionId: {}", sessionId);
                        demo.k8s.agent.observability.tracing.TraceContext.setSessionId(sessionId);
                    }
                    if (userId == null) {
                        userId = sessionId;
                        demo.k8s.agent.observability.tracing.TraceContext.setUserId(userId);
                    }
                    log.info("[TOOL CALLBACK] 调用 UnifiedToolExecutor.execute: toolName={}", toolName);
                    var result = executor.execute(tool, input, toolPermissionContext);
                    log.info("[TOOL CALLBACK] 工具执行完成：toolName={}, success={}, duration={}ms",
                        toolName, result.isSuccess(), result.getDurationMs());
                    // 使用 HashMap 避免 Map.of() 不支持 null 值的问题
                    var resultMap = new java.util.HashMap<String, Object>();
                    resultMap.put("success", result.isSuccess());
                    if (result.getContent() != null) {
                        resultMap.put("content", result.getContent());
                    }
                    if (result.getExecutionLocation() != null) {
                        resultMap.put("location", result.getExecutionLocation());
                    }
                    resultMap.put("duration", result.getDurationMs());
                    return resultMap;
                })
                .description(toolName)
                .inputType(Map.class)
                .build();
    }

    /**
     * 统一工具执行器（支持本地 + 远程）
     */
    @Bean
    UnifiedToolExecutor unifiedToolExecutor(
            LocalToolExecutor localExecutor,
            demo.k8s.agent.toolstate.ToolStateService toolStateService,
            demo.k8s.agent.toolstate.ToolArtifactRepository repository,
            demo.k8s.agent.privacykit.PrivacyKitService privacyKitService,
            @Autowired(required = false) demo.k8s.agent.tools.remote.PythonRemoteToolExecutor pythonRemoteToolExecutor,
            @Value("${remote.tools.base-url:}") String remoteBaseUrl,
            @Value("${remote.tools.api-key:}") String remoteApiKey) {
        // 强制仅本地调试：忽略远程沙盒配置与 Python 远程执行器。
        if (pythonRemoteToolExecutor != null || (remoteBaseUrl != null && !remoteBaseUrl.isEmpty())) {
            log.warn("Remote sandbox is disabled. All tools execute locally. remoteBaseUrl={}", remoteBaseUrl);
        }

        var builder = UnifiedToolExecutor.builder()
                .mode(UnifiedToolExecutor.ExecutionMode.LOCAL)
                .localExecutor(localExecutor)
                .toolStateService(toolStateService)
                .repository(repository)
                .privacyKitService(privacyKitService);

        return builder.build();
    }

    /**
     * 本地工具执行器
     */
    @Bean
    LocalToolExecutor localToolExecutor(
            ContextObjectReadService contextObjectReadService,
            TaskCreateMultiAgentRouter taskCreateMultiAgentRouter) {
        return new LocalToolExecutor(contextObjectReadService, taskCreateMultiAgentRouter);
    }
}
