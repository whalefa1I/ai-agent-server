package demo.k8s.agent.config;

import demo.k8s.agent.tools.UnifiedToolExecutor;
import demo.k8s.agent.tools.local.LocalToolExecutor;
import demo.k8s.agent.tools.local.LocalToolRegistry;
import demo.k8s.agent.toolsystem.DemoToolSpecs;
import demo.k8s.agent.toolsystem.ToolModule;
import demo.k8s.agent.toolsystem.ToolRegistry;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 简化工具注册配置 - 仅注册本地工具，避免循环依赖。
 */
@Configuration
public class DemoToolRegistryConfiguration {

    @Bean
    ToolRegistry demoToolRegistry(UnifiedToolExecutor unifiedToolExecutor) {
        // 只注册本地工具（6 个基本工具）
        ToolRegistry full = new ToolRegistry();
        full.register(new ToolModule(DemoToolSpecs.glob(), createToolCallback(unifiedToolExecutor, "glob")));
        full.register(new ToolModule(DemoToolSpecs.fileRead(), createToolCallback(unifiedToolExecutor, "file_read")));
        full.register(new ToolModule(DemoToolSpecs.fileWrite(), createToolCallback(unifiedToolExecutor, "file_write")));
        full.register(new ToolModule(DemoToolSpecs.fileEdit(), createToolCallback(unifiedToolExecutor, "file_edit")));
        full.register(new ToolModule(DemoToolSpecs.bash(), createToolCallback(unifiedToolExecutor, "bash")));
        full.register(new ToolModule(DemoToolSpecs.grep(), createToolCallback(unifiedToolExecutor, "grep")));

        return full;
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
                    return Map.of(
                            "success", result.isSuccess(),
                            "content", result.getContent(),
                            "location", result.getExecutionLocation(),
                            "duration", result.getDurationMs()
                    );
                })
                .description(toolName)
                .inputType(Map.class)
                .build();
    }

    /**
     * 统一工具执行器（默认本地模式）
     */
    @Bean
    UnifiedToolExecutor unifiedToolExecutor(LocalToolExecutor localExecutor) {
        return UnifiedToolExecutor.builder()
                .mode(UnifiedToolExecutor.ExecutionMode.LOCAL)
                .localExecutor(localExecutor)
                .build();
    }

    /**
     * 本地工具执行器
     */
    @Bean
    LocalToolExecutor localToolExecutor() {
        return new LocalToolExecutor();
    }
}
