package demo.k8s.agent.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 对话数据
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationData {

    private String sessionId;
    private String userId;
    private List<MessageData> messages;
    private Instant createdAt;
    private Map<String, Object> metadata;

    public ConversationData() {
    }

    public static ConversationDataBuilder builder() {
        return new ConversationDataBuilder();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<MessageData> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageData> messages) {
        this.messages = messages;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public static class ConversationDataBuilder {
        private final ConversationData data = new ConversationData();

        public ConversationDataBuilder sessionId(String sessionId) {
            data.setSessionId(sessionId);
            return this;
        }

        public ConversationDataBuilder userId(String userId) {
            data.setUserId(userId);
            return this;
        }

        public ConversationDataBuilder messages(List<MessageData> messages) {
            data.setMessages(messages);
            return this;
        }

        public ConversationDataBuilder createdAt(Instant createdAt) {
            data.setCreatedAt(createdAt);
            return this;
        }

        public ConversationDataBuilder metadata(Map<String, Object> metadata) {
            data.setMetadata(metadata);
            return this;
        }

        public ConversationData build() {
            return data;
        }
    }
}
