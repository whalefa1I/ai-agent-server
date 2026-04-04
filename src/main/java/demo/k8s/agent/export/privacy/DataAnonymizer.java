package demo.k8s.agent.export.privacy;

import demo.k8s.agent.export.ConversationData;
import demo.k8s.agent.export.MessageData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据脱敏器
 * <p>
 * 用于移除或替换敏感信息，包括：
 * - 用户 ID、Session ID
 * - 邮箱地址
 * - 电话号码
 * - IP 地址
 * - API 密钥和 Token
 */
@Component
public class DataAnonymizer {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?\\d{1,3}[-.]?)?\\(?(\\d{3})\\)?[-.]?(\\d{3})[-.]?(\\d{4})"
    );

    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"
    );

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|token|secret|password|pwd)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_-]{20,})['\"]?"
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "Bearer\\s+[a-zA-Z0-9._-]+"
    );

    /**
     * 脱敏单个对话
     */
    public ConversationData anonymize(ConversationData conversation) {
        // 生成随机的 session ID 和 user ID
        String anonymizedSessionId = "anon_session_" + UUID.randomUUID().toString().substring(0, 8);
        String anonymizedUserId = "anon_user_" + UUID.randomUUID().toString().substring(0, 8);

        // 脱敏消息内容
        List<MessageData> anonymizedMessages = new ArrayList<>();
        for (MessageData msg : conversation.getMessages()) {
            MessageData anonymizedMsg = anonymizeMessage(msg);
            anonymizedMessages.add(anonymizedMsg);
        }

        return ConversationData.builder()
                .sessionId(anonymizedSessionId)
                .userId(anonymizedUserId)
                .messages(anonymizedMessages)
                .createdAt(conversation.getCreatedAt())
                .metadata(null) // 移除元数据
                .build();
    }

    /**
     * 脱敏单条消息
     */
    private MessageData anonymizeMessage(MessageData msg) {
        String content = msg.getContent();
        if (content == null || content.isEmpty()) {
            return msg;
        }

        String anonymizedContent = content;

        // 脱敏邮箱
        anonymizedContent = EMAIL_PATTERN.matcher(anonymizedContent)
                .replaceAll("[EMAIL_REDACTED]");

        // 脱敏电话号码
        anonymizedContent = PHONE_PATTERN.matcher(anonymizedContent)
                .replaceAll("[PHONE_REDACTED]");

        // 脱敏 IP 地址
        anonymizedContent = IP_PATTERN.matcher(anonymizedContent)
                .replaceAll("[IP_REDACTED]");

        // 脱敏 API 密钥
        anonymizedContent = API_KEY_PATTERN.matcher(anonymizedContent)
                .replaceAll("$1: [API_KEY_REDACTED]");

        // 脱敏 Bearer Token
        anonymizedContent = BEARER_TOKEN_PATTERN.matcher(anonymizedContent)
                .replaceAll("Bearer [TOKEN_REDACTED]");

        return MessageData.builder()
                .role(msg.getRole())
                .content(anonymizedContent)
                .timestamp(msg.getTimestamp())
                .toolCalls(msg.getToolCalls())
                .build();
    }

    /**
     * 批量脱敏
     */
    public List<ConversationData> anonymizeAll(List<ConversationData> conversations) {
        return conversations.stream()
                .map(this::anonymize)
                .toList();
    }
}
