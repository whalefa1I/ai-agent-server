package demo.k8s.agent.query;

import demo.k8s.agent.config.DemoQueryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强版上下文压缩管道 - 对齐 Claude Code 的多档压缩策略
 * <p>
 * 压缩层级：
 * - Tier 1 (microcompact): 截断过长 ToolResponseMessage（始终执行）
 * - Tier 2 (time-based): 时间触发压缩 - 检测到长时间空闲后清除旧工具结果
 * - Tier 3 (autocompact): LLM 摘要 - 当上下文使用超过阈值时生成摘要
 * <p>
 * 配置项（application.yml）：
 * <pre>{@code
 * demo.query:
 *   microcompact-max-chars-per-tool-response: 24000  # Tier 1 阈值
 *   time-based-compaction-gap-minutes: 30            # Tier 2 空闲阈值（分钟）
 *   time-based-compaction-keep-recent: 3             # Tier 2 保留最近 N 个工具结果
 *   full-compact-threshold-chars: 120000             # Tier 3 阈值
 *   full-compact-enabled: false                       # Tier 3 开关
 * }</pre>
 */
@Component
public class EnhancedCompactionPipeline implements CompactionPipeline {

    private static final Logger log = LoggerFactory.getLogger(EnhancedCompactionPipeline.class);

    /** 可压缩的工具类型（与 Claude Code 对齐） */
    private static final Set<String> COMPACTABLE_TOOLS = Set.of(
            "file_read",
            "file_write",
            "file_edit",
            "glob",
            "grep",
            "bash",
            "web_search",
            "web_fetch"
    );

    /** 时间触发压缩后的清除标记 */
    private static final String TIME_BASED_CLEARED_MESSAGE = "[Old tool result content cleared - time-based compaction]";

    private final DemoQueryProperties props;
    private final ChatModel chatModel;

    /** 记录每个工具结果的时间戳（用于 time-based 压缩） */
    private final ConcurrentHashMap<String, Instant> toolResultTimestamps = new ConcurrentHashMap<>();

    public EnhancedCompactionPipeline(DemoQueryProperties props, ChatModel chatModel) {
        this.props = props;
        this.chatModel = chatModel;
    }

    @Override
    public List<Message> compactBeforeModelCall(List<Message> messages) {
        log.debug("[Compaction] Starting compaction pipeline with {} messages", messages.size());

        // Tier 1: microcompact（截断过长 tool result）
        List<Message> tier1Result = microcompact(messages);
        log.debug("[Compaction] Tier 1 (microcompact) completed: {} messages", tier1Result.size());

        // Tier 2: time-based compaction（时间触发）
        List<Message> tier2Result = maybeTimeBasedCompaction(tier1Result);
        if (tier2Result != tier1Result) {
            log.info("[Compaction] Tier 2 (time-based) triggered - cleared old tool results");
        }

        // Tier 3: autocompact（LLM 摘要）
        if (props.isFullCompactEnabled()) {
            int totalChars = MessageTextEstimator.estimateChars(tier2Result);
            if (totalChars >= props.getFullCompactThresholdChars()) {
                log.info("[Compaction] Tier 3 (autocompact) triggered: {} chars >= {} threshold",
                        totalChars, props.getFullCompactThresholdChars());
                return autocompactSummarize(tier2Result, totalChars);
            }
        }

        return tier2Result;
    }

    /**
     * Tier 1: microcompact - 截断过长的 ToolResponseMessage
     * <p>
     * 对齐 Claude Code 的 microCompactMessages，但简化为字符截断
     */
    private List<Message> microcompact(List<Message> messages) {
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

    /**
     * 截断单个 ToolResponseMessage
     */
    private ToolResponseMessage truncateToolResponse(ToolResponseMessage trm, int maxChars) {
        List<ToolResponseMessage.ToolResponse> truncatedResponses = new ArrayList<>();

        for (ToolResponseMessage.ToolResponse response : trm.getResponses()) {
            String data = response.responseData();
            if (data != null && data.length() > maxChars) {
                String truncated = data.substring(0, maxChars) + "\n...[truncated - exceeds microcompact limit]";
                truncatedResponses.add(new ToolResponseMessage.ToolResponse(
                        response.name(), response.id(), truncated));
                log.debug("[Microcompact] Truncated tool '{}' from {} to {} chars",
                        response.name(), data.length(), maxChars);
            } else {
                truncatedResponses.add(response);
            }
        }

        return ToolResponseMessage.builder()
                .responses(truncatedResponses)
                .build();
    }

    /**
     * Tier 2: time-based compaction - 时间触发压缩
     * <p>
     * 对齐 Claude Code 的 timeBasedMicrocompact：
     * - 检测到长时间空闲（默认 30 分钟）后清除旧工具结果
     * - 保留最近 N 个工具结果（默认 3 个）
     * <p>
     * 触发条件：
     * 1. 距离最后一条 assistant 消息超过配置的空闲阈值
     * 2. 有可压缩的工具结果
     */
    private List<Message> maybeTimeBasedCompaction(List<Message> messages) {
        // 检查是否启用了时间触发压缩
        int gapMinutes = props.getTimeBasedCompactionGapMinutes();
        if (gapMinutes <= 0) {
            return messages; // 禁用
        }

        // 找到最后一条 assistant 消息
        Message lastAssistant = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getClass().getSimpleName().contains("Assistant")) {
                lastAssistant = messages.get(i);
                break;
            }
        }

        if (lastAssistant == null) {
            return messages; // 没有 assistant 消息，不需要压缩
        }

        // 计算空闲时间
        Instant lastActivityTime = extractTimestamp(lastAssistant);
        if (lastActivityTime == null) {
            return messages; // 无法提取时间戳
        }

        Duration gap = Duration.between(lastActivityTime, Instant.now());
        long gapMinutesActual = gap.toMinutes();

        if (gapMinutesActual < gapMinutes) {
            return messages; // 空闲时间未达阈值
        }

        log.info("[TimeBasedCompaction] Gap detected: {} minutes >= {} threshold",
                gapMinutesActual, gapMinutes);

        // 收集所有可压缩的工具 ID
        List<String> compactableToolIds = collectCompactableToolIds(messages);
        if (compactableToolIds.isEmpty()) {
            return messages; // 没有可压缩的工具
        }

        // 保留最近 N 个工具结果
        int keepRecent = Math.max(1, props.getTimeBasedCompactionKeepRecent());
        List<String> toolsToKeep = compactableToolIds.subList(
                Math.max(0, compactableToolIds.size() - keepRecent),
                compactableToolIds.size()
        );
        Set<String> keepSet = Set.copyOf(toolsToKeep);

        // 清除旧的工具结果
        int clearedCount = 0;
        List<Message> result = new ArrayList<>();

        for (Message m : messages) {
            if (m instanceof ToolResponseMessage trm) {
                ToolResponseMessage cleared = clearToolResponse(trm, keepSet);
                if (cleared != trm) {
                    clearedCount++;
                }
                result.add(cleared);
            } else {
                result.add(m);
            }
        }

        if (clearedCount > 0) {
            log.info("[TimeBasedCompaction] Cleared {} old tool results, kept {} recent",
                    clearedCount, keepSet.size());
        }

        return result;
    }

    /**
     * 从消息中提取时间戳
     */
    private Instant extractTimestamp(Message message) {
        // 尝试从 message 的元数据中提取时间戳
        // Spring AI Message 没有标准的时间戳字段，需要从子类中提取
        try {
            // 尝试反射获取 timestamp 字段
            var timestampField = message.getClass().getDeclaredField("timestamp");
            timestampField.setAccessible(true);
            Object timestampObj = timestampField.get(message);
            if (timestampObj instanceof Instant instant) {
                return instant;
            }
            if (timestampObj instanceof String timestampStr) {
                return Instant.parse(timestampStr);
            }
        } catch (Exception e) {
            // 忽略，返回 null
        }
        return Instant.now(); // 默认返回当前时间
    }

    /**
     * 收集所有可压缩的工具 ID
     */
    private List<String> collectCompactableToolIds(List<Message> messages) {
        List<String> ids = new ArrayList<>();
        for (Message m : messages) {
            if (m instanceof ToolResponseMessage trm) {
                for (var response : trm.getResponses()) {
                    String toolName = response.name();
                    if (COMPACTABLE_TOOLS.contains(toolName)) {
                        ids.add(response.id());
                    }
                }
            }
        }
        return ids;
    }

    /**
     * 清除工具结果（保留指定的工具）
     */
    private ToolResponseMessage clearToolResponse(ToolResponseMessage trm, Set<String> keepSet) {
        List<ToolResponseMessage.ToolResponse> clearedResponses = new ArrayList<>();
        boolean modified = false;

        for (ToolResponseMessage.ToolResponse response : trm.getResponses()) {
            if (!keepSet.contains(response.id())) {
                // 清除这个工具结果
                clearedResponses.add(new ToolResponseMessage.ToolResponse(
                        response.name(), response.id(), TIME_BASED_CLEARED_MESSAGE));
                modified = true;
            } else {
                clearedResponses.add(response);
            }
        }

        if (!modified) {
            return trm;
        }

        return ToolResponseMessage.builder()
                .responses(clearedResponses)
                .build();
    }

    /**
     * Tier 3: autocompact - LLM 摘要
     * <p>
     * 对齐 Claude Code 的 autoCompact：
     * - 当上下文使用超过阈值时，调用 LLM 生成摘要
     * - 用摘要替换历史对话，保留关键信息
     */
    private List<Message> autocompactSummarize(List<Message> messages, int totalChars) {
        try {
            // 提取对话内容用于摘要
            String transcript = MessageTextEstimator.bodiesForSummary(messages, 80_000);

            String userPrompt = String.format(
                    "下列对话片段体积约 %d 字符。请用中文写一段结构化摘要（保留关键事实、文件路径、错误信息、未决问题），不超过 3000 字。\n\n" +
                    "对话片段：\n%s",
                    totalChars, transcript);

            Prompt prompt = new Prompt(
                    List.of(
                            new org.springframework.ai.chat.messages.SystemMessage(
                                    "你是上下文压缩助手。请生成简洁的对话摘要，保留所有关键信息。只输出摘要正文，不要调用工具，不要添加额外说明。"),
                            new org.springframework.ai.chat.messages.UserMessage(userPrompt)),
                    OpenAiChatOptions.builder()
                            .temperature(0.2)
                            .maxTokens(4000)
                            .build());

            log.info("[AutoCompact] Calling LLM to generate summary...");
            var compactResp = chatModel.call(prompt);

            String summary = "";
            if (compactResp != null && compactResp.getResult() != null
                    && compactResp.getResult().getOutput() != null) {
                summary = compactResp.getResult().getOutput().getText();
                if (summary == null) summary = "";
            }

            log.info("[AutoCompact] Summary generated: {} chars", summary.length());

            // 构建压缩后的消息列表
            List<Message> compactedMessages = new ArrayList<>();
            compactedMessages.add(new org.springframework.ai.chat.messages.SystemMessage(
                    "[上下文已压缩] 此前对话已压缩为以下摘要。后续推理请仅依赖此摘要与最新用户消息。\n\n" + summary));

            // 保留最后一条用户消息（如果有）
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (m.getClass().getSimpleName().contains("User")) {
                    compactedMessages.add(m);
                    break;
                }
            }

            log.info("[AutoCompact] Compaction complete: {} messages -> {} messages",
                    messages.size(), compactedMessages.size());

            return compactedMessages;

        } catch (Exception e) {
            log.error("[AutoCompact] Summary generation failed: {}", e.getMessage(), e);
            // 摘要失败时返回原始消息（降级处理）
            return messages;
        }
    }
}
