package demo.k8s.agent.export.exporters;

import demo.k8s.agent.export.ConversationData;
import demo.k8s.agent.export.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * ChatML 格式导出器
 * <p>
 * 输出格式（每行一个对话）：
 * <|im_start|>system
 * {system_content}<|im_end|>
 * <|im_start|>user
 * {user_content}<|im_end|>
 * <|im_start|>assistant
 * {assistant_content}<|im_end|>
 */
public class ChatMLExporter {

    private static final Logger log = LoggerFactory.getLogger(ChatMLExporter.class);

    private static final String START_TOKEN = "<|im_start|>";
    private static final String END_TOKEN = "<|im_end|>";

    /**
     * 导出为 ChatML 格式
     */
    public Path export(List<ConversationData> conversations, Path outputDir) throws IOException {
        String timestamp = Instant.now().toString().replaceAll("[:.]", "-");
        Path outputFile = outputDir.resolve("chatml_export_" + timestamp + ".txt");

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (ConversationData conversation : conversations) {
                writeConversation(conversation, writer);
                writer.newLine(); // 对话之间用空行分隔
            }
        }

        log.info("导出 ChatML 格式完成：{}, 记录数：{}", outputFile, conversations.size());
        return outputFile;
    }

    /**
     * 写入单个对话
     */
    private void writeConversation(ConversationData conversation, BufferedWriter writer) throws IOException {
        // 写入会话元数据注释
        writer.write("# Session: " + conversation.getSessionId());
        writer.newLine();
        writer.write("# User: " + conversation.getUserId());
        writer.newLine();
        if (conversation.getCreatedAt() != null) {
            writer.write("# Timestamp: " + conversation.getCreatedAt());
            writer.newLine();
        }
        writer.newLine();

        // 写入消息
        for (MessageData msg : conversation.getMessages()) {
            String role = mapRole(msg.getRole());
            String content = msg.getContent() != null ? msg.getContent() : "";

            writer.write(START_TOKEN + role);
            writer.newLine();
            writer.write(content);
            writer.newLine();
            writer.write(END_TOKEN);
            writer.newLine();
        }
    }

    /**
     * 映射角色名称到 ChatML 格式
     */
    private String mapRole(String role) {
        return switch (role) {
            case "user" -> "user";
            case "assistant" -> "assistant";
            case "system" -> "system";
            case "tool" -> "tool";
            default -> role;
        };
    }
}
