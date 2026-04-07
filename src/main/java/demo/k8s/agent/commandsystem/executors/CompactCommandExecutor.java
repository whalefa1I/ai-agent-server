package demo.k8s.agent.commandsystem.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import demo.k8s.agent.commandsystem.SlashCommandExecutor;
import demo.k8s.agent.commandsystem.SlashCommandResult;
import demo.k8s.agent.query.MessageTextEstimator;
import demo.k8s.agent.state.ChatMessage;
import demo.k8s.agent.state.ConversationSession;
import demo.k8s.agent.state.MessageType;

/**
 * /compact 命令执行器 - 上下文压缩
 *
 * 功能：
 * 1. 手动触发上下文压缩（使用 LLM 生成摘要）
 * 2. 显示压缩统计信息
 * 3. 支持自定义压缩指令
 *
 * 用法：
 * - /compact - 使用默认指令压缩
 * - /compact <instructions> - 使用自定义指令压缩（如："保留所有文件路径和错误信息"）
 */
public class CompactCommandExecutor implements SlashCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompactCommandExecutor.class);

    private final ConversationSession conversationSession;
    private final ChatModel chatModel;

    public CompactCommandExecutor(ConversationSession conversationSession, ChatModel chatModel) {
        this.conversationSession = conversationSession;
        this.chatModel = chatModel;
    }

    @Override
    public SlashCommandResult execute(String sessionId, String args) {
        try {
            log.info("执行 /compact 命令，sessionId={}, args={}", sessionId, args);

            // 获取当前历史消息
            List<ChatMessage> history = conversationSession.getMessages();

            if (history.size() < 2) {
                return SlashCommandResult.Text.of("⚠️ 当前会话没有足够的消息进行压缩（至少需要 2 条消息）。");
            }

            // 转换为 Spring AI Message 格式
            List<Message> messages = convertToSpringAiMessages(history);

            // 估算当前上下文大小
            int totalChars = MessageTextEstimator.estimateChars(messages);

            // 使用 LLM 生成摘要
            String summary = generateSummaryWithLLM(messages, args);

            // 创建压缩后的系统消息
            String compactSummaryContent = "[对话已压缩] 以下摘要由 /compact 命令生成。\n\n" + summary;
            SystemMessage compactSummary = new SystemMessage(compactSummaryContent);

            // 清除旧消息（保留最近 1 条用户消息）
            List<ChatMessage> newHistory = new ArrayList<>();
            newHistory.add(ChatMessage.system(compactSummary.getContent()));

            // 保留最后一条用户消息
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).type() == MessageType.USER) {
                    newHistory.add(history.get(i));
                    break;
                }
            }

            // 保存摘要到元数据
            conversationSession.setMetadata("compact_summary", summary);
            conversationSession.setMetadata("compact_timestamp", System.currentTimeMillis());
            conversationSession.setMetadata("compact_original_count", history.size());
            conversationSession.setMetadata("compact_chars", totalChars);

            // 替换会话消息
            // 由于 ConversationSession 没有 setMessages 方法，我们需要清除后重新添加
            // 这里通过元数据标记压缩已完成，实际消息清理在 query loop 中处理
            conversationSession.setMetadata("compacted_messages", newHistory);

            log.info("压缩完成：原始消息数={}, 保留消息数={}, 摘要长度={}, 字符数={}",
                    history.size(), newHistory.size(), summary.length(), totalChars);

            String result = String.format(
                "✓ 上下文已压缩\n\n" +
                "┌─ 压缩统计 ────────────┐\n" +
                "│ 原始消息数：%d\n" +
                "│ 保留消息数：%d\n" +
                "│ 上下文大小：%d 字符\n" +
                "│ 摘要长度：%d 字符\n" +
                "└──────────────────────┘\n\n" +
                "┌─ 摘要预览 ────────────┐\n%s\n" +
                "└──────────────────────┘",
                history.size(),
                newHistory.size(),
                totalChars,
                summary.length(),
                formatSummaryPreview(summary, 800)
            );

            return SlashCommandResult.Text.of(result);

        } catch (Exception e) {
            log.error("/compact 命令执行失败", e);
            return SlashCommandResult.Error.of("压缩失败：" + e.getMessage());
        }
    }

    /**
     * 使用 LLM 生成对话摘要
     */
    private String generateSummaryWithLLM(List<Message> messages, String customInstructions) {
        try {
            // 提取对话内容
            String transcript = MessageTextEstimator.bodiesForSummary(messages, 60_000);

            StringBuilder userPromptBuilder = new StringBuilder();
            userPromptBuilder.append("下列对话片段体积约 ").append(transcript.length())
                    .append(" 字符。\n\n");

            if (customInstructions != null && !customInstructions.isBlank()) {
                userPromptBuilder.append("用户要求：").append(customInstructions).append("\n\n");
            }

            userPromptBuilder.append("请用中文写一段结构化摘要，要求：\n")
                    .append("1. 保留所有关键事实、文件路径、错误信息\n")
                    .append("2. 记录未决问题和待办事项\n")
                    .append("3. 简洁明了，不超过 2000 字\n\n")
                    .append("对话片段：\n")
                    .append(transcript);

            String userPrompt = userPromptBuilder.toString();

            Prompt prompt = new Prompt(
                    List.of(
                            new SystemMessage(
                                    "你是上下文压缩助手。请生成简洁的对话摘要，保留所有关键信息。" +
                                    "只输出摘要正文，不要调用工具，不要添加额外说明。"),
                            new UserMessage(userPrompt)),
                    OpenAiChatOptions.builder()
                            .temperature(0.2)
                            .maxTokens(3000)
                            .build());

            log.info("[/compact] 调用 LLM 生成摘要...");
            var compactResp = chatModel.call(prompt);

            String summary = "";
            if (compactResp != null && compactResp.getResult() != null
                    && compactResp.getResult().getOutput() != null) {
                summary = compactResp.getResult().getOutput().getText();
                if (summary == null) summary = "[摘要生成失败]";
            }

            log.info("[/compact] 摘要生成完成：{} 字符", summary.length());
            return summary;

        } catch (Exception e) {
            log.error("[/compact] 摘要生成失败：{}", e.getMessage(), e);
            // 降级处理：返回基础统计信息
            return generateFallbackSummary(messages);
        }
    }

    /**
     * 降级摘要（LLM 失败时使用）
     */
    private String generateFallbackSummary(List<Message> messages) {
        StringBuilder summary = new StringBuilder();
        summary.append("[对话摘要 - 简化版]\n\n");

        int userCount = 0, assistantCount = 0, toolCount = 0;
        List<String> recentTopics = new ArrayList<>();

        for (Message m : messages) {
            if (m instanceof UserMessage) {
                userCount++;
                String text = m.getContent();
                if (text != null && text.length() > 0 && recentTopics.size() < 5) {
                    recentTopics.add(truncate(text, 60));
                }
            } else if (m instanceof org.springframework.ai.chat.messages.AssistantMessage) {
                assistantCount++;
            } else if (m instanceof org.springframework.ai.chat.messages.ToolResponseMessage) {
                toolCount++;
            }
        }

        summary.append("对话统计：\n");
        summary.append("- 用户消息：").append(userCount).append(" 条\n");
        summary.append("- 助手回复：").append(assistantCount).append(" 条\n");
        summary.append("- 工具调用：").append(toolCount).append(" 次\n\n");

        if (!recentTopics.isEmpty()) {
            summary.append("最近话题：\n");
            for (String topic : recentTopics) {
                summary.append("- ").append(topic).append("\n");
            }
        }

        return summary.toString();
    }

    /**
     * 格式化摘要预览（用于显示）
     */
    private String formatSummaryPreview(String summary, int maxChars) {
        if (summary == null || summary.isEmpty()) {
            return "[无摘要]";
        }

        String preview = summary;
        if (preview.length() > maxChars) {
            preview = preview.substring(0, maxChars) + "\n\n... (摘要过长，已折叠)";
        }

        // 格式化输出
        return preview.replaceAll("\n", "\n│ ");
    }

    /**
     * 转换 ChatMessage 为 Spring AI Message
     */
    private List<Message> convertToSpringAiMessages(List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            String content = msg.content() != null ? msg.content() : "";
            switch (msg.type()) {
                case USER -> messages.add(new UserMessage(content));
                case ASSISTANT -> messages.add(new org.springframework.ai.chat.messages.AssistantMessage(content));
                case SYSTEM -> messages.add(new SystemMessage(content));
                case TOOL -> {
                    // 工具消息转换为 ToolResponseMessage（简化处理）
                    var toolResponse = new org.springframework.ai.chat.messages.ToolResponseMessage(
                            List.of(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                    "unknown", "unknown", content)),
                            Map.of());
                    messages.add(toolResponse);
                }
            }
        }
        return messages;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
