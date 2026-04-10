package demo.k8s.agent.subagent;

import demo.k8s.agent.MinimalK8sAgentDemoApplication;
import demo.k8s.agent.coordinator.AsyncSubagentExecutor;
import demo.k8s.agent.coordinator.CoordinatorState;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.state.MessageType;
import demo.k8s.agent.tools.local.LocalToolExecutor;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 子 Agent 模块完整性验收：Facade → Runtime →（Mock）Worker、TaskCreate 路由 → DB 终态。
 */
@SpringBootTest(classes = MinimalK8sAgentDemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(SubagentModuleIntegrationTest.MockAsyncSubagentConfig.class)
@TestPropertySource(
        properties = {
                "demo.multi-agent.enabled=true",
                "demo.multi-agent.mode=on",
                "spring.ai.openai.api-key=test-dummy-key-for-integration",
                "demo.dev.disable-auth=true",
                // 与实体一致的干净 schema（避免沿用文件 H2 上旧表结构导致缺列）
                "spring.datasource.url=jdbc:h2:mem:subagent_integration;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
        }
)
class SubagentModuleIntegrationTest {

    @Autowired
    private MultiAgentFacade multiAgentFacade;

    @Autowired
    private SubagentRunRepository subagentRunRepository;

    @Autowired
    private LocalToolExecutor localToolExecutor;

    @Autowired
    private ConversationManager conversationManager;

    @BeforeEach
    void setUp() {
        TraceContext.init("integration-trace", TraceContext.generateSpanId());
        TraceContext.setSessionId(conversationManager.getSessionId());
        TraceContext.setTenantId("default");
        TraceContext.setAppId("integration-app");
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void facadeSpawnTask_persistsCompletedRun() {
        SpawnResult r = multiAgentFacade.spawnTask(
                "MyTask", "Do something useful", "general", 0, Set.of("file_read"));

        assertTrue(r.isSuccess(), r::getMessage);
        Optional<SubagentRun> row = subagentRunRepository.findById(r.getRunId());
        assertTrue(row.isPresent());
        assertEquals(SubagentRun.RunStatus.COMPLETED, row.get().getStatus());
        assertNotNull(row.get().getResult());
        assertTrue(row.get().getResult().contains("worker-output"));
    }

    @Test
    void spawnBatch_allWorkersComplete_injectsSystemResumeMessage() throws Exception {
        String sid = conversationManager.getSessionId();
        List<MultiAgentFacade.BatchTask> tasks = List.of(
                new MultiAgentFacade.BatchTask("t1", "goal-1", "general"),
                new MultiAgentFacade.BatchTask("t2", "goal-2", "general"),
                new MultiAgentFacade.BatchTask("t3", "goal-3", "general"));
        MultiAgentFacade.BatchSpawnResult br =
                multiAgentFacade.spawnBatch(sid, "main-run-batch", tasks, 0, Set.of("file_read"));
        assertEquals(3, br.totalTasks());

        boolean ok = false;
        for (int i = 0; i < 150; i++) {
            Thread.sleep(50);
            ok = conversationManager.getFullHistory().stream()
                    .anyMatch(m -> m.type() == MessageType.SYSTEM && m.content().contains("SUBAGENT BATCH COMPLETED"));
            if (ok) {
                break;
            }
        }
        assertTrue(ok, "应在全部子任务完成后注入批次完成 SYSTEM 消息");
    }

    @Test
    void taskCreate_routesThroughFacade_whenMultiAgentOn() {
        ClaudeLikeTool tool = mock(ClaudeLikeTool.class);
        when(tool.name()).thenReturn("TaskCreate");

        Map<String, Object> input = new HashMap<>();
        input.put("subject", "Router subject");
        input.put("description", "Router description line");

        LocalToolResult res = localToolExecutor.execute(tool, input, ToolPermissionContext.defaultContext());

        assertTrue(res.isSuccess(), res::getError);
        assertTrue(res.getContent() != null && res.getContent().contains("runId="),
                () -> "expected runId in content: " + res.getContent());
    }

    @TestConfiguration
    static class MockAsyncSubagentConfig {

        @Bean
        @Primary
        AsyncSubagentExecutor asyncSubagentExecutor() {
            AsyncSubagentExecutor mock = mock(AsyncSubagentExecutor.class);
            try {
                doReturn(new CoordinatorState.TaskResult("coordinator-internal", "worker-output", null))
                        .when(mock)
                        .runSynchronousAgent(anyString(), anyString(), anyString(), any(Duration.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return mock;
        }
    }
}
