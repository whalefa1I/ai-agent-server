package demo.k8s.agent.state;

import java.util.List;
import java.util.Optional;

/**
 * 对话仓库接口，用于会话持久化。
 */
public interface ConversationRepository {

    /**
     * 保存会话
     */
    void saveSession(ConversationSession session);

    /**
     * 加载会话
     */
    Optional<ConversationSession> loadSession(String sessionId);

    /**
     * 删除会话
     */
    void deleteSession(String sessionId);

    /**
     * 保存消息
     */
    void saveMessage(String sessionId, ChatMessage message);

    /**
     * 保存文件快照列表
     */
    void saveFileSnapshot(String sessionId, String filePath, List<FileSnapshot> snapshots);

    /**
     * 加载文件历史
     */
    List<FileSnapshot> loadFileHistory(String sessionId, String filePath);

    /**
     * 列出所有会话
     */
    List<String> listSessions();
}
