package demo.k8s.agent.plugin.hook.event;

import java.time.Instant;

/**
 * 消息接收事件数据
 */
public class MessageReceivedEvent {

    private final String channelId;
    private final String messageId;
    private final String content;
    private final Instant timestamp;

    public MessageReceivedEvent(String channelId, String messageId, String content) {
        this.channelId = channelId;
        this.messageId = messageId;
        this.content = content;
        this.timestamp = Instant.now();
    }

    public String getChannelId() {
        return channelId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getContent() {
        return content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
