package demo.k8s.agent.toolstate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

/**
 * ToolState 使用示例
 *
 * 演示如何在工具调用流程中集成 ToolStateService
 */
public class ToolStateExample {

    private static final Logger log = LoggerFactory.getLogger(ToolStateExample.class);

    @Autowired
    private ToolStateService toolStateService;

    /**
     * 示例：完整的工具调用流程
     *
     * 1. 创建工具状态 (todo)
     * 2. 更新为计划中 (plan)
     * 3. 更新为待确认 (pending_confirmation)
     * 4. 等待用户确认后执行 (executing)
     * 5. 完成或失败 (completed/failed)
     */
    public void executeToolWithStateTracking(String sessionId, String userId, String toolName,
                                              Map<String, Object> toolInput, WebSocketSession session) {
        // Step 1: 创建工具状态 (todo)
        ToolArtifact artifact = toolStateService.createToolArtifact(
            sessionId,
            userId,
            toolName,
            "tool",
            ToolStatus.TODO,
            Map.of("todo", "Execute " + toolName),
            session
        );
        log.info("创建工具状态：artifactId={}, status=todo", artifact.getId());

        // Step 2: 更新为计划中 (plan)
        List<String> plan = List.of(
            "1. Validate input parameters",
            "2. Execute command",
            "3. Return output"
        );
        toolStateService.updateToolArtifact(
            artifact.getId(),
            userId,
            ToolStatus.PLAN,
            Map.of("plan", plan),
            artifact.getBodyVersion(),
            session
        );
        log.info("更新工具状态：artifactId={}, status=plan", artifact.getId());

        // Step 3: 更新为待确认 (pending_confirmation)
        ToolStateService.UpdateResult result = toolStateService.updateToolArtifact(
            artifact.getId(),
            userId,
            ToolStatus.PENDING_CONFIRMATION,
            Map.of(
                "input", toolInput,
                "confirmation", Map.of("requested", true)
            ),
            2,
            session
        );

        if (result.isVersionMismatch()) {
            log.warn("版本冲突，需要刷新后重试");
            return;
        }

        log.info("更新工具状态：artifactId={}, status=pending_confirmation, 等待用户确认", artifact.getId());

        // Step 4: 执行工具 (executing) - 假设用户已确认
        toolStateService.updateToolArtifact(
            artifact.getId(),
            userId,
            ToolStatus.EXECUTING,
            Map.of(
                "input", toolInput,
                "progress", "Executing command..."
            ),
            3,
            session
        );
        log.info("更新工具状态：artifactId={}, status=executing", artifact.getId());

        // Step 5: 完成 (completed)
        Object output = Map.of("stdout", "file1.txt", "stderr", "", "exitCode", 0);
        toolStateService.updateToolArtifact(
            artifact.getId(),
            userId,
            ToolStatus.COMPLETED,
            Map.of(
                "input", toolInput,
                "output", output
            ),
            4,
            session
        );
        log.info("更新工具状态：artifactId={}, status=completed", artifact.getId());
    }

    /**
     * 示例：获取会话的所有工具状态
     */
    public List<ToolArtifact> getSessionToolStates(String sessionId) {
        return toolStateService.getSessionToolArtifacts(sessionId);
    }
}
