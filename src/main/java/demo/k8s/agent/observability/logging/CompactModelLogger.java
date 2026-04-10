package demo.k8s.agent.observability.logging;

import demo.k8s.agent.config.DemoDebugProperties;
import org.slf4j.Logger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 精简版模型调用日志 - 只打印当前轮次关键信息，避免日志爆炸
 */
public final class CompactModelLogger {

    private CompactModelLogger() {}

    /**
     * 只打印当前轮次的关键信息（用户输入、工具调用意图）
     */
    public static void logTurnSummary(
            Logger log,
            DemoDebugProperties debug,
            List<Message> messages,
            int turnIndex) {

        if (debug == null || !debug.isLogModelRequest()) {
            return;
        }

        // 只找最后一条用户消息和助手消息
        Message lastUser = null;
        Message lastAssistant = null;
        
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (lastUser == null && m instanceof UserMessage) {
                lastUser = m;
            }
            if (lastAssistant == null && m instanceof AssistantMessage) {
                lastAssistant = m;
            }
            if (lastUser != null && lastAssistant != null) break;
        }

        // 打印用户输入摘要（前100字符）
        if (lastUser != null) {
            String text = ((UserMessage) lastUser).getText();
            log.info("[Turn-{}] User: {}", turnIndex, truncate(text, 100));
        }

        // 打印助手工具调用意图
        if (lastAssistant instanceof AssistantMessage asm) {
            if (asm.getToolCalls() != null && !asm.getToolCalls().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (AssistantMessage.ToolCall tc : asm.getToolCalls()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(tc.name());
                    // 特别标记 spawn_subagent
                    if ("spawn_subagent".equals(tc.name())) {
                        sb.append(" [SPAWN]");
                    }
                }
                log.info("[Turn-{}] Assistant will call: {}", turnIndex, sb);
            } else {
                String text = asm.getText();
                log.info("[Turn-{}] Assistant response: {}", turnIndex, truncate(text, 80));
            }
        }
    }

    /**
     * 打印工具执行结果摘要
     */
    public static void logToolResult(
            Logger log,
            String toolName,
            String result,
            boolean success) {
        
        String status = success ? "✓" : "✗";
        String preview = truncate(result, 60);
        
        if ("spawn_subagent".equals(toolName)) {
            // 特别高亮 spawn_subagent 结果
            log.info("[SPAWN-RESULT] {} {} | {}", status, toolName, preview);
        } else {
            log.info("[Tool-RESULT] {} {} | {}", status, toolName, preview);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
