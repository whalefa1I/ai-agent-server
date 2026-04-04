package demo.k8s.agent.export.exporters;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.export.ConversationData;
import demo.k8s.agent.export.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ShareGPT 格式导出器
 * <p>
 * 输出格式：
 * {
 *   "conversations": [
 *     {"from": "human", "value": "..."},
 *     {"from": "gpt", "value": "..."}
 *   ]
 * }
 */
public class ShareGPTExporter {

    private static final Logger log = LoggerFactory.getLogger(ShareGPTExporter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 导出为 ShareGPT 格式
     */
    public Path export(List<ConversationData> conversations, Path outputDir) throws IOException {
        List<Map<String, Object>> shareGptData = convertToShareGPT(conversations);

        String timestamp = Instant.now().toString().replaceAll("[:.]", "-");
        Path outputFile = outputDir.resolve("sharegpt_export_" + timestamp + ".json");

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), shareGptData);

        log.info("导出 ShareGPT 格式完成：{}, 记录数：{}", outputFile, conversations.size());
        return outputFile;
    }

    /**
     * 转换为 ShareGPT 格式
     */
    private List<Map<String, Object>> convertToShareGPT(List<ConversationData> conversations) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (ConversationData conversation : conversations) {
            Map<String, Object> conversationRecord = new HashMap<>();
            conversationRecord.put("sessionId", conversation.getSessionId());
            conversationRecord.put("userId", conversation.getUserId());
            conversationRecord.put("timestamp", conversation.getCreatedAt() != null ?
                    conversation.getCreatedAt().toString() : null);

            List<Map<String, String>> conversationsList = new ArrayList<>();
            for (MessageData msg : conversation.getMessages()) {
                Map<String, String> message = new HashMap<>();
                message.put("from", convertRole(msg.getRole()));
                message.put("value", msg.getContent() != null ? msg.getContent() : "");
                conversationsList.add(message);
            }

            conversationRecord.put("conversations", conversationsList);
            result.add(conversationRecord);
        }

        return result;
    }

    /**
     * 转换角色名称
     */
    private String convertRole(String role) {
        return switch (role) {
            case "user" -> "human";
            case "assistant" -> "gpt";
            case "system" -> "system";
            case "tool" -> "tool";
            default -> role;
        };
    }
}
