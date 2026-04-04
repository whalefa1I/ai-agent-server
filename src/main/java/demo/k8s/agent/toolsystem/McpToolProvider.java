package demo.k8s.agent.toolsystem;

import java.util.List;

/**
 * 动态 MCP 工具来源；与 {@code tools.ts} 中 {@code mcpTools} 参数对齐，默认空实现由 Spring 注入。
 */
@FunctionalInterface
public interface McpToolProvider {

    List<ToolModule> loadMcpTools();
}
