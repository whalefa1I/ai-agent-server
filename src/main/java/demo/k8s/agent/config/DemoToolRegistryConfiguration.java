package demo.k8s.agent.config;

import demo.k8s.agent.tools.UnifiedToolExecutor;
import demo.k8s.agent.tools.local.LocalToolExecutor;
import demo.k8s.agent.tools.local.LocalToolRegistry;
import demo.k8s.agent.tools.local.planning.TodoArtifactHelper;
import demo.k8s.agent.toolsystem.DemoToolSpecs;
import demo.k8s.agent.toolsystem.McpToolProvider;
import demo.k8s.agent.toolsystem.ToolModule;
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
        // 初始化 TodoArtifactHelper 的依赖服务
        TodoArtifactHelper.setServices(toolStateService, repository, privacyKitService);
        log.info("TodoArtifactHelper 已初始化");
    }

    @Bean
    ToolRegistry demoToolRegistry(UnifiedToolExecutor unifiedToolExecutor) {
        // 只注册本地工具（7 个基本工具 + todo_write）
        ToolRegistry full = new ToolRegistry();
        full.register(new ToolModule(DemoToolSpecs.glob(), createToolCallback(unifiedToolExecutor, "glob")));
        full.register(new ToolModule(DemoToolSpecs.fileRead(), createToolCallback(unifiedToolExecutor, "file_read")));
        full.register(new ToolModule(DemoToolSpecs.fileWrite(), createToolCallback(unifiedToolExecutor, "file_write")));
        full.register(new ToolModule(DemoToolSpecs.fileEdit(), createToolCallback(unifiedToolExecutor, "file_edit")));
        full.register(new ToolModule(DemoToolSpecs.bash(), createToolCallback(unifiedToolExecutor, "bash")));
        full.register(new ToolModule(DemoToolSpecs.grep(), createToolCallback(unifiedToolExecutor, "grep")));
        full.register(new ToolModule(DemoToolSpecs.todoWrite(), createToolCallback(unifiedToolExecutor, "todo_write")));

        return full;
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
    private ToolCallback createToolCallback(UnifiedToolExecutor executor, String toolName) {
        return FunctionToolCallback.builder(
                toolName,
                (Map<String, Object> input) -> {
                    var tool = LocalToolRegistry.getToolByName(toolName);
                    if (tool == null) {
                        return Map.of("success", false, "error", "Tool not found: " + toolName);
                    }
                    var result = executor.execute(tool, input, null);
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

        // 创建远程工具路由器
        demo.k8s.agent.tools.remote.RemoteToolRouter router = null;
        if (pythonRemoteToolExecutor != null && remoteBaseUrl != null && !remoteBaseUrl.isEmpty()) {
            router = new demo.k8s.agent.tools.remote.RemoteToolRouter(
                true, pythonRemoteToolExecutor, remoteBaseUrl, remoteApiKey);
        }

        var builder = UnifiedToolExecutor.builder()
                .mode(UnifiedToolExecutor.ExecutionMode.AUTO)
                .localExecutor(localExecutor)
                .toolStateService(toolStateService)
                .repository(repository)
                .privacyKitService(privacyKitService)
                .remoteRouter(router);

        return builder.build();
    }

    /**
     * 本地工具执行器
     */
    @Bean
    LocalToolExecutor localToolExecutor() {
        return new LocalToolExecutor();
    }
}
