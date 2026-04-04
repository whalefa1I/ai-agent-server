package demo.k8s.agent.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 基于文件系统的对话仓库实现。
 */
@Repository
public class FileSystemConversationRepository implements ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemConversationRepository.class);

    private final Path sessionsDir;
    private final ObjectMapper objectMapper;

    public FileSystemConversationRepository(
            @org.springframework.beans.factory.annotation.Value("${agent.state.dir:${user.home}/.claude/sessions}")
            String sessionsDir,
            ObjectMapper objectMapper) {
        this.sessionsDir = Path.of(sessionsDir);
        this.objectMapper = objectMapper;

        try {
            Files.createDirectories(this.sessionsDir);
            log.info("会话存储目录：{}", this.sessionsDir);
        } catch (IOException e) {
            log.warn("创建会话存储目录失败：{}", this.sessionsDir, e);
        }
    }

    @Override
    public void saveSession(ConversationSession session) {
        Path file = sessionsDir.resolve(session.getSessionId() + ".json");
        try {
            objectMapper.writeValue(file.toFile(), session.getSnapshot());
            log.debug("保存会话：{}", session.getSessionId());
        } catch (IOException e) {
            log.warn("保存会话失败：{}", session.getSessionId(), e);
        }
    }

    @Override
    public Optional<ConversationSession> loadSession(String sessionId) {
        Path file = sessionsDir.resolve(sessionId + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            // 注意：这里加载的是快照，不是完整状态
            // 完整实现需要反序列化整个 ConversationSession
            log.debug("加载会话快照：{}", sessionId);
            return Optional.empty(); // 简化实现
        } catch (Exception e) {
            log.warn("加载会话失败：{}", sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        Path file = sessionsDir.resolve(sessionId + ".json");
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                log.info("删除会话：{}", sessionId);
            }
        } catch (IOException e) {
            log.warn("删除会话失败：{}", sessionId, e);
        }
    }

    @Override
    public void saveMessage(String sessionId, ChatMessage message) {
        Path file = sessionsDir.resolve(sessionId + "_messages.jsonl");
        try {
            String json = objectMapper.writeValueAsString(message);
            Files.writeString(file, json + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("保存消息失败：{}", sessionId, e);
        }
    }

    @Override
    public void saveFileSnapshot(String sessionId, String filePath, List<FileSnapshot> snapshots) {
        Path file = sessionsDir.resolve(sessionId + "_files_" + sanitize(filePath) + ".json");
        try {
            objectMapper.writeValue(file.toFile(), snapshots);
        } catch (IOException e) {
            log.warn("保存文件快照失败：{} - {}", sessionId, filePath, e);
        }
    }

    @Override
    public List<FileSnapshot> loadFileHistory(String sessionId, String filePath) {
        Path file = sessionsDir.resolve(sessionId + "_files_" + sanitize(filePath) + ".json");
        if (!Files.exists(file)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(file.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FileSnapshot.class));
        } catch (IOException e) {
            log.warn("加载文件历史失败：{} - {}", sessionId, filePath, e);
            return List.of();
        }
    }

    @Override
    public List<String> listSessions() {
        try (Stream<Path> stream = Files.list(sessionsDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .toList();
        } catch (IOException e) {
            log.warn("列出会话失败", e);
            return List.of();
        }
    }

    private static String sanitize(String filePath) {
        // 将文件路径转换为安全的文件名
        return filePath.replace("/", "_").replace("\\", "_").replace(":", "_");
    }
}
