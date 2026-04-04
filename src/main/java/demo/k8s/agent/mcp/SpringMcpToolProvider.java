package demo.k8s.agent.mcp;

import demo.k8s.agent.toolsystem.McpToolProvider;
import demo.k8s.agent.toolsystem.ToolModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI MCP 工具提供者实现。
 */
@Service
@ConditionalOnProperty(prefix = "demo.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringMcpToolProvider.class);

    private final McpClientService mcpClientService;

    public SpringMcpToolProvider(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }

    @Override
    public List<ToolModule> loadMcpTools() {
        log.debug("加载 MCP 工具...");

        List<ToolModule> modules = new ArrayList<>();

        // 获取所有已发现的工具回调
        List<ToolCallback> callbacks = mcpClientService.getDiscoveredTools();

        for (ToolCallback callback : callbacks) {
            try {
                // 创建 ClaudeLikeTool 规范
                demo.k8s.agent.toolsystem.ClaudeLikeTool spec =
                        demo.k8s.agent.toolsystem.ClaudeToolFactory.buildTool(
                                new demo.k8s.agent.toolsystem.ToolDefPartial(
                                        callback.getToolDefinition().name(),
                                        demo.k8s.agent.toolsystem.ToolCategory.EXTERNAL,
                                        callback.getToolDefinition().description(),
                                        callback.getToolDefinition().inputSchema(),
                                        null,
                                        false
                                ));

                modules.add(new ToolModule(spec, callback));
            } catch (Exception e) {
                log.warn("处理 MCP 工具失败：{} - {}", callback.getToolDefinition().name(), e.getMessage());
            }
        }

        log.info("加载了 {} 个 MCP 工具", modules.size());
        return modules;
    }
}
