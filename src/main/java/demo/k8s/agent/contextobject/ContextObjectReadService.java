package demo.k8s.agent.contextobject;

import demo.k8s.agent.config.DemoContextObjectProperties;
import demo.k8s.agent.observability.tracing.TraceContext;
import demo.k8s.agent.subagent.metrics.SubagentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 读取外置上下文对象：会话与租户由 {@link TraceContext} 与配置隐式注入，不信任模型传入的”当前会话”。
 */
@Service
public class ContextObjectReadService {

    private static final Logger log = LoggerFactory.getLogger(ContextObjectReadService.class);

    private final ContextObjectRepository repository;
    private final DemoContextObjectProperties props;
    private final SubagentMetrics metrics;

    public ContextObjectReadService(ContextObjectRepository repository,
                                    DemoContextObjectProperties props,
                                    SubagentMetrics metrics) {
        this.repository = repository;
        this.props = props;
        this.metrics = metrics;
    }

    /**
     * @param objectId   模型仅允许传业务 id（如 ctx-obj-…）
     * @param charOffset 正文起始字符偏移（可选分片）
     * @param charLimit  最大返回字符数，null 时使用配置 readMaxChars
     */
    public String read(String objectId, Integer charOffset, Integer charLimit) {
        if (!props.isEnabled()) {
            return "SYSTEM: Context object store reads are disabled. DO NOT fabricate content for this ID.";
        }
        if (objectId == null || objectId.isBlank()) {
            return "SYSTEM ERROR: id is required. DO NOT guess object contents.";
        }
        String conversationId = TraceContext.getSessionId();
        if (conversationId == null || conversationId.isBlank()) {
            return "SYSTEM ERROR: No active session context. Cannot resolve context object. DO NOT guess content.";
        }
        // v1 M2: 优先从 TraceContext 获取租户 ID，配置仅作为兜底
        String tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = props.getDefaultTenantId();
        }

        var row = repository.findByIdAndConversationIdAndTenantId(objectId.trim(), conversationId, tenantId);
        if (row.isEmpty()) {
            log.debug("context_object miss or forbidden: id={}, conversationId={}, tenantId={}", objectId, conversationId, tenantId);
            return "SYSTEM ERROR: Context object not found or you do not have permission to read it. "
                    + "It may belong to another session or have expired. DO NOT invent its contents.";
        }
        ContextObject obj = row.get();
        if (obj.getExpiresAt().isBefore(Instant.now())) {
            return "SYSTEM ERROR: Context object has expired and was purged. DO NOT guess its former contents.";
        }
        if (obj.getTokenEstimate() > props.getReadMaxTokenEstimate()) {
            return "SYSTEM ERROR: This context object is too large (estimated " + obj.getTokenEstimate()
                    + " tokens) for a single read under current limits. "
                    + "Use offset/limit parameters to read a slice, or request a smaller export.";
        }
        if (obj.getStorageUri() != null && !obj.getStorageUri().isBlank()
                && (obj.getContent() == null || obj.getContent().isEmpty())) {
            return "SYSTEM ERROR: Large object is stored externally (" + obj.getStorageUri()
                    + "). Inline retrieval is not implemented in this build.";
        }
        String body = obj.getContent() != null ? obj.getContent() : "";
        int offset = Math.max(0, charOffset != null ? charOffset : 0);
        int maxLen = charLimit != null ? Math.max(1, charLimit) : props.getReadMaxChars();
        if (offset > 0 && offset >= body.length()) {
            return "SYSTEM ERROR: offset (" + offset + ") is past end of content (length " + body.length() + ").";
        }
        int end = Math.min(body.length(), offset + maxLen);
        String slice = body.substring(offset, end);
        boolean truncated = end < body.length();
        StringBuilder out = new StringBuilder(slice.length() + 128);
        if (offset > 0 || truncated) {
            out.append("[Partial read: offset=").append(offset)
                    .append(", returnedChars=").append(slice.length())
                    .append(", totalChars=").append(body.length()).append("]\n");
        }
        out.append(slice);
        if (truncated) {
            out.append("\n[TRUNCATED: use read_context_object with offset=").append(end)
                    .append(" to continue.]");
        }
        String result = out.toString();
        // 记录读取字节数指标
        metrics.recordContextObjectRead(result.length());
        return result;
    }
}
