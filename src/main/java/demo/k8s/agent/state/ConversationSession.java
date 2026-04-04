package demo.k8s.agent.state;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话级对话状态，与 Claude Code 的 AppState/QueryEngine 状态对齐。
 * <p>
 * 使用 @Scope("prototype") 确保每个会话有独立的状态实例。
 */
@Component
@Scope("prototype")
public class ConversationSession {

    /**
     * 会话唯一 ID
     */
    private final String sessionId;

    /**
     * 所属用户 ID（可选，为空表示匿名会话）
     */
    private volatile String userId;

    /**
     * 会话创建时间
     */
    private final Instant createdAt;

    /**
     * 消息历史列表
     */
    private final List<ChatMessage> messages;

    /**
     * 文件快照（按文件路径索引）
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<FileSnapshot>> fileSnapshots;

    /**
     * 归因记录（按消息 ID 索引）
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Attribution>> attributions;

    /**
     * Token 计数
     */
    private final AtomicLong totalInputTokens;
    private final AtomicLong totalOutputTokens;

    /**
     * 会话元数据
     */
    private final ConcurrentHashMap<String, Object> metadata;

    public ConversationSession() {
        this.sessionId = generateSessionId();
        this.userId = null; // 默认为匿名会话
        this.createdAt = Instant.now();
        this.messages = new CopyOnWriteArrayList<>();
        this.fileSnapshots = new ConcurrentHashMap<>();
        this.attributions = new ConcurrentHashMap<>();
        this.totalInputTokens = new AtomicLong(0);
        this.totalOutputTokens = new AtomicLong(0);
        this.metadata = new ConcurrentHashMap<>();

        Objects.requireNonNull(sessionId);
    }

    /**
     * 设置所属用户 ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 获取所属用户 ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 是否为匿名会话
     */
    public boolean isAnonymous() {
        return userId == null || userId.isBlank();
    }

    // ===== 消息管理 =====

    /**
     * 添加消息到历史
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);

        // 更新 Token 计数
        totalInputTokens.addAndGet(message.inputTokens());
        totalOutputTokens.addAndGet(message.outputTokens());
    }

    /**
     * 获取所有消息
     */
    public List<ChatMessage> getMessages() {
        return List.copyOf(messages);
    }

    /**
     * 获取最近 N 条消息
     */
    public List<ChatMessage> getRecentMessages(int limit) {
        int size = messages.size();
        if (size <= limit) {
            return List.copyOf(messages);
        }
        return messages.subList(size - limit, size);
    }

    /**
     * 获取消息数量
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * 更新最后一条消息的 Token 计数
     */
    public void updateLastMessageTokens(long input, long output) {
        if (!messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            // 由于 ChatMessage 是 record，需要替换整个对象
            messages.remove(last);
            messages.add(last.withTokenCounts(input, output));
            totalInputTokens.addAndGet(input);
            totalOutputTokens.addAndGet(output);
        }
    }

    // ===== 文件快照管理 =====

    /**
     * 创建文件快照
     */
    public void snapshotFile(String filePath, String content, String attributedToMessageId) {
        FileSnapshot snapshot = FileSnapshot.create(filePath, content, attributedToMessageId);
        addSnapshot(snapshot);
    }

    /**
     * 记录文件修改
     */
    public void recordFileModification(String filePath, String content, String attributedToMessageId) {
        FileSnapshot snapshot = FileSnapshot.modify(filePath, content, attributedToMessageId);
        addSnapshot(snapshot);
    }

    /**
     * 记录文件删除
     */
    public void recordFileDeletion(String filePath, String attributedToMessageId) {
        FileSnapshot snapshot = FileSnapshot.delete(filePath, attributedToMessageId);
        addSnapshot(snapshot);
    }

    private void addSnapshot(FileSnapshot snapshot) {
        fileSnapshots
                .computeIfAbsent(snapshot.filePath(), k -> new CopyOnWriteArrayList<>())
                .add(snapshot);
    }

    /**
     * 获取文件的所有快照
     */
    public List<FileSnapshot> getFileHistory(String filePath) {
        List<FileSnapshot> snapshots = fileSnapshots.get(filePath);
        return snapshots != null ? List.copyOf(snapshots) : List.of();
    }

    /**
     * 获取文件的最新内容
     */
    public String getLatestFileContent(String filePath) {
        List<FileSnapshot> snapshots = fileSnapshots.get(filePath);
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        FileSnapshot latest = snapshots.get(snapshots.size() - 1);
        return latest.content();
    }

    /**
     * 获取所有修改过的文件路径
     */
    public List<String> getModifiedFilePaths() {
        return List.copyOf(fileSnapshots.keySet());
    }

    // ===== 归因管理 =====

    /**
     * 添加归因记录
     */
    public void addAttribution(Attribution attribution) {
        attributions
                .computeIfAbsent(attribution.messageId(), k -> new CopyOnWriteArrayList<>())
                .add(attribution);
    }

    /**
     * 获取消息的归因记录
     */
    public List<Attribution> getAttributions(String messageId) {
        List<Attribution> attribs = attributions.get(messageId);
        return attribs != null ? List.copyOf(attribs) : List.of();
    }

    // ===== 元数据管理 =====

    /**
     * 设置元数据
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    /**
     * 获取所有元数据（只读副本）
     */
    public Map<String, Object> getAllMetadata() {
        return new java.util.HashMap<>(metadata);
    }

    // ===== 统计信息 =====

    /**
     * 获取会话持续时间
     */
    public Duration getSessionDuration() {
        return Duration.between(createdAt, Instant.now());
    }

    /**
     * 获取总输入 Token 数
     */
    public long getTotalInputTokens() {
        return totalInputTokens.get();
    }

    /**
     * 获取总输出 Token 数
     */
    public long getTotalOutputTokens() {
        return totalOutputTokens.get();
    }

    /**
     * 获取总 Token 数
     */
    public long getTotalTokens() {
        return totalInputTokens.get() + totalOutputTokens.get();
    }

    // ===== Getter =====

    public String getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 会话统计快照
     */
    public record SessionSnapshot(
            String sessionId,
            String userId,
            Instant createdAt,
            Duration sessionDuration,
            int messageCount,
            long totalInputTokens,
            long totalOutputTokens,
            int modifiedFileCount,
            List<String> modifiedFilePaths
    ) {
        public static SessionSnapshot from(ConversationSession session) {
            return new SessionSnapshot(
                    session.getSessionId(),
                    session.getUserId(),
                    session.getCreatedAt(),
                    session.getSessionDuration(),
                    session.getMessageCount(),
                    session.getTotalInputTokens(),
                    session.getTotalOutputTokens(),
                    session.getModifiedFilePaths().size(),
                    session.getModifiedFilePaths()
            );
        }
    }

    public SessionSnapshot getSnapshot() {
        return SessionSnapshot.from(this);
    }

    private static String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" +
               UUID.randomUUID().toString().substring(0, 8);
    }
}
