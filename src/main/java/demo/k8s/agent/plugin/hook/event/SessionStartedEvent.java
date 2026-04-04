package demo.k8s.agent.plugin.hook.event;

import java.time.Instant;

/**
 * 会话开始事件数据
 */
public class SessionStartedEvent {

    private final String sessionId;
    private final String userId;
    private final Instant timestamp;

    public SessionStartedEvent(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.timestamp = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
