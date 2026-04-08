package demo.k8s.agent.tools.local;

import demo.k8s.agent.tools.local.file.LocalGlobTool;
import demo.k8s.agent.tools.local.file.LocalFileReadTool;
import demo.k8s.agent.tools.local.file.LocalFileWriteTool;
import demo.k8s.agent.tools.local.file.LocalFileEditTool;
import demo.k8s.agent.tools.local.planning.TaskTools;
import demo.k8s.agent.tools.local.search.LocalGrepTool;
import demo.k8s.agent.tools.local.shell.LocalBashTool;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ToolPermissionContext;

import java.util.Map;

/**
 * 本地工具执行器 - 在本地执行工具，不经过 HTTP。
 * <p>
 * 未来扩展：可通过实现 {@link RemoteToolExecutor} 支持 HTTP 远程调用。
 */
public class LocalToolExecutor {

    /**
     * 执行本地工具
     *
     * @param tool 工具定义
     * @param input 工具输入
     * @param ctx 权限上下文
     * @return 工具执行结果
     */
    public LocalToolResult execute(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            ToolPermissionContext ctx) {

        // 根据工具名称调用相应的执行方法
        String toolName = tool.name();
        return switch (toolName) {
            case "glob" -> LocalGlobTool.execute(input);
            case "file_read" -> LocalFileReadTool.execute(input);
            case "file_write" -> LocalFileWriteTool.execute(input);
            case "file_edit" -> LocalFileEditTool.execute(input);
            case "grep" -> LocalGrepTool.execute(input);
            case "bash" -> LocalBashTool.execute(input);
            // Task 工具集
            case "TaskCreate" -> TaskTools.executeTaskCreate(input);
            case "TaskList" -> TaskTools.executeTaskList(input);
            case "TaskGet" -> TaskTools.executeTaskGet(input);
            case "TaskUpdate" -> TaskTools.executeTaskUpdate(input);
            case "TaskStop" -> TaskTools.executeTaskStop(input);
            case "TaskOutput" -> TaskTools.executeTaskOutput(input);
            default -> LocalToolResult.error("Unknown tool: " + toolName);
        };
    }
}
