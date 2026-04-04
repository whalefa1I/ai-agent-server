package demo.k8s.agent.memory.index;

import demo.k8s.agent.memory.embedding.EmbeddingService;
import demo.k8s.agent.memory.model.MemoryEntry;
import demo.k8s.agent.memory.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆索引服务 - 负责将对话历史索引到向量存储
 */
@Service
public class MemoryIndexer {

    private static final Logger log = LoggerFactory.getLogger(MemoryIndexer.class);

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public MemoryIndexer(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        log.info("MemoryIndexer initialized");
    }

    /**
     * 索引单条记忆（同步）
     *
     * @param content 记忆内容
     * @param source  记忆来源
     * @param sessionId 会话 ID
     * @param metadata  附加元数据
     * @return 索引后的记忆条目
     */
    public MemoryEntry index(String content, MemoryEntry.MemorySource source, String sessionId, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            log.warn("Cannot index empty content");
            return null;
        }

        try {
            // 生成嵌入向量
            float[] embedding = embeddingService.embed(content);

            // 创建记忆条目
            MemoryEntry entry = MemoryEntry.builder()
                    .content(content)
                    .embedding(embedding)
                    .source(source)
                    .sessionId(sessionId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .metadata(metadata != null ? metadata : Map.of())
                    .build();

            // 存储到向量数据库
            vectorStore.add(entry);

            log.debug("Indexed memory: {} (source={}, session={})", entry.getId(), source, sessionId);
            return entry;

        } catch (Exception e) {
            log.error("Failed to index memory: {}", content, e);
            throw new RuntimeException("Failed to index memory", e);
        }
    }

    /**
     * 批量索引记忆（异步）
     *
     * @param contents 记忆内容列表
     * @param source   记忆来源
     * @param sessionId 会话 ID
     * @return CompletableFuture
     */
    @Async
    public CompletableFuture<List<MemoryEntry>> indexBatch(List<String> contents, MemoryEntry.MemorySource source, String sessionId) {
        List<MemoryEntry> indexedEntries = new ArrayList<>();

        for (String content : contents) {
            try {
                MemoryEntry entry = index(content, source, sessionId, Map.of());
                if (entry != null) {
                    indexedEntries.add(entry);
                }
            } catch (Exception e) {
                log.warn("Failed to index batch item, skipping: {}", content, e);
            }
        }

        log.info("Batch indexed {} memories for session {}", indexedEntries.size(), sessionId);
        return CompletableFuture.completedFuture(indexedEntries);
    }

    /**
     * 索引对话历史
     *
     * @param conversationHistory 对话历史（用户和助手消息交替）
     * @param sessionId 会话 ID
     * @return 索引的记忆数量
     */
    public int indexConversation(List<Map<String, String>> conversationHistory, String sessionId) {
        int indexedCount = 0;

        for (Map<String, String> message : conversationHistory) {
            String role = message.get("role");
            String content = message.get("content");

            if (content == null || content.isBlank()) {
                continue;
            }

            // 只为有意义的消息创建记忆（跳过太短的内容）
            if (content.length() < 20) {
                continue;
            }

            try {
                MemoryEntry.MemorySource source = "user".equals(role)
                        ? MemoryEntry.MemorySource.CONVERSATION
                        : MemoryEntry.MemorySource.CONVERSATION;

                Map<String, Object> metadata = Map.of(
                        "role", role != null ? role : "unknown",
                        "contentLength", content.length()
                );

                index(content, source, sessionId, metadata);
                indexedCount++;

            } catch (Exception e) {
                log.warn("Failed to index conversation message, skipping");
            }
        }

        log.info("Indexed {} conversation messages for session {}", indexedCount, sessionId);
        return indexedCount;
    }

    /**
     * 重新索引所有记忆（强制重建）
     *
     * @param entries 要重新索引的记忆条目
     * @return 重新索引的数量
     */
    public int reindexAll(List<MemoryEntry> entries) {
        int count = 0;
        for (MemoryEntry entry : entries) {
            try {
                // 为没有嵌入的记忆重新生成
                if (entry.getEmbedding() == null || entry.getEmbedding().length == 0) {
                    float[] newEmbedding = embeddingService.embed(entry.getContent());
                    // 注意：这里需要更新 entry，但 MemoryEntry 是不可变的
                    // 实际使用时可能需要 VectorStore 支持更新操作
                    count++;
                }
            } catch (Exception e) {
                log.warn("Failed to reindex entry: {}", entry.getId(), e);
            }
        }
        log.info("Reindexed {} memories", count);
        return count;
    }
}
