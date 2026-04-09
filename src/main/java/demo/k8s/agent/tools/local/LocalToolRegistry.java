package demo.k8s.agent.tools.local;

import demo.k8s.agent.tools.local.context.ReadContextObjectTool;
import demo.k8s.agent.tools.local.file.*;
import demo.k8s.agent.tools.local.git.LocalGitTool;
import demo.k8s.agent.tools.local.lsp.LspDiagnosticTool;
import demo.k8s.agent.tools.local.memory.MemorySearchTool;
import demo.k8s.agent.tools.local.interaction.AskUserQuestionTool;
import demo.k8s.agent.tools.local.planning.ExitPlanModeTool;
import demo.k8s.agent.tools.local.planning.SpawnSubagentTool;
import demo.k8s.agent.tools.local.planning.TaskTools;
import demo.k8s.agent.tools.local.search.LocalGrepTool;
import demo.k8s.agent.tools.local.shell.LocalBashTool;
import demo.k8s.agent.tools.local.web.WebFetchTool;
import demo.k8s.agent.tools.local.web.WebSearchTool;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地工具注册表 - 注册所有本地实现的工具。
 * <p>
 * 用法：
 * <pre>{@code
 * List<ClaudeLikeTool> tools = LocalToolRegistry.getAllTools();
 * }</pre>
 */
public class LocalToolRegistry {

    /**
     * 获取所有本地工具
     */
    public static List<ClaudeLikeTool> getAllTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();

        // 文件操作工具
        tools.add(LocalGlobTool.createTool());
        tools.add(LocalFileReadTool.createTool());
        tools.add(LocalFileWriteTool.createTool());
        tools.add(LocalFileEditTool.createTool());
        tools.add(LocalFileDeleteTool.createTool());
        tools.add(LocalMkdirTool.createTool());
        tools.add(LocalLSTool.createTool());
        tools.add(LocalMultiEditTool.createTool());
        tools.add(LocalFileStatTool.createTool());
        tools.add(LocalFileCopyTool.createTool());
        tools.add(LocalFileMoveTool.createTool());

        // Git 工具
        tools.add(LocalGitTool.createTool());

        // 搜索工具
        tools.add(LocalGrepTool.createTool());

        tools.add(ReadContextObjectTool.createTool());

        // Shell 工具
        tools.add(LocalBashTool.createTool());

        // 规划工具
        tools.add(ExitPlanModeTool.createTool());
        // Task 工具集（推荐使用）
        tools.add(TaskTools.createTaskCreateTool());
        tools.add(TaskTools.createTaskListTool());
        tools.add(TaskTools.createTaskGetTool());
        tools.add(TaskTools.createTaskUpdateTool());
        tools.add(TaskTools.createTaskStopTool());
        tools.add(TaskTools.createTaskOutputTool());
        // spawn_subagent 工具（直接派生子 Agent）
        tools.add(SpawnSubagentTool.createTool());

        // LSP 工具
        tools.add(LspDiagnosticTool.createTool());

        // Web 工具
        tools.add(WebSearchTool.createTool());
        tools.add(WebFetchTool.createTool());

        // 记忆工具
        tools.add(MemorySearchTool.createTool());

        // 交互工具
        tools.add(AskUserQuestionTool.createTool());

        return tools;
    }

    /**
     * 按名称获取工具
     */
    public static ClaudeLikeTool getToolByName(String name) {
        return getAllTools().stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取文件操作相关工具
     */
    public static List<ClaudeLikeTool> getFileTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        tools.add(LocalGlobTool.createTool());
        tools.add(LocalFileReadTool.createTool());
        tools.add(LocalFileWriteTool.createTool());
        tools.add(LocalFileEditTool.createTool());
        return tools;
    }

    /**
     * 获取搜索相关工具
     */
    public static List<ClaudeLikeTool> getSearchTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        tools.add(LocalGrepTool.createTool());
        return tools;
    }

    /**
     * 获取 Shell 相关工具
     */
    public static List<ClaudeLikeTool> getShellTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        tools.add(LocalBashTool.createTool());
        return tools;
    }

    /**
     * 获取版本控制相关工具
     */
    public static List<ClaudeLikeTool> getVersionControlTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        tools.add(LocalGitTool.createTool());
        return tools;
    }

    /**
     * 获取 LSP 相关工具
     */
    public static List<ClaudeLikeTool> getLspTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        tools.add(LspDiagnosticTool.createTool());
        return tools;
    }

    /**
     * 获取 Web 相关工具
     */
    public static List<ClaudeLikeTool> getWebTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        tools.add(WebSearchTool.createTool());
        tools.add(WebFetchTool.createTool());
        return tools;
    }

    /**
     * 获取记忆相关工具
     */
    public static List<ClaudeLikeTool> getMemoryTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        tools.add(MemorySearchTool.createTool());
        return tools;
    }
}
