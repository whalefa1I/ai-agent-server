package demo.k8s.agent.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.ai.chat.messages.MessageType;

import java.time.Instant;
import java.util.List;

/**
 * 消息数据
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageData {

    private String role;
    private String content;
    private Instant timestamp;
    private List<?> toolCalls;

    public MessageData() {
    }

    public static MessageDataBuilder builder() {
        return new MessageDataBuilder();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<?> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<?> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public static class MessageDataBuilder {
        private final MessageData data = new MessageData();

        public MessageDataBuilder role(String role) {
            data.setRole(role);
            return this;
        }

        public MessageDataBuilder content(String content) {
            data.setContent(content);
            return this;
        }

        public MessageDataBuilder timestamp(Instant timestamp) {
            data.setTimestamp(timestamp);
            return this;
        }

        public MessageDataBuilder toolCalls(List<?> toolCalls) {
            data.setToolCalls(toolCalls);
            return this;
        }

        public MessageData build() {
            return data;
        }
    }
}
