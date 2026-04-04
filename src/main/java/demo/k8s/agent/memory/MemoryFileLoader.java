package demo.k8s.agent.memory;

import demo.k8s.agent.memory.embedding.EmbeddingService;
import demo.k8s.agent.memory.model.MemoryEntry;
import demo.k8s.agent.memory.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MEMORY.md 文件加载器 - 解析并索引跨会话记忆文件
 * <p>
 * MEMORY.md 格式：
 * ---
 * name: project_context
 * description: 项目背景和关键决策
 * type: project
 * ---
 *
 * 内容主体...
 *
 * ---
 * name: user_preferences
 * description: 用户偏好设置
 * type: feedback
 * ---
 *
 * 内容主体...
 */
@Service
public class MemoryFileLoader {

    private static final Logger log = LoggerFactory.getLogger(MemoryFileLoader.class);

    private static final Path MEMORY_FILE = Paths.get(System.getProperty("user.home"), ".claude", "MEMORY.md");

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)",
            Pattern.DOTALL
    );

    private static final Pattern METADATA_PATTERN = Pattern.compile(
            "^(\\w+):\\s*(.+?)\\s*$",
            Pattern.MULTILINE
    );

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public MemoryFileLoader(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        log.info("MemoryFileLoader initialized, looking for MEMORY.md at: {}", MEMORY_FILE);
    }

    /**
     * 应用启动时自动加载 MEMORY.md
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onLoad() {
        loadAndIndex();
    }

    /**
     * 加载并索引 MEMORY.md 文件
     *
     * @return 加载的记忆条目数量
     */
    public int loadAndIndex() {
        if (!Files.exists(MEMORY_FILE)) {
            log.debug("MEMORY.md not found at {}, skipping", MEMORY_FILE);
            return 0;
        }

        try {
            String content = Files.readString(MEMORY_FILE);
            List<MemoryEntry> entries = parse(content);

            for (MemoryEntry entry : entries) {
                // 为每个条目生成嵌入
                float[] embedding = embeddingService.embed(entry.getContent());
                // 注意：需要更新 entry 的 embedding，但 MemoryEntry 是不可变的
                // 这里创建一个带 embedding 的新条目
                MemoryEntry entryWithEmbedding = MemoryEntry.builder()
                        .id(entry.getId())
                        .content(entry.getContent())
                        .embedding(embedding)
                        .source(MemoryEntry.MemorySource.MEMORY_FILE)
                        .metadata(entry.getMetadata())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                vectorStore.add(entryWithEmbedding);
            }

            log.info("Loaded and indexed {} memory entries from MEMORY.md", entries.size());
            return entries.size();

        } catch (IOException e) {
            log.error("Failed to read MEMORY.md: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 解析 MEMORY.md 文件内容
     *
     * @param content 文件内容
     * @return 记忆条目列表
     */
    public List<MemoryEntry> parse(String content) {
        List<MemoryEntry> entries = new ArrayList<>();

        // 查找所有的 frontmatter 块
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);

        while (matcher.find()) {
            String frontmatter = matcher.group(1);
            String body = matcher.group(2).trim();

            // 解析元数据
            Map<String, Object> metadata = parseMetadata(frontmatter);

            // 创建记忆条目
            MemoryEntry entry = MemoryEntry.builder()
                    .content(body)
                    .source(MemoryEntry.MemorySource.MEMORY_FILE)
                    .metadata(metadata)
                    .build();

            entries.add(entry);
        }

        // 如果没有找到 frontmatter，但内容有值，将整个文件作为一个记忆条目
        if (entries.isEmpty() && !content.isBlank()) {
            MemoryEntry entry = MemoryEntry.builder()
                    .content(content.trim())
                    .source(MemoryEntry.MemorySource.MEMORY_FILE)
                    .metadata(Map.of("type", "global"))
                    .build();
            entries.add(entry);
        }

        log.debug("Parsed {} memory entries from MEMORY.md", entries.size());
        return entries;
    }

    /**
     * 解析 frontmatter 元数据
     */
    private Map<String, Object> parseMetadata(String frontmatter) {
        Map<String, Object> metadata = new HashMap<>();

        Matcher metaMatcher = METADATA_PATTERN.matcher(frontmatter);
        while (metaMatcher.find()) {
            String key = metaMatcher.group(1);
            String value = metaMatcher.group(2);
            metadata.put(key, value);
        }

        return metadata;
    }

    /**
     * 重新加载 MEMORY.md（先删除旧的，再加载新的）
     *
     * @return 加载的记忆条目数量
     */
    public int reload() {
        // 删除所有来自 MEMORY_FILE 的记忆
        // 注意：VectorStore 需要支持按来源删除，这里简化处理
        log.info("Reloading MEMORY.md...");
        return loadAndIndex();
    }

    /**
     * 获取 MEMORY.md 文件路径
     */
    public static Path getMemoryFilePath() {
        return MEMORY_FILE;
    }

    /**
     * 检查 MEMORY.md 是否存在
     */
    public boolean exists() {
        return Files.exists(MEMORY_FILE);
    }
}
