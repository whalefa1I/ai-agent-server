package demo.k8s.agent.memory.store;

import demo.k8s.agent.memory.model.MemoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryVectorStore 单元测试
 */
class InMemoryVectorStoreTest {

    private InMemoryVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore();
    }

    @Test
    void testAddEntry() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        MemoryEntry entry = MemoryEntry.builder()
                .id("test-id")
                .content("This is a test memory")
                .embedding(embedding)
                .source(MemoryEntry.MemorySource.CONVERSATION)
                .sessionId("session-123")
                .userId("user-456")
                .build();

        vectorStore.add(entry);

        var retrieved = vectorStore.getById("test-id");
        assertTrue(retrieved.isPresent());
        assertEquals("test-id", retrieved.get().getId());
        assertEquals("This is a test memory", retrieved.get().getContent());
    }

    @Test
    void testDeleteEntry() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        MemoryEntry entry = MemoryEntry.builder()
                .id("test-id")
                .content("This is a test memory")
                .embedding(embedding)
                .source(MemoryEntry.MemorySource.CONVERSATION)
                .sessionId("session-123")
                .userId("user-456")
                .build();

        vectorStore.add(entry);
        vectorStore.delete("test-id");

        var retrieved = vectorStore.getById("test-id");
        assertTrue(retrieved.isEmpty());
    }

    @Test
    void testSimilaritySearch() {
        // 添加两个不同方向的向量
        float[] embedding1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] embedding2 = new float[]{0.0f, 1.0f, 0.0f};

        MemoryEntry entry1 = MemoryEntry.builder()
                .id("id-1")
                .content("Memory about cats")
                .embedding(embedding1)
                .source(MemoryEntry.MemorySource.CONVERSATION)
                .sessionId("session-1")
                .userId("user-1")
                .build();

        MemoryEntry entry2 = MemoryEntry.builder()
                .id("id-2")
                .content("Memory about dogs")
                .embedding(embedding2)
                .source(MemoryEntry.MemorySource.CONVERSATION)
                .sessionId("session-2")
                .userId("user-2")
                .build();

        vectorStore.add(entry1);
        vectorStore.add(entry2);

        // 搜索与 embedding1 相似的向量
        var results = vectorStore.searchWithScore(embedding1, 2);

        assertNotNull(results);
        assertEquals(2, results.size());
        // 第一个结果应该是 entry1（最相似）
        assertEquals("id-1", results.get(0).entry().getId());
        assertTrue(results.get(0).score() > 0.9); // 应该非常接近 1
    }

    @Test
    void testSearchWithFilter() {
        float[] embedding = new float[]{1.0f, 0.0f, 0.0f};

        MemoryEntry entry1 = MemoryEntry.builder()
                .id("id-1")
                .content("Memory 1")
                .embedding(embedding)
                .source(MemoryEntry.MemorySource.CONVERSATION)
                .sessionId("session-1")
                .userId("user-1")
                .build();

        MemoryEntry entry2 = MemoryEntry.builder()
                .id("id-2")
                .content("Memory 2")
                .embedding(embedding)
                .source(MemoryEntry.MemorySource.CONVERSATION)
                .sessionId("session-2")
                .userId("user-1")
                .build();

        vectorStore.add(entry1);
        vectorStore.add(entry2);

        // 按 sessionId 过滤 - 注意：当前实现不支持过滤，这个测试验证基本搜索
        var results = vectorStore.searchWithScore(embedding, 10);

        assertEquals(2, results.size());
    }

    @Test
    void testClear() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        MemoryEntry entry = MemoryEntry.builder()
                .id("test-id")
                .content("Test")
                .embedding(embedding)
                .source(MemoryEntry.MemorySource.CONVERSATION)
                .sessionId("session-1")
                .userId("user-1")
                .build();

        vectorStore.add(entry);
        vectorStore.clear();

        var results = vectorStore.searchWithScore(embedding, 10);
        assertTrue(results.isEmpty());
    }
}
