package demo.k8s.agent.config;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import demo.k8s.agent.skills.SkillRegistry;
import demo.k8s.agent.skills.SkillExecutorRegistry;
import demo.k8s.agent.toolsystem.McpToolProvider;
import demo.k8s.agent.toolsystem.ToolFeatureFlags;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import demo.k8s.agent.toolsystem.ToolRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    @Bean
    @Primary
    ChatClient demoChatClient(
            ChatClient.Builder chatClientBuilder,
            ToolRegistry demoToolRegistry,
            ToolPermissionContext toolPermissionContext,
            ToolFeatureFlags toolFeatureFlags,
            McpToolProvider mcpToolProvider,
            SkillRegistry skillRegistry,
            SkillExecutorRegistry skillExecutorRegistry,
            DemoCoordinatorProperties coordinatorProperties) {

        // 加载 MCP 工具并转换为 ToolCallback
        List<org.springframework.ai.tool.ToolCallback> mcpTools = mcpToolProvider.loadMcpTools().stream()
                .map(demo.k8s.agent.toolsystem.ToolModule::callback)
                .toList();

        // 加载 Skill 工具（带执行函数）
        List<org.springframework.ai.tool.ToolCallback> skillTools = new java.util.ArrayList<>();
        if (skillRegistry != null) {
            skillTools = skillRegistry.getSkillTools().stream()
                    .map(tool -> {
                        try {
                            // 获取工具执行器（如果已注册）
                            java.util.function.Function<String, String> executor =
                                demo.k8s.agent.skills.SkillService.getToolExecutor(tool.name());
                            return demo.k8s.agent.toolsystem.ClaudeToolFactory.toToolCallback(tool, executor);
                        } catch (Exception e) {
                            log.error("转换技能工具失败：{}", tool.name(), e);
                            return null;
                        }
                    })
                    .filter(t -> t != null)
                    .toList();
        }

        // 合并所有工具
        List<org.springframework.ai.tool.ToolCallback> allTools = new java.util.ArrayList<>(
                demoToolRegistry.filteredCallbacks(toolPermissionContext, toolFeatureFlags, mcpToolProvider.loadMcpTools()));
        allTools.addAll(skillTools);

        String system =
                coordinatorProperties.isEnabled()
                        ? AgentPrompts.COORDINATOR_ORCHESTRATOR_ONLY
                        : AgentPrompts.DEMO_COORDINATOR_SYSTEM;
        return chatClientBuilder
                .defaultSystem(system)
                .defaultToolCallbacks(allTools.toArray(org.springframework.ai.tool.ToolCallback[]::new))
                .defaultAdvisors(
                        ToolCallAdvisor.builder().conversationHistoryEnabled(false).build())
                .build();
    }

    /**
     * Worker Agent 异步执行器
     */
    @Bean(name = "workerExecutor")
    public Executor workerExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Jackson ObjectMapper for JSON serialization
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
