package demo.k8s.agent.memory.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 记忆条目 - 存储在向量数据库中的基本单元
 */
public class MemoryEntry {

    private final String id;
    private final String content;
    private final float[] embedding;
    private final MemorySource source;
    private final String sessionId;
    private final String userId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Map<String, Object> metadata;

    public enum MemorySource {
        /**
         * 来自对话历史
         */
        CONVERSATION,
        /**
         * 来自文件快照
         */
        FILE,
        /**
         * 用户显式添加的记忆笔记
         */
        USER_NOTE,
        /**
         * 来自 MEMORY.md 文件
         */
        MEMORY_FILE
    }

    private MemoryEntry(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.content = Objects.requireNonNull(builder.content, "content");
        this.embedding = builder.embedding;
        this.source = builder.source != null ? builder.source : MemorySource.CONVERSATION;
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public MemorySource getSource() {
        return source;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 获取记忆内容的摘要（前 100 字符）
     */
    public String getSummary() {
        if (content.length() <= 100) {
            return content;
        }
        return content.substring(0, 100) + "...";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String content;
        private float[] embedding;
        private MemorySource source;
        private String sessionId;
        private String userId;
        private Instant createdAt;
        private Instant updatedAt;
        private Map<String, Object> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder embedding(float[] embedding) {
            this.embedding = embedding;
            return this;
        }

        public Builder source(MemorySource source) {
            this.source = source;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public MemoryEntry build() {
            return new MemoryEntry(this);
        }
    }
}
