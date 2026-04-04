package demo.k8s.agent.memory.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 嵌入服务 - 将文本转换为向量嵌入
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("EmbeddingService initialized with model: {}", embeddingModel.getClass().getSimpleName());
    }

    /**
     * 生成单个文本的嵌入
     *
     * @param text 输入文本
     * @return 嵌入向量
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        try {
            // Spring AI EmbeddingModel.embed() 返回 float[]
            float[] embedding = embeddingModel.embed(text);
            return embedding;
        } catch (Exception e) {
            log.error("生成嵌入失败：{}", text, e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * 批量生成嵌入
     *
     * @param texts 输入文本列表
     * @return 嵌入向量列表
     */
    public float[][] embedBatch(List<String> texts) {
        return texts.stream()
                .map(this::embed)
                .toArray(float[][]::new);
    }

    /**
     * 获取嵌入维度
     *
     * @return 嵌入维度
     */
    public int getDimensions() {
        // OpenAI text-embedding-3-small 返回 1536 维
        // 这里通过一次试探性调用来获取维度
        try {
            float[] testEmbedding = embeddingModel.embed("test");
            return testEmbedding.length;
        } catch (Exception e) {
            log.warn("无法获取嵌入维度，返回默认值 1536", e);
            return 1536;
        }
    }

}
