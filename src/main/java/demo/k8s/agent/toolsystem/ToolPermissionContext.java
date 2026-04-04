package demo.k8s.agent.toolsystem;

import java.util.Collections;
import java.util.Set;

/**
 * 与 Claude Code {@code ToolPermissionContext} 类似：携带当前会话的权限与可见工具类别。
 *
 * @param mode 权限模式
 * @param allowedCategories 为空表示不限制类别；非空则仅允许列表中的分类
 * @param deniedToolNames 名称级 blanket deny，与 {@code filterToolsByDenyRules} 对齐
 */
public record ToolPermissionContext(
        ToolPermissionMode mode, Set<ToolCategory> allowedCategories, Set<String> deniedToolNames) {

    public ToolPermissionContext {
        deniedToolNames = deniedToolNames == null ? Set.of() : Set.copyOf(deniedToolNames);
    }

    public static ToolPermissionContext defaultContext() {
        return new ToolPermissionContext(ToolPermissionMode.DEFAULT, Collections.emptySet(), Set.of());
    }

    public boolean isCategoryAllowed(ToolCategory category) {
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            return true;
        }
        return allowedCategories.contains(category);
    }
}
