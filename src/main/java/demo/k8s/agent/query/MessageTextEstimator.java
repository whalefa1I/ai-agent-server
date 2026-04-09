package demo.k8s.agent.query;

import java.util.List;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 粗略估算上下文体积（字符数），用于 autocompact 阈值；非精确 token。
 */
public final class MessageTextEstimator {

    private MessageTextEstimator() {}

    public static int estimateChars(Iterable<Message> messages) {
        int n = 0;
        for (Message m : messages) {
            n += estimateOne(m);
        }
        return n;
    }

    public static int estimateOne(Message m) {
        if (m instanceof ToolResponseMessage) {
            ToolResponseMessage trm = (ToolResponseMessage) m;
            return trm.getResponses().stream()
                    .mapToInt(r -> r.responseData() != null ? r.responseData().length() : 0)
                    .sum();
        }
        if (m instanceof AssistantMessage) {
            AssistantMessage am = (AssistantMessage) m;
            int t = textLen(textOf(am));
            if (am.getToolCalls() != null) {
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    t += tc.arguments() != null ? tc.arguments().length() : 0;
                }
            }
            return t;
        }
        if (m instanceof AbstractMessage) {
            AbstractMessage am = (AbstractMessage) m;
            return textLen(textOf(am));
        }
        return 0;
    }

    private static String textOf(AbstractMessage am) {
        String t = am.getText();
        return t != null ? t : "";
    }

    private static int textLen(String s) {
        return s == null ? 0 : s.length();
    }

    /** 供 autocompact 子调用：拼接正文，总长封顶避免摘要请求本身爆窗 */
    public static String bodiesForSummary(List<Message> messages, int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append("[").append(m.getClass().getSimpleName()).append("]\n");
            if (m instanceof ToolResponseMessage) {
                ToolResponseMessage trm = (ToolResponseMessage) m;
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    String d = r.responseData();
                    sb.append(r.name()).append(": ").append(d != null ? d : "").append('\n');
                }
            } else if (m instanceof AbstractMessage) {
                AbstractMessage am = (AbstractMessage) m;
                sb.append(textOf(am));
            }
            sb.append("\n---\n");
            if (sb.length() >= maxChars) {
                sb.append("\n...[capped for summarization prompt]\n");
                break;
            }
        }
        return sb.toString();
    }
}
