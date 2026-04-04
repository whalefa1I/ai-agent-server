package demo.k8s.agent.memory.store;

import demo.k8s.agent.memory.model.MemoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量存储实现 - 简单快速的开发/测试用存储
 * <p>
 * 注意：重启后数据会丢失，适用于开发和测试场景
 */
@Service
public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        log.info("InMemoryVectorStore initialized (empty store)");
    }

    @Override
    public void add(MemoryEntry entry) {
        entries.put(entry.getId(), entry);
        log.debug("Added memory entry: {}", entry.getId());
    }

    @Override
    public void addAll(List<MemoryEntry> entries) {
        entries.forEach(this::add);
        log.info("Added {} memory entries", entries.size());
    }

    @Override
    public Optional<MemoryEntry> getById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public List<MemoryEntry> searchBySimilarity(float[] queryEmbedding, int maxResults) {
        return searchWithScore(queryEmbedding, maxResults)
                .stream()
                .map(SearchResult::entry)
                .toList();
    }

    @Override
    public List<SearchResult> searchWithScore(float[] queryEmbedding, int maxResults) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }

        List<SearchResult> results = entries.values().stream()
                .filter(entry -> entry.getEmbedding() != null && entry.getEmbedding().length > 0)
                .map(entry -> {
                    double score = cosineSimilarity(queryEmbedding, entry.getEmbedding());
                    return new SearchResult(entry, score);
                })
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(maxResults)
                .toList();

        log.debug("Similarity search returned {} results", results.size());
        return results;
    }

    @Override
    public boolean delete(String id) {
        boolean removed = entries.remove(id) != null;
        if (removed) {
            log.debug("Deleted memory entry: {}", id);
        }
        return removed;
    }

    @Override
    public void deleteBySession(String sessionId) {
        entries.values().removeIf(entry -> sessionId.equals(entry.getSessionId()));
        log.info("Deleted all memories for session: {}", sessionId);
    }

    @Override
    public long count() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
        log.info("Cleared all memory entries");
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 余弦相似度（-1 到 1 之间，1 表示完全相同）
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match: " + a.length + " vs " + b.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
