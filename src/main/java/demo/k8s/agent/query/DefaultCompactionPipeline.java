package demo.k8s.agent.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import demo.k8s.agent.config.DemoQueryProperties;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 对齐 Zread「Context Compression」三档中的可落地子集：
 * <ul>
 *   <li>Tier1 microcompact：截断过长 {@link ToolResponseMessage}（类比 {@code microcompactMessages}）</li>
 *   <li>Tier3 可选 autocompact：总字符超阈值时用一次额外模型调用生成摘要并替换历史（简化版，无 boundary marker / 附件恢复）</li>
 * </ul>
 */
@Component
public class DefaultCompactionPipeline implements CompactionPipeline {

    private final DemoQueryProperties props;
    private final ChatModel chatModel;

    public DefaultCompactionPipeline(DemoQueryProperties props, ChatModel chatModel) {
        this.props = props;
        this.chatModel = chatModel;
    }

    @Override
    public List<Message> compactBeforeModelCall(List<Message> messages) {
        List<Message> tier1 = microcompact(messages);
        if (!props.isFullCompactEnabled()) {
            return tier1;
        }
        int total = MessageTextEstimator.estimateChars(tier1);
        if (total < props.getFullCompactThresholdChars()) {
            return tier1;
        }
        return autocompactSummarize(tier1, total);
    }

    private List<Message> microcompact(List<Message> messages) {
        // 截断过长的 ToolResponseMessage
        int maxChars = props.getMicrocompactMaxCharsPerToolResponse();
        List<Message> result = new ArrayList<>();

        for (Message m : messages) {
            if (m instanceof ToolResponseMessage trm) {
                ToolResponseMessage truncated = truncateToolResponse(trm, maxChars);
                result.add(truncated);
            } else {
                result.add(m);
            }
        }

        return result;
    }

    private ToolResponseMessage truncateToolResponse(ToolResponseMessage trm, int maxChars) {
        List<ToolResponseMessage.ToolResponse> truncatedResponses = new ArrayList<>();

        for (ToolResponseMessage.ToolResponse response : trm.getResponses()) {
            String data = response.responseData();
            if (data != null && data.length() > maxChars) {
                // 截断并添加省略号
                String truncated = data.substring(0, maxChars) + "\n...[truncated]";
                truncatedResponses.add(new ToolResponseMessage.ToolResponse(
                        response.name(), response.id(), truncated));
            } else {
                truncatedResponses.add(response);
            }
        }

        return ToolResponseMessage.builder()
                .responses(truncatedResponses)
                .build();
    }

    private List<Message> autocompactSummarize(List<Message> messages, int totalChars) {
        String transcript = MessageTextEstimator.bodiesForSummary(messages, 80_000);
        String userPrompt =
                "下列对话片段体积约 "
                        + totalChars
                        + " 字符。请用中文写一段结构化摘要（保留关键事实、文件路径、错误与未决问题），不超过 3000 字。\n"
                        + "片段概览：\n"
                        + transcript;

        Prompt p = new Prompt(
                List.of(
                        new org.springframework.ai.chat.messages.UserMessage(
                                "你是上下文压缩子任务，只输出摘要正文，不要调用工具。"),
                        new org.springframework.ai.chat.messages.UserMessage(userPrompt)),
                OpenAiChatOptions.builder().temperature(0.2d).build());

        org.springframework.ai.chat.model.ChatResponse compactResp = chatModel.call(p);
        String summary = "";
        if (compactResp != null
                && compactResp.getResult() != null
                && compactResp.getResult().getOutput() != null) {
            String t = compactResp.getResult().getOutput().getText();
            summary = t != null ? t : "";
        }
        return List.of(
                new org.springframework.ai.chat.messages.SystemMessage(
                        "此前对话已压缩。以下为摘要，后续轮次请仅依赖摘要与本轮用户消息继续推理。"),
                new org.springframework.ai.chat.messages.UserMessage(summary));
    }
}
