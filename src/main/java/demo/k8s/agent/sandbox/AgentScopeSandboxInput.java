package demo.k8s.agent.sandbox;

import java.util.Map;

/**
 * AgentScope 沙盒输入参数。
 *
 * @param sessionId 会话 ID
 * @param sandboxType 沙盒类型
 * @param code      Python 代码
 * @param command   Shell 命令
 * @param arguments 工具参数
 */
public record AgentScopeSandboxInput(
        String sessionId,
        String sandboxType,
        String code,
        String command,
        Map<String, Object> arguments
) {
    /**
     * 创建 Python 执行输入。
     *
     * @param sessionId 会话 ID
     * @param code      Python 代码
     * @return 输入参数
     */
    public static AgentScopeSandboxInput forPython(String sessionId, String code) {
        return new AgentScopeSandboxInput(sessionId, "base", code, null, null);
    }

    /**
     * 创建 Shell 执行输入。
     *
     * @param sessionId 会话 ID
     * @param command   Shell 命令
     * @return 输入参数
     */
    public static AgentScopeSandboxInput forShell(String sessionId, String command) {
        return new AgentScopeSandboxInput(sessionId, "base", null, command, null);
    }

    /**
     * 创建工具调用输入。
     *
     * @param sessionId 会话 ID
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 输入参数
     */
    public static AgentScopeSandboxInput forTool(String sessionId, String toolName, Map<String, Object> arguments) {
        return new AgentScopeSandboxInput(sessionId, null, null, null, arguments);
    }
}
