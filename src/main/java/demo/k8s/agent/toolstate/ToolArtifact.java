package demo.k8s.agent.toolstate;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * ToolArtifact - 工具状态持久化实体
 *
 * 参考 happy-server 的 Artifact 设计，支持：
 * - header/body 分离，各自独立版本控制
 * - 乐观并发控制（通过 version 字段）
 * - 加密存储（header/body 存储加密后的 JSON）
 */
@Entity
@Table(name = "tool_artifact", indexes = {
    @Index(name = "idx_tool_account", columnList = "accountId"),
    @Index(name = "idx_tool_session", columnList = "sessionId"),
    @Index(name = "idx_tool_updated", columnList = "accountId, updatedAt DESC")
})
public class ToolArtifact {

    @Id
    private String id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String accountId;

    /**
     * Header: 工具元数据
     * JSON 格式：{"name": "BashTool", "type": "tool", "status": "todo|executing|completed"}
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String header;

    @Column(nullable = false)
    private int headerVersion = 0;

    /**
     * Body: 工具状态详情
     * JSON 格式根据状态不同而变化：
     * - todo: {"todo": "description"}
     * - plan: {"plan": ["step1", "step2"]}
     * - pending_confirmation: {"input": {...}, "confirmation": {"requested": true}}
     * - executing: {"input": {...}, "progress": "running"}
     * - completed: {"input": {...}, "output": {...}}
     * - failed: {"input": {...}, "error": "message"}
     */
    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private int bodyVersion = 0;

    @Column(nullable = false)
    private long seq = 0;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }

    public int getHeaderVersion() { return headerVersion; }
    public void setHeaderVersion(int headerVersion) { this.headerVersion = headerVersion; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public int getBodyVersion() { return bodyVersion; }
    public void setBodyVersion(int bodyVersion) { this.bodyVersion = bodyVersion; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
