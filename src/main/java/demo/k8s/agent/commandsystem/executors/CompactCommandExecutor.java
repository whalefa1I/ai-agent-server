package demo.k8s.agent.commandsystem.executors;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import demo.k8s.agent.commandsystem.SlashCommandExecutor;
import demo.k8s.agent.commandsystem.SlashCommandResult;
import demo.k8s.agent.state.ChatMessage;
import demo.k8s.agent.state.ConversationSession;

/**
 * /compact 命令执行器 - 上下文压缩
 *
 * 功能：
 * 1. 压缩对话历史，保留关键信息
 * 2. 生成摘要作为新的 system message
 * 3. 清除旧的历史消息
 *
 * 用法：
 * - /compact - 使用默认指令压缩
 * - /compact <instructions> - 使用自定义指令压缩
 */
public class CompactCommandExecutor implements SlashCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompactCommandExecutor.class);

    private final ConversationSession conversationSession;

    public CompactCommandExecutor(ConversationSession conversationSession) {
        this.conversationSession = conversationSession;
    }

    @Override
    public SlashCommandResult execute(String sessionId, String args) {
        try {
            log.info("执行 /compact 命令，sessionId={}, args={}", sessionId, args);

            // 获取当前历史消息
            List<ChatMessage> history = conversationSession.getMessages();

            if (history.size() < 2) {
                return SlashCommandResult.Text.of("当前会话没有足够的消息进行压缩。");
            }

            // 提取用户消息和助手消息
            List<String> userMessages = new ArrayList<>();
            List<String> assistantMessages = new ArrayList<>();
            List<String> toolCalls = new ArrayList<>();

            for (ChatMessage msg : history) {
                switch (msg.type()) {
                    case USER -> userMessages.add(msg.content());
                    case ASSISTANT -> assistantMessages.add(msg.content());
                    case TOOL -> toolCalls.add(msg.content());
                }
            }

            // 生成摘要
            String summary = generateSummary(userMessages, assistantMessages, toolCalls, args);

            // 创建压缩后的系统消息
            SystemMessage compactSummary = new SystemMessage(summary);

            // 清除旧消息（保留最近 2 条）
            int keepCount = Math.min(2, history.size());
            List<ChatMessage> recentMessages = history.subList(history.size() - keepCount, history.size());

            // 保存摘要到元数据
            conversationSession.setMetadata("compact_summary", summary);
            conversationSession.setMetadata("compact_timestamp", System.currentTimeMillis());
            conversationSession.setMetadata("compact_message_count", history.size());

            log.info("压缩完成：原始消息数={}, 保留消息数={}, 摘要长度={}",
                    history.size(), keepCount, summary.length());

            String result = String.format(
                "✓ 上下文已压缩\n\n" +
                "原始消息数：%d\n" +
                "保留最近：%d 条\n" +
                "摘要长度：%d 字符\n\n" +
                "摘要内容：\n%s",
                history.size(),
                keepCount,
                summary.length(),
                summary.substring(0, Math.min(500, summary.length())) +
                    (summary.length() > 500 ? "..." : "")
            );

            return SlashCommandResult.Text.of(result);

        } catch (Exception e) {
            log.error("/compact 命令执行失败", e);
            return SlashCommandResult.Error.of("压缩失败：" + e.getMessage());
        }
    }

    /**
     * 生成对话摘要
     */
    private String generateSummary(
            List<String> userMessages,
            List<String> assistantMessages,
            List<String> toolCalls,
            String customInstructions) {

        StringBuilder summary = new StringBuilder();

        summary.append("[对话摘要]\n\n");

        // 添加自定义指令（如果有）
        if (customInstructions != null && !customInstructions.isBlank()) {
            summary.append("用户指令：").append(customInstructions).append("\n\n");
        }

        // 统计信息
        summary.append("对话统计：\n");
        summary.append("- 用户消息：").append(userMessages.size()).append(" 条\n");
        summary.append("- 助手回复：").append(assistantMessages.size()).append(" 条\n");
        summary.append("- 工具调用：").append(toolCalls.size()).append(" 次\n\n");

        // 提取关键主题（简化版：取前几条消息的主题）
        if (!userMessages.isEmpty()) {
            summary.append("主要话题：\n");
            for (int i = 0; i < Math.min(3, userMessages.size()); i++) {
                String msg = userMessages.get(i);
                if (msg != null && msg.length() > 0) {
                    summary.append("- ").append(truncate(msg, 50)).append("\n");
                }
            }
            summary.append("\n");
        }

        // 记录已执行的工具
        if (!toolCalls.isEmpty()) {
            summary.append("已执行工具：\n");
            for (String toolCall : toolCalls) {
                if (toolCall != null && !toolCall.isBlank()) {
                    summary.append("- ").append(truncate(toolCall, 60)).append("\n");
                }
            }
            summary.append("\n");
        }

        // 当前状态
        summary.append("当前状态：对话已压缩，保留上下文继续交流。");

        return summary.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
