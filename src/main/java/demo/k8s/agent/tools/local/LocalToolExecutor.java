package demo.k8s.agent.tools.local;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.contextobject.ContextObjectReadService;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.MultiAgentFacade;
import demo.k8s.agent.subagent.SpawnGatekeeper;
import demo.k8s.agent.tools.local.context.ReadContextObjectTool;
import demo.k8s.agent.tools.local.file.LocalGlobTool;
import demo.k8s.agent.tools.local.file.LocalFileReadTool;
import demo.k8s.agent.tools.local.file.LocalFileWriteTool;
import demo.k8s.agent.tools.local.file.LocalFileEditTool;
import demo.k8s.agent.tools.local.planning.SpawnSubagentTool;
import demo.k8s.agent.tools.local.planning.QuerySubagentResultTool;
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

    private final ContextObjectReadService contextObjectReadService;
    private final SpawnSubagentTool spawnSubagentTool;
    private final QuerySubagentResultTool querySubagentResultTool;
    private final DemoMultiAgentProperties multiAgentProperties;
    private final MultiAgentFacade multiAgentFacade;
    private final SpawnGatekeeper spawnGatekeeper;

    public LocalToolExecutor() {
        this(null, null, null, null, null, null);
    }

    public LocalToolExecutor(ContextObjectReadService contextObjectReadService) {
        this(contextObjectReadService, null, null, null, null, null);
    }

    public LocalToolExecutor(ContextObjectReadService contextObjectReadService,
                             SpawnSubagentTool spawnSubagentTool) {
        this(contextObjectReadService, spawnSubagentTool, null, null, null, null);
    }

    public LocalToolExecutor(
            ContextObjectReadService contextObjectReadService,
            SpawnSubagentTool spawnSubagentTool,
            QuerySubagentResultTool querySubagentResultTool,
            DemoMultiAgentProperties multiAgentProperties,
            MultiAgentFacade multiAgentFacade,
            SpawnGatekeeper spawnGatekeeper) {
        this.contextObjectReadService = contextObjectReadService;
        this.spawnSubagentTool = spawnSubagentTool;
        this.querySubagentResultTool = querySubagentResultTool;
        this.multiAgentProperties = multiAgentProperties;
        this.multiAgentFacade = multiAgentFacade;
        this.spawnGatekeeper = spawnGatekeeper;
    }

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
            case "read_context_object" -> ReadContextObjectTool.execute(input, contextObjectReadService);
            // Task 工具集
            case "TaskCreate" -> executeTaskCreateRouted(input);
            case "TaskList" -> TaskTools.executeTaskList(input);
            case "TaskGet" -> TaskTools.executeTaskGet(input);
            case "TaskUpdate" -> TaskTools.executeTaskUpdate(input);
            case "TaskStop" -> TaskTools.executeTaskStop(input);
            case "TaskOutput" -> TaskTools.executeTaskOutput(input);
            // spawn_subagent 工具
            case "spawn_subagent" -> spawnSubagentTool != null
                    ? spawnSubagentTool.executeSpawnSubagent(input)
                    : LocalToolResult.error("spawn_subagent tool not initialized");
            // query_subagent_result 工具
            case "query_subagent_result" -> querySubagentResultTool != null
                    ? querySubagentResultTool.executeQuery(input)
                    : LocalToolResult.error("query_subagent_result tool not initialized");
            default -> LocalToolResult.error("Unknown tool: " + toolName);
        };
    }

    /**
     * multi-agent {@code mode=on} 时 TaskCreate 经 {@link MultiAgentFacade} 派生子运行，并在成功文案中带 {@code runId=}；
     * 否则保持纯内存任务行。
     */
    private LocalToolResult executeTaskCreateRouted(Map<String, Object> input) {
        if (multiAgentProperties != null
                && multiAgentProperties.isEnabled()
                && multiAgentProperties.getMode() == DemoMultiAgentProperties.Mode.on
                && multiAgentFacade != null
                && spawnGatekeeper != null) {
            int depth =
                    spawnSubagentTool != null
                            ? spawnSubagentTool.resolveCallerSubagentDepth(TraceContext.getSessionId())
                            : 0;
            return TaskTools.executeTaskCreate(input, multiAgentFacade, spawnGatekeeper, depth);
        }
        return TaskTools.executeTaskCreate(input);
    }
}
