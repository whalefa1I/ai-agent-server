package demo.k8s.agent.memory.search;

import demo.k8s.agent.memory.embedding.EmbeddingService;
import demo.k8s.agent.memory.model.MemoryEntry;
import demo.k8s.agent.memory.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆搜索服务 - 提供语义搜索和混合搜索能力
 */
@Service
public class MemorySearchService {

    private static final Logger log = LoggerFactory.getLogger(MemorySearchService.class);

    private static final double DEFAULT_SCORE_THRESHOLD = 0.3; // 最低相似度阈值

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final boolean enabled;

    public MemorySearchService(EmbeddingService embeddingService, VectorStore vectorStore,
                               @Value("${demo.memory.enabled:false}") boolean enabled) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.enabled = enabled;
        log.info("MemorySearchService initialized (enabled={})", enabled);
    }

    /**
     * 语义搜索 - 基于向量相似度
     *
     * @param query      查询文本
     * @param maxResults 最大返回结果数
     * @return 匹配的记忆条目列表
     */
    public List<MemoryEntry> search(String query, int maxResults) {
        return search(query, maxResults, null);
    }

    /**
     * 语义搜索（带过滤器）
     *
     * @param query      查询文本
     * @param maxResults 最大返回结果数
     * @param filter     过滤器（sessionId/source 等）
     * @return 匹配的记忆条目列表
     */
    public List<MemoryEntry> search(String query, int maxResults, SearchFilter filter) {
        if (!enabled) {
            return List.of(); // 记忆系统禁用时直接返回空
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            // 生成查询向量
            float[] queryEmbedding = embeddingService.embed(query);

            // 执行相似度搜索
            List<VectorStore.SearchResult> results = vectorStore.searchWithScore(queryEmbedding, maxResults * 2);

            // 应用过滤器和阈值
            List<MemoryEntry> filteredResults = results.stream()
                    .filter(result -> filter == null || filter.matches(result.entry()))
                    .filter(result -> result.score() >= DEFAULT_SCORE_THRESHOLD)
                    .map(VectorStore.SearchResult::entry)
                    .limit(maxResults)
                    .toList();

            log.debug("Search for '{}' returned {} results (filtered from {})", query, filteredResults.size(), results.size());
            return filteredResults;

        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            return List.of();
        }
    }

    /**
     * 带分数的搜索结果
     *
     * @param query      查询文本
     * @param maxResults 最大返回结果数
     * @return 记忆条目和分数的列表
     */
    public List<SearchResultWithScore> searchWithScore(String query, int maxResults) {
        if (!enabled) {
            return List.of(); // 记忆系统禁用时直接返回空
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            float[] queryEmbedding = embeddingService.embed(query);

            List<VectorStore.SearchResult> results = vectorStore.searchWithScore(queryEmbedding, maxResults);

            return results.stream()
                    .map(r -> new SearchResultWithScore(r.entry(), r.score()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Search with score failed for query: {}", query, e);
            return List.of();
        }
    }

    /**
     * 按会话搜索记忆
     *
     * @param sessionId  会话 ID
     * @param maxResults 最大返回结果数
     * @return 该会话的记忆条目列表
     */
    public List<MemoryEntry> searchBySession(String sessionId, int maxResults) {
        // 内存实现需要遍历所有条目，SQLite 实现可以用 SQL WHERE 优化
        return vectorStore.searchBySimilarity(new float[0], Integer.MAX_VALUE)
                .stream()
                .filter(entry -> sessionId.equals(entry.getSessionId()))
                .limit(maxResults)
                .toList();
    }

    /**
     * 按来源搜索记忆
     *
     * @param source     记忆来源
     * @param maxResults 最大返回结果数
     * @return 匹配来源的记忆条目列表
     */
    public List<MemoryEntry> searchBySource(MemoryEntry.MemorySource source, int maxResults) {
        return vectorStore.searchBySimilarity(new float[0], Integer.MAX_VALUE)
                .stream()
                .filter(entry -> entry.getSource() == source)
                .limit(maxResults)
                .toList();
    }

    /**
     * 获取相关记忆（用于注入到系统提示词）
     * <p>
     * 这个方法会在每次用户消息前调用，返回与当前消息最相关的记忆片段
     *
     * @param currentMessage 当前用户消息
     * @param sessionId      当前会话 ID
     * @return 格式化的记忆片段
     */
    public String getRelevantMemoriesForContext(String currentMessage, String sessionId) {
        if (!enabled) {
            return ""; // 记忆系统禁用时直接返回空
        }
        if (currentMessage == null || currentMessage.isBlank()) {
            return "";
        }

        List<MemoryEntry> memories = search(currentMessage, 5, new SearchFilter(null, sessionId));

        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n<relevant_memories>\n");
        context.append("The following memories from previous conversations may be relevant:\n\n");

        for (int i = 0; i < memories.size(); i++) {
            MemoryEntry m = memories.get(i);
            context.append("[").append(i + 1).append("] ");
            context.append(m.getContent());
            context.append(" (from ").append(m.getSource().name().toLowerCase());
            if (m.getSessionId() != null && !m.getSessionId().equals(sessionId)) {
                context.append(", session: ").append(m.getSessionId().substring(0, 8));
            }
            context.append(")\n");
        }

        context.append("</relevant_memories>\n");

        return context.toString();
    }

    /**
     * 搜索过滤器
     */
    public record SearchFilter(
            MemoryEntry.MemorySource source,
            String sessionId
    ) {
        public boolean matches(MemoryEntry entry) {
            if (source != null && entry.getSource() != source) {
                return false;
            }
            if (sessionId != null && !sessionId.equals(entry.getSessionId())) {
                return false;
            }
            return true;
        }
    }

    /**
     * 带分数的搜索结果
     */
    public record SearchResultWithScore(MemoryEntry entry, double score) {}
}
