package demo.k8s.agent.memory.store;

import demo.k8s.agent.memory.model.MemoryEntry;

import java.util.List;
import java.util.Optional;

/**
 * 向量存储接口 - 用于存储和检索记忆嵌入
 */
public interface VectorStore {

    /**
     * 初始化存储（创建表、索引等）
     */
    void initialize();

    /**
     * 添加单个记忆条目
     *
     * @param entry 记忆条目
     */
    void add(MemoryEntry entry);

    /**
     * 批量添加记忆条目
     *
     * @param entries 记忆条目列表
     */
    void addAll(List<MemoryEntry> entries);

    /**
     * 根据 ID 获取记忆条目
     *
     * @param id 记忆 ID
     * @return 记忆条目（如果存在）
     */
    Optional<MemoryEntry> getById(String id);

    /**
     * 执行相似度搜索
     *
     * @param queryEmbedding 查询向量
     * @param maxResults     最大返回结果数
     * @return 按相似度排序的记忆条目列表
     */
    List<MemoryEntry> searchBySimilarity(float[] queryEmbedding, int maxResults);

    /**
     * 执行相似度搜索（带分数）
     *
     * @param queryEmbedding 查询向量
     * @param maxResults     最大返回结果数
     * @return 记忆条目和相似度分数的列表
     */
    List<SearchResult> searchWithScore(float[] queryEmbedding, int maxResults);

    /**
     * 删除记忆条目
     *
     * @param id 记忆 ID
     * @return 是否删除成功
     */
    boolean delete(String id);

    /**
     * 删除指定会话的所有记忆
     *
     * @param sessionId 会话 ID
     */
    void deleteBySession(String sessionId);

    /**
     * 获取记忆总数
     *
     * @return 记忆总数
     */
    long count();

    /**
     * 清空所有记忆
     */
    void clear();

    /**
     * 搜索结果 - 包含记忆条目和相似度分数
     */
    record SearchResult(MemoryEntry entry, double score) {}
}
