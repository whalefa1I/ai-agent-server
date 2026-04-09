package demo.k8s.agent.toolsystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;

/**
 * 对应 Claude Code 中在 {@code tools.ts} 组装、再按 {@link ToolPermissionContext} 与特性标志过滤的过程。
 * 完整流水线见 {@link ToolAssembly#assembleFilteredToolPool}。
 */
public final class ToolRegistry {

    private final List<ToolModule> modules = new ArrayList<>();

    public ToolRegistry register(ToolModule module) {
        modules.add(module);
        return this;
    }

    public List<ToolModule> modules() {
        return List.copyOf(modules);
    }

    /**
     * 与 {@code getTools(permissionContext)} + {@code assembleToolPool} 等价：先特性门控 / 简单模式 / deny，再与 MCP 合并去重，
     * 最后按类别与只读模式裁剪。
     */
    public List<ToolCallback> filteredCallbacks(
            ToolPermissionContext ctx, ToolFeatureFlags flags, List<ToolModule> mcpTools) {
        List<ToolModule> merged = ToolAssembly.assembleFilteredToolPool(modules, ctx, flags, mcpTools);
        return callbacksAfterCategoryFilter(merged, ctx);
    }

    /**
     * 按 {@link ClaudeLikeTool#isEnabled()}、类别白名单与只读模式过滤，返回可供
     * {@link org.springframework.ai.chat.client.ChatClient.Builder#defaultToolCallbacks} 使用的回调。
     */
    private static List<ToolCallback> callbacksAfterCategoryFilter(
            List<ToolModule> merged, ToolPermissionContext ctx) {
        List<ToolCallback> out = new ArrayList<>();
        for (ToolModule m : merged) {
            ClaudeLikeTool spec = m.spec();
            if (!spec.isEnabled()) {
                continue;
            }
            if (!ctx.isCategoryAllowed(spec.category())) {
                continue;
            }
            if (ctx.mode() == ToolPermissionMode.READ_ONLY) {
                if (!spec.defaultReadOnlyHint() && !isReadOnlyCategory(spec)) {
                    continue;
                }
            }
            out.add(m.callback());
        }
        return out;
    }

    /** 规划类 Todo 等在只读模式下可放行（与业务策略相关，可按需调整） */
    private static boolean isReadOnlyCategory(ClaudeLikeTool spec) {
        return spec.category() == ToolCategory.PLANNING;
    }

    /**
     * 获取所有已注册工具的 name 集合（用于 Gatekeeper 白名单检查）。
     * <p>
     * 注意：此方法返回所有工具，不进行权限过滤；实际权限检查由 {@link #filteredCallbacks} 负责。
     */
    public Set<String> getAllToolNames() {
        return modules.stream()
                .map(ToolModule::spec)
                .map(ClaudeLikeTool::name)
                .collect(java.util.stream.Collectors.toSet());
    }
}
