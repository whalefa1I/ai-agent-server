package demo.k8s.agent.web;

import demo.k8s.agent.config.DemoMultiAgentProperties;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event;
import demo.k8s.agent.ops.OpsApiAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 子 Agent SSE 实时推送端点。
 * <p>
 * 产品路径：GET /api/v2/stream/subagent?sessionId=xxx[&amp;runId=xxx]
 * 运维路径：GET /api/ops/stream/subagent/{sessionId}[?runId=]（需 {@code X-Ops-Secret}）
 */
@RestController
@CrossOrigin(origins = "*")
public class SubagentSseController {

    private static final Logger log = LoggerFactory.getLogger(SubagentSseController.class);

    private final Map<String, List<SseRegistration>> sessionEmitters = new ConcurrentHashMap<>();

    private final EventBus eventBus;
    private final DemoMultiAgentProperties properties;
    private final SubagentSessionAuthHelper sessionAuthHelper;
    private final OpsApiAuthorizer opsApiAuthorizer;

    public SubagentSseController(EventBus eventBus,
                                 DemoMultiAgentProperties properties,
                                 SubagentSessionAuthHelper sessionAuthHelper,
                                 OpsApiAuthorizer opsApiAuthorizer) {
        this.eventBus = eventBus;
        this.properties = properties;
        this.sessionAuthHelper = sessionAuthHelper;
        this.opsApiAuthorizer = opsApiAuthorizer;

        eventBus.subscribe(SubagentLogEvent.class, this::onSubagentLogEvent);
        eventBus.subscribe(SubagentProgressEvent.class, this::onSubagentProgressEvent);
        eventBus.subscribe(SubagentStatusEvent.class, this::onSubagentStatusEvent);
        eventBus.subscribe(SubagentFatalErrorEvent.class, this::onSubagentFatalErrorEvent);

        log.info("[SubagentSseController] Initialized with SSE event subscriptions");
    }

    @GetMapping(value = "/api/v2/stream/subagent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeProduct(HttpServletRequest request,
                                       @RequestParam String sessionId,
                                       @RequestParam(required = false) String runId) {
        ResponseEntity<?> denied = sessionAuthHelper.validateSessionOwnership(request, sessionId);
        if (denied != null) {
            throwDenied(denied);
        }
        return registerEmitter(sessionId, runId);
    }

    @GetMapping(value = "/api/ops/stream/subagent/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeOps(HttpServletRequest request,
                                   @PathVariable String sessionId,
                                   @RequestParam(required = false) String runId) {
        ResponseEntity<Map<String, Object>> deny = opsApiAuthorizer.preflight(request);
        if (deny != null) {
            throwDenied(deny);
        }
        return registerEmitter(sessionId, runId);
    }

    private SseEmitter registerEmitter(String sessionId, String runId) {
        int cap = Math.max(1, properties.getMaxSseConnectionsPerSession());
        List<SseRegistration> list = sessionEmitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        if (list.size() >= cap) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "SSE connection limit reached for session (max=" + cap + ")");
        }

        SseEmitter emitter = new SseEmitter(0L);
        String runFilter = (runId != null && !runId.isBlank()) ? runId.trim() : null;
        SseRegistration reg = new SseRegistration(emitter, runFilter);
        list.add(reg);

        Runnable cleanup = () -> unregister(sessionId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        sendHeartbeat(emitter);
        return emitter;
    }

    private void unregister(String sessionId, SseEmitter emitter) {
        List<SseRegistration> list = sessionEmitters.get(sessionId);
        if (list == null) {
            return;
        }
        list.removeIf(r -> r.emitter == emitter);
        if (list.isEmpty()) {
            sessionEmitters.remove(sessionId);
        }
    }

    private static void throwDenied(ResponseEntity<?> denied) {
        throw new ResponseStatusException(
                HttpStatus.valueOf(denied.getStatusCode().value()),
                denied.getBody() != null ? denied.getBody().toString() : "Forbidden");
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data(Map.of("timestamp", System.currentTimeMillis())));
        } catch (IOException e) {
            log.debug("Failed to send heartbeat", e);
        }
    }

    private void onSubagentLogEvent(SubagentLogEvent event) {
        pushToSession(event.sessionId(), "LOG_OUTPUT", event.toSseData(), event.getRunId());
    }

    private void onSubagentProgressEvent(SubagentProgressEvent event) {
        pushToSession(event.sessionId(), "PROGRESS", event.toSseData(), event.getRunId());
    }

    private void onSubagentStatusEvent(SubagentStatusEvent event) {
        pushToSession(event.sessionId(), event.getEventType(), event.toSseData(), event.getRunId());
    }

    private void onSubagentFatalErrorEvent(SubagentFatalErrorEvent event) {
        pushToSession(event.sessionId(), "FATAL_ERROR", event.toSseData(), event.getRunId());
    }

    private void pushToSession(String sessionId, String eventName, Map<String, Object> data, String eventRunId) {
        List<SseRegistration> list = sessionEmitters.get(sessionId);
        if (list == null || list.isEmpty()) {
            return;
        }

        List<SseRegistration> stale = new ArrayList<>();
        for (SseRegistration reg : list) {
            if (reg.runFilter != null) {
                if (eventRunId == null || !reg.runFilter.equals(eventRunId)) {
                    continue;
                }
            }
            try {
                reg.emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.debug("Failed to push SSE event, will remove emitter: {}", e.getMessage());
                stale.add(reg);
            }
        }
        if (!stale.isEmpty()) {
            list.removeAll(stale);
            if (list.isEmpty()) {
                sessionEmitters.remove(sessionId);
            }
        }
    }

    public void publishLogEvent(String runId, String sessionId, String content) {
        eventBus.publish(new SubagentLogEvent(sessionId, runId, content));
    }

    public void publishProgressEvent(String runId, String sessionId, int percent, String phase) {
        eventBus.publish(new SubagentProgressEvent(sessionId, runId, percent, phase));
    }

    public void publishStatusEvent(String runId, String sessionId, String status, String result, String error) {
        eventBus.publish(new SubagentStatusEvent(sessionId, runId, status, result, error));
    }

    public void publishFatalError(String runId, String sessionId, String fatalCode, String message, boolean retryable) {
        eventBus.publish(new SubagentFatalErrorEvent(sessionId, runId, fatalCode, message, retryable));
    }

    private record SseRegistration(SseEmitter emitter, String runFilter) {}

    public static class SubagentLogEvent extends Event {
        private final String runId;
        private final String content;

        public SubagentLogEvent(String sessionId, String runId, String content) {
            super(sessionId, "system", Map.of("runId", runId, "content", content));
            this.runId = runId;
            this.content = content;
        }

        public String getRunId() {
            return runId;
        }

        public String getContent() {
            return content;
        }

        public Map<String, Object> toSseData() {
            return Map.of(
                    "runId", runId,
                    "sessionId", sessionId(),
                    "content", content
            );
        }

        @Override
        public String getEventType() {
            return "LOG_OUTPUT";
        }
    }

    public static class SubagentProgressEvent extends Event {
        private final String runId;
        private final int percent;
        private final String phase;

        public SubagentProgressEvent(String sessionId, String runId, int percent, String phase) {
            super(sessionId, "system", Map.of("runId", runId, "percent", percent, "phase", phase));
            this.runId = runId;
            this.percent = percent;
            this.phase = phase;
        }

        public String getRunId() {
            return runId;
        }

        public int getPercent() {
            return percent;
        }

        public String getPhase() {
            return phase;
        }

        public Map<String, Object> toSseData() {
            return Map.of(
                    "runId", runId,
                    "sessionId", sessionId(),
                    "percent", percent,
                    "phase", phase
            );
        }

        @Override
        public String getEventType() {
            return "PROGRESS";
        }
    }

    public static class SubagentStatusEvent extends Event {
        private final String runId;
        private final String status;
        private final String result;
        private final String error;

        public SubagentStatusEvent(String sessionId, String runId, String status, String result, String error) {
            super(sessionId, "system", buildStatusPayload(runId, status, result, error));
            this.runId = runId;
            this.status = status;
            this.result = result;
            this.error = error;
        }

        private static Map<String, Object> buildStatusPayload(String runId, String status, String result, String error) {
            Map<String, Object> payload = new ConcurrentHashMap<>();
            payload.put("runId", runId);
            payload.put("status", status);
            if (result != null) {
                payload.put("result", result);
            }
            if (error != null) {
                payload.put("error", error);
            }
            return payload;
        }

        public String getRunId() {
            return runId;
        }

        public String getStatus() {
            return status;
        }

        public String getResult() {
            return result;
        }

        public String getError() {
            return error;
        }

        public Map<String, Object> toSseData() {
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("runId", runId);
            data.put("sessionId", sessionId());
            data.put("status", status);
            if (result != null) {
                data.put("result", result);
            }
            if (error != null) {
                data.put("error", error);
            }
            return data;
        }

        @Override
        public String getEventType() {
            return status;
        }
    }

    public static class SubagentFatalErrorEvent extends Event {
        private final String runId;
        private final String fatalCode;
        private final String message;
        private final boolean retryable;

        public SubagentFatalErrorEvent(String sessionId, String runId, String fatalCode,
                                        String message, boolean retryable) {
            super(sessionId, "system", Map.of(
                    "runId", runId,
                    "fatalCode", fatalCode,
                    "message", message,
                    "retryable", retryable));
            this.runId = runId;
            this.fatalCode = fatalCode;
            this.message = message;
            this.retryable = retryable;
        }

        public String getRunId() {
            return runId;
        }

        public String getFatalCode() {
            return fatalCode;
        }

        public String getMessage() {
            return message;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public Map<String, Object> toSseData() {
            return Map.of(
                    "runId", runId,
                    "sessionId", sessionId(),
                    "fatalCode", fatalCode,
                    "message", message,
                    "retryable", retryable
            );
        }

        @Override
        public String getEventType() {
            return "FATAL_ERROR";
        }
    }
}
