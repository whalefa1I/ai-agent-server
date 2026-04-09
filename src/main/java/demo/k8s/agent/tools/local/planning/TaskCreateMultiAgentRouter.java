package demo.k8s.agent.tools.local.planning;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.MultiAgentFacade;
import demo.k8s.agent.subagent.SpawnGatekeeper;
import demo.k8s.agent.subagent.SpawnResult;
import demo.k8s.agent.subagent.SubagentRun;
import demo.k8s.agent.subagent.SubagentRunService;
import demo.k8s.agent.tools.local.LocalToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * TaskCreate 路由：在 {@code demo.multi-agent} 开启且 {@code mode=on} 时走 {@link MultiAgentFacade}，
 * 与内存 Task 列表通过 {@link TaskTools#mirrorSubagentRunCompleted} 对齐 runId。
 */
@Component
public class TaskCreateMultiAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(TaskCreateMultiAgentRouter.class);

    private final DemoMultiAgentProperties multiAgentProperties;
    private final ObjectProvider<MultiAgentFacade> multiAgentFacade;
    private final SpawnGatekeeper spawnGatekeeper;
    private final SubagentRunService subagentRunService;

    public TaskCreateMultiAgentRouter(
            DemoMultiAgentProperties multiAgentProperties,
            ObjectProvider<MultiAgentFacade> multiAgentFacade,
            SpawnGatekeeper spawnGatekeeper,
            SubagentRunService subagentRunService) {
        this.multiAgentProperties = multiAgentProperties;
        this.multiAgentFacade = multiAgentFacade;
        this.spawnGatekeeper = spawnGatekeeper;
        this.subagentRunService = subagentRunService;
    }

    public LocalToolResult routeTaskCreate(Map<String, Object> input) {
        try {
            if (!multiAgentProperties.isEnabled()
                    || multiAgentProperties.getMode() == DemoMultiAgentProperties.Mode.off) {
                return TaskTools.executeTaskCreate(input);
            }
            if (multiAgentProperties.getMode() == DemoMultiAgentProperties.Mode.shadow) {
                return TaskTools.taskCreateShadowBlocked(input);
            }

            TaskCreateParseResult parsed = TaskTools.parseTaskCreateInput(input);
            if (parsed instanceof TaskCreateParseResult.ParseError e) {
                return e.error();
            }
            var p = (TaskCreateParseResult.Parsed) parsed;

            Set<String> allowed = spawnGatekeeper.globalSafeToolNames();
            SpawnResult spawnResult = multiAgentFacade.getObject().spawnTask(
                    p.subject(), p.description(), "general", 0, allowed);
            String sessionId = TraceContext.getSessionId();

            if (!spawnResult.isSuccess()) {
                String msg = spawnResult.getMessage() != null ? spawnResult.getMessage() : "spawn failed";
                if (spawnResult.getMustDoNext() != null && spawnResult.getMustDoNext().suggestion() != null) {
                    msg = spawnResult.getMustDoNext().suggestion();
                }
                log.info("[TaskCreateRouter] Spawn rejected or failed: sessionId={}, subject={}, msg={}",
                        sessionId, p.subject(), msg);
                return TaskTools.taskCreateSpawnRejected(input, msg);
            }

            String runId = spawnResult.getRunId();
            if (sessionId == null || sessionId.isBlank()) {
                return TaskTools.taskCreateSpawnRejected(input, "No active session for subagent run");
            }

            SubagentRun row = subagentRunService.getRun(runId, sessionId);
            String preview = row.getResult() != null ? row.getResult() : "";
            TaskTools.mirrorSubagentRunCompleted(runId, p.subject(), p.description(), preview);
            log.info("[TaskCreateRouter] Spawn success: sessionId={}, runId={}, runStatus={}",
                    sessionId, runId, row.getStatus());

            return TaskTools.taskCreateSuccessWithRunId(runId, p.subject());
        } catch (Exception e) {
            log.error("[TaskCreateRouter] Failed", e);
            return TaskTools.taskCreateSpawnRejected(input, e.getMessage() != null ? e.getMessage() : "router error");
        }
    }
}
