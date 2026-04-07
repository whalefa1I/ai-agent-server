package demo.k8s.agent.commandsystem.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import demo.k8s.agent.commandsystem.SlashCommandExecutor;
import demo.k8s.agent.commandsystem.SlashCommandResult;
import demo.k8s.agent.query.MessageTextEstimator;
import demo.k8s.agent.state.ChatMessage;
import demo.k8s.agent.state.ConversationSession;

/**
 * /context 命令执行器 - 查看上下文使用情况
 *
 * 功能：
 * 1. 显示当前上下文使用量（字符数、消息数）
 * 2. 显示压缩阈值和状态
 * 3. 显示各类型消息分布
 *
 * 用法：
 * - /context - 查看上下文使用统计
 */
public class ContextCommandExecutor implements SlashCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ContextCommandExecutor.class);

    /** 警告阈值（字符数） */
    private static final int WARNING_THRESHOLD = 80_000;

    /** 错误阈值（字符数） */
    private static final int ERROR_THRESHOLD = 100_000;

    /** 自动压缩阈值（字符数） */
    private static final int AUTOCOMPACT_THRESHOLD = 120_000;

    private final ConversationSession conversationSession;

    public ContextCommandExecutor(ConversationSession conversationSession) {
        this.conversationSession = conversationSession;
    }

    @Override
    public SlashCommandResult execute(String sessionId, String args) {
        try {
            log.info("执行 /context 命令，sessionId={}", sessionId);

            // 获取当前历史消息
            List<ChatMessage> history = conversationSession.getMessages();

            // 转换为 Spring AI Message 格式并估算大小
            List<Message> messages = convertToSpringAiMessages(history);
            int totalChars = MessageTextEstimator.estimateChars(messages);

            // 统计各类型消息
            int userCount = 0, assistantCount = 0, systemCount = 0, toolCount = 0;
            int maxMessageChars = 0;
            String longestMessagePreview = "";

            for (ChatMessage msg : history) {
                switch (msg.type()) {
                    case USER -> userCount++;
                    case ASSISTANT -> assistantCount++;
                    case SYSTEM -> systemCount++;
                    case TOOL -> toolCount++;
                }
                int msgLen = msg.content() != null ? msg.content().length() : 0;
                if (msgLen > maxMessageChars) {
                    maxMessageChars = msgLen;
                    longestMessagePreview = truncate(msg.content(), 100);
                }
            }

            // 计算使用率
            double usagePercent = (totalChars * 100.0) / AUTOCOMPACT_THRESHOLD;
            String statusEmoji = getStatusEmoji(totalChars);
            String progressBar = createProgressBar(usagePercent);

            // 构建结果显示
            StringBuilder result = new StringBuilder();
            result.append("┌─ 上下文使用统计 ────────────────────────────┐\n");
            result.append("│ ").append(statusEmoji).append(" 当前状态：").append(getStatusText(totalChars)).append("\n");
            result.append("│\n");
            result.append("│ 总字符数：").append(String.format("%,d", totalChars)).append(" / ").append(String.format("%,d", AUTOCOMPACT_THRESHOLD)).append("\n");
            result.append("│ 使用率：").append(String.format("%.1f%%", usagePercent)).append(" ").append(progressBar).append("\n");
            result.append("│\n");
            result.append("│ 消息分布：\n");
            result.append("│   👤 用户消息：").append(userCount).append("\n");
            result.append("│   🤖 助手回复：").append(assistantCount).append("\n");
            result.append("│   ⚙️ 系统消息：").append(systemCount).append("\n");
            result.append("│   🔧 工具调用：").append(toolCount).append("\n");
            result.append("│\n");
            result.append("│ 最长消息：").append(maxMessageChars).append(" 字符\n");
            if (!longestMessagePreview.isEmpty()) {
                result.append("│   \"").append(longestMessagePreview).append("\"\n");
            }
            result.append("└─────────────────────────────────────────────┘\n\n");

            // 添加建议
            result.append(getSuggestions(totalChars));

            return SlashCommandResult.Text.of(result.toString());

        } catch (Exception e) {
            log.error("/context 命令执行失败", e);
            return SlashCommandResult.Error.of("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取状态 Emoji
     */
    private String getStatusEmoji(int totalChars) {
        if (totalChars < WARNING_THRESHOLD) {
            return "🟢";
        } else if (totalChars < ERROR_THRESHOLD) {
            return "🟡";
        } else if (totalChars < AUTOCOMPACT_THRESHOLD) {
            return "🟠";
        } else {
            return "🔴";
        }
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(int totalChars) {
        if (totalChars < WARNING_THRESHOLD) {
            return "健康";
        } else if (totalChars < ERROR_THRESHOLD) {
            return "注意";
        } else if (totalChars < AUTOCOMPACT_THRESHOLD) {
            return "即将超限";
        } else {
            return "已超阈值 - 建议执行 /compact";
        }
    }

    /**
     * 创建进度条
     */
    private String createProgressBar(double percent) {
        int totalBars = 20;
        int filledBars = (int) (percent / 100 * totalBars);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("]");
        return bar.toString();
    }

    /**
     * 获取建议
     */
    private String getSuggestions(int totalChars) {
        StringBuilder suggestions = new StringBuilder();

        if (totalChars < WARNING_THRESHOLD) {
            suggestions.append("💡 提示：上下文使用正常，继续交流即可。\n");
        } else if (totalChars < ERROR_THRESHOLD) {
            suggestions.append("💡 提示：上下文使用接近警告线，请注意控制对话长度。\n");
            suggestions.append("   可使用 /compact 命令主动压缩上下文。\n");
        } else if (totalChars < AUTOCOMPACT_THRESHOLD) {
            suggestions.append("⚠️ 警告：上下文即将超过阈值！\n");
            suggestions.append("   建议立即执行 /compact 命令压缩上下文。\n");
            suggestions.append("   或使用 /clear 清除当前会话。\n");
        } else {
            suggestions.append("🔴 警告：上下文已超过阈值！\n");
            suggestions.append("   请立即执行 /compact 命令或 /clear 清除会话。\n");
            suggestions.append("   否则可能影响后续对话质量。\n");
        }

        return suggestions.toString();
    }

    /**
     * 转换 ChatMessage 为 Spring AI Message
     */
    private List<Message> convertToSpringAiMessages(List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            String content = msg.content() != null ? msg.content() : "";
            switch (msg.type()) {
                case USER -> messages.add(new org.springframework.ai.chat.messages.UserMessage(content));
                case ASSISTANT -> messages.add(new org.springframework.ai.chat.messages.AssistantMessage(content));
                case SYSTEM -> messages.add(new org.springframework.ai.chat.messages.SystemMessage(content));
                case TOOL -> {
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
