package demo.k8s.agent.plugin.hook.event;

import java.time.Instant;

/**
 * Agent 回合事件数据
 */
public class AgentTurnEvent {

    private final String sessionId;
    private final int turnNumber;
    private final String userMessage;
    private final Instant timestamp;

    public AgentTurnEvent(String sessionId, int turnNumber, String userMessage) {
        this.sessionId = sessionId;
        this.turnNumber = turnNumber;
        this.userMessage = userMessage;
        this.timestamp = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
