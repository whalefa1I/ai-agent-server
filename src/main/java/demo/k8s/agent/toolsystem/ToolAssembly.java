package demo.k8s.agent.toolsystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 对齐 Claude Code {@code src/tools.ts} 中的装配逻辑：
 * <ul>
 *   <li>{@link #filterToolsByDenyRules}</li>
 *   <li>简单模式（{@code getTools} 中 {@code CLAUDE_CODE_SIMPLE} 分支）</li>
 *   <li>{@link #assembleToolPool} — 内置 + MCP，按名去重，内置优先</li>
 *   <li>{@link #getMergedTools} — 内置 + MCP 直接拼接（不去重）</li>
 * </ul>
 */
public final class ToolAssembly {

    private ToolAssembly() {}

    /**
     * 名称级拒绝；与 TS {@code filterToolsByDenyRules} 的 blanket deny 对齐（此处为简化：按 tool name 精确匹配）。
     */
    public static List<ToolModule> filterToolsByDenyRules(List<ToolModule> tools, ToolPermissionContext ctx) {
        Objects.requireNonNull(tools, "tools");
        if (ctx.deniedToolNames().isEmpty()) {
            return List.copyOf(tools);
        }
        return tools.stream().filter(t -> !ctx.deniedToolNames().contains(t.spec().name())).toList();
    }

    public static List<ToolModule> filterSimpleMode(List<ToolModule> tools, ToolFeatureFlags flags) {
        if (!flags.simpleMode()) {
            return List.copyOf(tools);
        }
        Set<String> names = flags.simpleToolNames();
        if (names.isEmpty()) {
            return List.of();
        }
        return tools.stream().filter(t -> names.contains(t.spec().name())).toList();
    }

    /** 特性门控：Agent 类、实验类、内部 ant 工具名前缀 */
    public static List<ToolModule> applyFeatureGates(List<ToolModule> tools, ToolFeatureFlags flags) {
        List<ToolModule> out = new ArrayList<>();
        for (ToolModule m : tools) {
            ClaudeLikeTool s = m.spec();
            if (s.category() == ToolCategory.AGENT && !flags.agentSwarmsEnabled()) {
                continue;
            }
            if (s.category() == ToolCategory.EXPERIMENTAL && !flags.experimentalEnabled()) {
                continue;
            }
            if (s.name().startsWith("internal_") && !flags.internalAntTools()) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    /**
     * 单一路径：特性门控 → 简单模式 → 拒绝规则 → 与 MCP 分区合并去重（内置优先）。
     */
    public static List<ToolModule> assembleFilteredToolPool(
            List<ToolModule> baseRegistered,
            ToolPermissionContext ctx,
            ToolFeatureFlags flags,
            List<ToolModule> mcpTools) {
        List<ToolModule> a = applyFeatureGates(baseRegistered, flags);
        a = filterSimpleMode(a, flags);
        a = filterToolsByDenyRules(a, ctx);
        List<ToolModule> mcpFiltered = filterToolsByDenyRules(mcpTools, ctx);
        return assembleToolPool(a, mcpFiltered);
    }

    /**
     * 与 {@code assembleToolPool(permissionContext, mcpTools)} 一致：分区排序后按 {@code name} 去重，先注册的内置工具优先。
     */
    public static List<ToolModule> assembleToolPool(List<ToolModule> builtInTools, List<ToolModule> mcpTools) {
        Comparator<ToolModule> cmpByName = Comparator.comparing(m -> m.spec().name());
        List<ToolModule> sortedBuilt = new ArrayList<>(builtInTools);
        sortedBuilt.sort(cmpByName);
        List<ToolModule> sortedMcp = new ArrayList<>(mcpTools);
        sortedMcp.sort(cmpByName);

        LinkedHashMap<String, ToolModule> mapByName = new LinkedHashMap<>();
        for (ToolModule m : sortedBuilt) {
            mapByName.putIfAbsent(m.spec().name(), m);
        }
        for (ToolModule m : sortedMcp) {
            mapByName.putIfAbsent(m.spec().name(), m);
        }
        return List.copyOf(mapByName.values());
    }

    /** 与 {@code getMergedTools} 一致：不去重，仅拼接。 */
    public static List<ToolModule> getMergedTools(List<ToolModule> builtInTools, List<ToolModule> mcpTools) {
        List<ToolModule> out = new ArrayList<>(builtInTools);
        out.addAll(mcpTools);
        return List.copyOf(out);
    }
}
