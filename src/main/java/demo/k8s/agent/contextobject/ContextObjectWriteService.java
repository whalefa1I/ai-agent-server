package demo.k8s.agent.contextobject;

import demo.k8s.agent.config.DemoContextObjectWriteProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.metrics.SubagentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 写入外置上下文对象：超长 tool 结果外置到 DB，主上下文仅保留存根。
 * <p>
 * 写入失败时返回降级结果，不抛链路级异常，保障主对话继续可用。
 */
@Service
public class ContextObjectWriteService {

    private static final Logger log = LoggerFactory.getLogger(ContextObjectWriteService.class);

    private final ContextObjectRepository repository;
    private final DemoContextObjectWriteProperties props;
    private final SubagentMetrics metrics;

    public ContextObjectWriteService(ContextObjectRepository repository,
                                     DemoContextObjectWriteProperties props,
                                     SubagentMetrics metrics) {
        this.repository = repository;
        this.props = props;
        this.metrics = metrics;
    }

    /**
     * 写入超长工具结果，返回存根 ID 和存根文本。
     *
     * @param toolName      工具名称
     * @param rawContent    原始工具结果
     * @param tokenEstimate 估算 token 数
     * @param producerKind  生产者类型
     * @param runId         可选的 runId（子 Agent 场景）
     * @return 写入结果（成功时返回存根 ID 和文本，失败时返回降级片段）
     */
    public WriteResult write(String toolName, String rawContent, int tokenEstimate,
                             ProducerKind producerKind, String runId) {
        if (!props.isWriteEnabled()) {
            log.debug("[ContextObjectWrite] Store disabled, returning fallback for tool={}", toolName);
            return buildFallback(rawContent, toolName, "Context object store is disabled");
        }

        if (rawContent == null || rawContent.isEmpty()) {
            log.debug("[ContextObjectWrite] Empty content for tool={}", toolName);
            return new WriteResult(null, "", true, null);
        }

        // 检查是否超过写入阈值
        if (rawContent.length() < props.getWriteThresholdChars()) {
            log.debug("[ContextObjectWrite] Content {} chars < threshold {}, skipping write",
                    rawContent.length(), props.getWriteThresholdChars());
            return new WriteResult(null, rawContent, true, null);
        }

        // 构建存根 ID
        String objectId = "ctx-obj-" + UUID.randomUUID().toString().substring(0, 24);
        String stubText = "[" + objectId + "]";

        try {
            ContextObject contextObject = new ContextObject();
            contextObject.setId(objectId);
            contextObject.setTenantId(getTenantId());
            contextObject.setConversationId(getConversationId());
            contextObject.setRunId(runId);
            contextObject.setProducerKind(producerKind);
            contextObject.setToolName(toolName);
            contextObject.setContentType("text/plain");
            contextObject.setContent(rawContent);
            contextObject.setTokenEstimate(tokenEstimate);
            contextObject.setCreatedAt(Instant.now());
            contextObject.setExpiresAt(Instant.now().plusSeconds(props.getDefaultTtlHours() * 3600L));

            repository.save(contextObject);
            log.info("[ContextObjectWrite] Saved context object: id={}, tool={}, chars={}, tokens={}",
                    objectId, toolName, rawContent.length(), tokenEstimate);

            return new WriteResult(objectId, stubText, true, null);

        } catch (Exception e) {
            log.error("[ContextObjectWrite] Failed to save context object: tool={}, error={}",
                    toolName, e.getMessage(), e);
            // 记录指标
            metrics.recordContextObjectWriteFailure();
            // 写入失败时返回降级片段
            return buildFallback(rawContent, toolName, e.getMessage());
        }
    }

    /**
     * 构建降级结果：当写入失败时，返回头尾截断的片段 + SYSTEM 标记。
     */
    private WriteResult buildFallback(String rawContent, String toolName, String failureReason) {
        int headChars = props.getFallbackHeadChars();
        int tailChars = props.getFallbackTailChars();

        String fallbackText;
        if (rawContent.length() <= headChars + tailChars) {
            fallbackText = rawContent;
        } else {
            String head = rawContent.substring(0, headChars);
            String tail = rawContent.substring(rawContent.length() - tailChars);
            fallbackText = String.format(
                    "[SYSTEM: Context object write failed (%s). The following is a forcibly truncated fragment of the tool result for '%s'. Full content was not persisted.]\n%s\n...[truncated - write failure fallback]...\n%s",
                    failureReason, toolName, head, tail
            );
        }

        log.warn("[ContextObjectWrite] Fallback applied for tool={}: {} chars preserved (head={}, tail={})",
                toolName, fallbackText.length(), headChars, tailChars);

        return new WriteResult(null, fallbackText, false, fallbackText);
    }

    /**
     * 从 TraceContext 获取会话 ID。
     */
    private String getConversationId() {
        String sessionId = TraceContext.getSessionId();
        return sessionId != null ? sessionId : "unknown-session";
    }

    /**
     * 从 TraceContext 获取租户 ID（v1 M2：优先从上下文获取，配置仅作为兜底）。
     */
    private String getTenantId() {
        String tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = props.getDefaultTenantId();
        }
        return tenantId;
    }

    /**
     * 写入结果。
     */
    public record WriteResult(
            String objectId,
            String stubText,
            boolean success,
            String fallbackContent
    ) {
    }
}
