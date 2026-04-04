package demo.k8s.agent.export.exporters;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.export.ConversationData;
import demo.k8s.agent.export.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alpaca 格式导出器
 * <p>
 * 输出格式（JSONL）：
 * {"instruction": "...", "input": "...", "output": "..."}
 */
public class AlpacaExporter {

    private static final Logger log = LoggerFactory.getLogger(AlpacaExporter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 导出为 Alpaca 格式（单个 JSON 文件，包含所有对话）
     */
    public Path export(List<ConversationData> conversations, Path outputDir) throws IOException {
        List<Map<String, Object>> alpacaData = convertToAlpaca(conversations);

        String timestamp = Instant.now().toString().replaceAll("[:.]", "-");
        Path outputFile = outputDir.resolve("alpaca_export_" + timestamp + ".json");

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), alpacaData);

        log.info("导出 Alpaca 格式完成：{}, 记录数：{}", outputFile, conversations.size());
        return outputFile;
    }

    /**
     * 导出为 JSONL 格式（每行一个 JSON 对象）
     */
    public Path exportJsonl(List<ConversationData> conversations, Path outputDir) throws IOException {
        String timestamp = Instant.now().toString().replaceAll("[:.]", "-");
        Path outputFile = outputDir.resolve("export_" + timestamp + ".jsonl");

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (ConversationData conversation : conversations) {
                List<Map<String, Object>> records = convertConversationToJsonl(conversation);
                for (Map<String, Object> record : records) {
                    writer.write(objectMapper.writeValueAsString(record));
                    writer.newLine();
                }
            }
        }

        log.info("导出 JSONL 格式完成：{}, 记录数：{}", outputFile, conversations.size());
        return outputFile;
    }

    /**
     * 转换为 Alpaca 格式
     */
    private List<Map<String, Object>> convertToAlpaca(List<ConversationData> conversations) {
        return conversations.stream()
                .flatMap(conv -> convertConversationToAlpaca(conv).stream())
                .toList();
    }

    /**
     * 将单个对话转换为 Alpaca 格式记录
     */
    private List<Map<String, Object>> convertConversationToAlpaca(ConversationData conversation) {
        List<MessageData> messages = conversation.getMessages();
        List<Map<String, Object>> records = new java.util.ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            MessageData msg = messages.get(i);
            String role = msg.getRole();

            // 只处理用户 - 助手对话对
            if ("user".equals(role) && i + 1 < messages.size()) {
                MessageData nextMsg = messages.get(i + 1);
                if ("assistant".equals(nextMsg.getRole())) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("instruction", msg.getContent());
                    record.put("input", ""); // 可选的上下文输入
                    record.put("output", nextMsg.getContent());

                    // 添加元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("sessionId", conversation.getSessionId());
                    metadata.put("userId", conversation.getUserId());
                    metadata.put("timestamp", conversation.getCreatedAt() != null ?
                            conversation.getCreatedAt().toString() : null);
                    record.put("metadata", metadata);

                    records.add(record);
                }
            }
        }

        return records;
    }

    /**
     * 将单个对话转换为 JSONL 记录
     */
    private List<Map<String, Object>> convertConversationToJsonl(ConversationData conversation) {
        List<MessageData> messages = conversation.getMessages();
        List<Map<String, Object>> records = new java.util.ArrayList<>();

        for (MessageData msg : messages) {
            Map<String, Object> record = new HashMap<>();
            record.put("role", msg.getRole());
            record.put("content", msg.getContent());
            record.put("sessionId", conversation.getSessionId());
            record.put("userId", conversation.getUserId());
            record.put("timestamp", conversation.getCreatedAt() != null ?
                    conversation.getCreatedAt().toString() : null);

            if (msg.getTimestamp() != null) {
                record.put("messageTimestamp", msg.getTimestamp().toString());
            }

            records.add(record);
        }

        return records;
    }
}
