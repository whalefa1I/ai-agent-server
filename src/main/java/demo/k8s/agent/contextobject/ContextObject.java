package demo.k8s.agent.contextobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 持久化的外置上下文切片：超长 tool 结果或 compaction 外置正文。
 */
@Entity
@Table(name = "context_object")
public class ContextObject {

    @Id
    @Column(length = 96)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "producer_kind", nullable = false, length = 32)
    private ProducerKind producerKind;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType = "text/plain";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "storage_uri", length = 512)
    private String storageUri;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public ProducerKind getProducerKind() {
        return producerKind;
    }

    public void setProducerKind(ProducerKind producerKind) {
        this.producerKind = producerKind;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public void setTokenEstimate(int tokenEstimate) {
        this.tokenEstimate = tokenEstimate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
