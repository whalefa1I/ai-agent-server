package demo.k8s.agent.config;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import demo.k8s.agent.skills.SkillService;
import demo.k8s.agent.skills.SkillsWatchService;
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
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    @Bean
    @Primary
    @DependsOn("skillService")  // 确保技能在 ChatClient 之前加载
    ChatClient demoChatClient(
            ChatClient.Builder chatClientBuilder,
            ToolRegistry demoToolRegistry,
            ToolPermissionContext toolPermissionContext,
            ToolFeatureFlags toolFeatureFlags,
            McpToolProvider mcpToolProvider,
            SkillService skillService,
            SkillsWatchService skillsWatchService,
            DemoCoordinatorProperties coordinatorProperties) {

        // 启动技能监听服务
        skillsWatchService.startWatching();

        // 加载 MCP 工具并转换为 ToolCallback
        List<org.springframework.ai.tool.ToolCallback> mcpTools = mcpToolProvider.loadMcpTools().stream()
                .map(demo.k8s.agent.toolsystem.ToolModule::callback)
                .toList();

        // 仅注入常规工具，不向模型直接暴露 skill_<name> 动态工具；
        // skills 通过 system prompt catalog + file_read(SKILL.md) 渐进加载（对齐 OpenClaw）。
        List<org.springframework.ai.tool.ToolCallback> allTools = new java.util.ArrayList<>(
                demoToolRegistry.filteredCallbacks(toolPermissionContext, toolFeatureFlags, mcpToolProvider.loadMcpTools()));

        // 构建系统提示词，动态注入技能提示词
        String baseSystem =
                coordinatorProperties.isEnabled()
                        ? AgentPrompts.COORDINATOR_ORCHESTRATOR_ONLY
                        : AgentPrompts.DEMO_COORDINATOR_SYSTEM;

        // 添加技能提示词
        String skillsPrompt = skillService.buildSkillsPrompt();
        String fullSystem = baseSystem + (skillsPrompt.isEmpty() ? "" : "\n\n" + skillsPrompt);

        log.info("初始化 ChatClient，系统提示词长度：{} chars", fullSystem.length());

        return chatClientBuilder
                .defaultSystem(fullSystem)
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
