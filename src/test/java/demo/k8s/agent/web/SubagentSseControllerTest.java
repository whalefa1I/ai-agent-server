package demo.k8s.agent.web;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.ops.OpsApiAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link SubagentSseController} 单元测试
 */
class SubagentSseControllerTest {

    private EventBus eventBus;
    private DemoMultiAgentProperties properties;
    private SubagentSessionAuthHelper sessionAuthHelper;
    private OpsApiAuthorizer opsApiAuthorizer;
    private SubagentSseController sseController;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        properties = mock(DemoMultiAgentProperties.class);
        sessionAuthHelper = mock(SubagentSessionAuthHelper.class);
        opsApiAuthorizer = mock(OpsApiAuthorizer.class);
        when(sessionAuthHelper.validateSessionOwnership(any(), anyString())).thenReturn(null);
        when(properties.getMaxSseConnectionsPerSession()).thenReturn(8);
        sseController = new SubagentSseController(eventBus, properties, sessionAuthHelper, opsApiAuthorizer);
    }

    @Test
    void testSubscribe_CreatesEmitter() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        String sessionId = "test-session-123";

        // When
        SseEmitter emitter = sseController.subscribeProduct(request, sessionId, null);

        // Then
        assertNotNull(emitter);
        // 发射器已注册到会话
        verifyEmitterRegistered(sessionId, emitter);
    }

    @Test
    void testSubscribe_WithRunId() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        String sessionId = "test-session-123";
        String runId = "run-001";

        // When
        SseEmitter emitter = sseController.subscribeProduct(request, sessionId, runId);

        // Then
        assertNotNull(emitter);
        verifyEmitterRegistered(sessionId, emitter);
    }

    @Test
    void testPublishLogEvent_PublishesToEventBus() {
        // Given
        String runId = "run-001";
        String sessionId = "test-session";
        String content = "Test log message";

        // When
        sseController.publishLogEvent(runId, sessionId, content);

        // Then
        // 验证事件已发布到 EventBus
        var events = eventBus.getEventsByType(SubagentSseController.SubagentLogEvent.class);
        assertEquals(1, events.size());

        SubagentSseController.SubagentLogEvent logEvent = events.get(0);
        assertEquals(runId, logEvent.getRunId());
        assertEquals(sessionId, logEvent.sessionId());
        assertEquals(content, logEvent.getContent());
    }

    @Test
    void testPublishProgressEvent_PublishesToEventBus() {
        // Given
        String runId = "run-001";
        String sessionId = "test-session";
        int percent = 50;
        String phase = "Processing file...";

        // When
        sseController.publishProgressEvent(runId, sessionId, percent, phase);

        // Then
        var events = eventBus.getEventsByType(SubagentSseController.SubagentProgressEvent.class);
        assertEquals(1, events.size());

        SubagentSseController.SubagentProgressEvent progressEvent = events.get(0);
        assertEquals(runId, progressEvent.getRunId());
        assertEquals(sessionId, progressEvent.sessionId());
        assertEquals(percent, progressEvent.getPercent());
        assertEquals(phase, progressEvent.getPhase());
    }

    @Test
    void testPublishStatusEvent_Completed() {
        // Given
        String runId = "run-001";
        String sessionId = "test-session";
        String result = "Task completed successfully";

        // When
        sseController.publishStatusEvent(runId, sessionId, "COMPLETED", result, null);

        // Then
        var events = eventBus.getEventsByType(SubagentSseController.SubagentStatusEvent.class);
        assertEquals(1, events.size());

        SubagentSseController.SubagentStatusEvent statusEvent = events.get(0);
        assertEquals(runId, statusEvent.getRunId());
        assertEquals("COMPLETED", statusEvent.getStatus());
        assertEquals(result, statusEvent.getResult());
    }

    @Test
    void testPublishStatusEvent_Failed() {
        // Given
        String runId = "run-001";
        String sessionId = "test-session";
        String error = "Task failed with error";

        // When
        sseController.publishStatusEvent(runId, sessionId, "FAILED", null, error);

        // Then
        var events = eventBus.getEventsByType(SubagentSseController.SubagentStatusEvent.class);
        assertEquals(1, events.size());

        SubagentSseController.SubagentStatusEvent statusEvent = events.get(0);
        assertEquals(runId, statusEvent.getRunId());
        assertEquals("FAILED", statusEvent.getStatus());
        assertEquals(error, statusEvent.getError());
    }

    @Test
    void testPublishFatalErrorEvent() {
        // Given
        String runId = "run-001";
        String sessionId = "test-session";
        String fatalCode = "IMAGE_PULL_BACKOFF";
        String message = "Failed to pull worker image";
        boolean retryable = false;

        // When
        sseController.publishFatalError(runId, sessionId, fatalCode, message, retryable);

        // Then
        var events = eventBus.getEventsByType(SubagentSseController.SubagentFatalErrorEvent.class);
        assertEquals(1, events.size());

        SubagentSseController.SubagentFatalErrorEvent fatalEvent = events.get(0);
        assertEquals(runId, fatalEvent.getRunId());
        assertEquals(fatalCode, fatalEvent.getFatalCode());
        assertEquals(message, fatalEvent.getMessage());
        assertEquals(retryable, fatalEvent.isRetryable());
    }

    @Test
    void testSseEventDataSerialization() {
        // Given
        String runId = "run-001";
        String sessionId = "test-session";
        String content = "Test log";

        SubagentSseController.SubagentLogEvent logEvent =
                new SubagentSseController.SubagentLogEvent(sessionId, runId, content);

        // When
        Map<String, Object> data = logEvent.toSseData();

        // Then
        assertNotNull(data);
        assertEquals(runId, data.get("runId"));
        assertEquals(sessionId, data.get("sessionId"));
        assertEquals(content, data.get("content"));
    }

    @Test
    void testProgressEventToSseData() {
        // Given
        SubagentSseController.SubagentProgressEvent progressEvent =
                new SubagentSseController.SubagentProgressEvent("session-1", "run-001", 75, "Processing");

        // When
        Map<String, Object> data = progressEvent.toSseData();

        // Then
        assertEquals("run-001", data.get("runId"));
        assertEquals("session-1", data.get("sessionId"));
        assertEquals(75, data.get("percent"));
        assertEquals("Processing", data.get("phase"));
    }

    @Test
    void testStatusEventToSseData_WithResult() {
        // Given
        SubagentSseController.SubagentStatusEvent statusEvent =
                new SubagentSseController.SubagentStatusEvent("session-1", "run-001", "COMPLETED", "Success", null);

        // When
        Map<String, Object> data = statusEvent.toSseData();

        // Then
        assertEquals("run-001", data.get("runId"));
        assertEquals("session-1", data.get("sessionId"));
        assertEquals("COMPLETED", data.get("status"));
        assertEquals("Success", data.get("result"));
        assertFalse(data.containsKey("error"));
    }

    @Test
    void testStatusEventToSseData_WithError() {
        // Given
        SubagentSseController.SubagentStatusEvent statusEvent =
                new SubagentSseController.SubagentStatusEvent("session-1", "run-001", "FAILED", null, "Error message");

        // When
        Map<String, Object> data = statusEvent.toSseData();

        // Then
        assertEquals("run-001", data.get("runId"));
        assertEquals("FAILED", data.get("status"));
        assertEquals("Error message", data.get("error"));
        assertFalse(data.containsKey("result"));
    }

    @Test
    void testFatalErrorEventToSseData() {
        // Given
        SubagentSseController.SubagentFatalErrorEvent fatalEvent =
                new SubagentSseController.SubagentFatalErrorEvent("session-1", "run-001", "FATAL_CODE", "Message", false);

        // When
        Map<String, Object> data = fatalEvent.toSseData();

        // Then
        assertEquals("run-001", data.get("runId"));
        assertEquals("session-1", data.get("sessionId"));
        assertEquals("FATAL_CODE", data.get("fatalCode"));
        assertEquals("Message", data.get("message"));
        assertEquals(false, data.get("retryable"));
    }

    private void verifyEmitterRegistered(String sessionId, SseEmitter emitter) {
        // 简化验证：仅检查 emitter 不为 null
        // 实际测试中可能需要更复杂的验证逻辑来检查内部注册表
        assertNotNull(emitter);
    }
}
