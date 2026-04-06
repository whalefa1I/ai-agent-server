package demo.k8s.agent.tools.remote;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 远程工具路由器 - 根据工具类型决定是否路由到远程服务执行
 */
public class RemoteToolRouter {

    private static final Logger log = LoggerFactory.getLogger(RemoteToolRouter.class);

    /**
     * 需要路由到远程服务的具体工具名称（危险操作）
     */
    private static final Set<String> REMOTE_TOOLS = Set.of(
        "bash", "shell",                              // Shell 执行 - 危险
        "file_write", "file_edit", "file_delete",     // 文件修改
        "file_move", "file_copy", "mkdir"             // 文件系统操作
    );

    /**
     * 只在本地执行的安全工具（只读操作）
     */
    private static final Set<String> LOCAL_ONLY_TOOLS = Set.of(
        "file_read", "glob", "grep",                  // 只读文件操作
        "todo_write", "task_create", "task_list",     // 规划工具
        "task_get", "task_update", "task_stop",       // 任务工具
        "task_output", "exit_plan_mode"               // 其他规划工具
    );

    private final boolean remoteEnabled;
    private final RemoteToolExecutor remoteExecutor;
    private final String remoteBaseUrl;
    private final String remoteAuthToken;

    public RemoteToolRouter(
            boolean remoteEnabled,
            RemoteToolExecutor remoteExecutor,
            String remoteBaseUrl,
            String remoteAuthToken) {
        this.remoteEnabled = remoteEnabled;
        this.remoteExecutor = remoteExecutor;
        this.remoteBaseUrl = remoteBaseUrl;
        this.remoteAuthToken = remoteAuthToken;
    }

    /**
     * 判断工具是否应该路由到远程服务
     */
    public boolean shouldRouteToRemote(ClaudeLikeTool tool) {
        // 远程功能未启用时，全部本地执行
        if (!remoteEnabled || remoteExecutor == null || remoteBaseUrl == null || remoteBaseUrl.isEmpty()) {
            return false;
        }

        String toolName = tool.name();

        // 明确指定只本地的工具（只读操作）
        if (LOCAL_ONLY_TOOLS.contains(toolName)) {
            return false;
        }

        // 明确指定远程的工具（危险操作）
        return REMOTE_TOOLS.contains(toolName);
    }

    /**
     * 同步执行工具（自动路由）
     */
    public LocalToolResult executeSync(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            Function<ClaudeLikeTool, LocalToolResult> localExecutor) {

        if (shouldRouteToRemote(tool)) {
            log.info("Routing dangerous tool {} to remote sandbox", tool.name());
            try {
                return remoteExecutor.executeRemoteSync(tool, input, remoteBaseUrl, remoteAuthToken);
            } catch (Exception e) {
                log.error("Remote execution failed for {}, falling back to local", tool.name(), e);
                // 远程失败时回退到本地执行
                return localExecutor.apply(tool);
            }
        } else {
            log.debug("Executing safe tool {} locally", tool.name());
            return localExecutor.apply(tool);
        }
    }

    /**
     * 获取工具执行位置描述
     */
    public String getExecutionLocation(ClaudeLikeTool tool) {
        if (shouldRouteToRemote(tool)) {
            return "remote:" + remoteBaseUrl;
        }
        return "local";
    }

    /**
     * 简单的测试入口
     */
    public static void main(String[] args) {
        log.info("=== 远程工具路由器测试 ===");

        // 创建路由器（远程未启用）
        RemoteToolRouter router = new RemoteToolRouter(false, null, null, null);

        // 测试工具
        String[] tools = {"bash", "file_read", "file_write", "glob", "grep", "todo_write"};
        ToolCategory[] categories = {
            ToolCategory.SHELL,
            ToolCategory.FILE_SYSTEM,
            ToolCategory.FILE_SYSTEM,
            ToolCategory.FILE_SYSTEM,
            ToolCategory.FILE_SYSTEM,
            ToolCategory.PLANNING
        };

        log.info("\n路由决策测试:");
        for (int i = 0; i < tools.length; i++) {
            ClaudeLikeTool tool = ClaudeToolFactory.buildTool(new ToolDefPartial(
                tools[i], categories[i], "Test", "{}", null, false
            ));
            log.info("  {} ({}): shouldRoute={}", tools[i], categories[i], router.shouldRouteToRemote(tool));
        }

        log.info("\n=== 测试完成 ===");
    }
}
