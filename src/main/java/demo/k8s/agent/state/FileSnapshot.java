package demo.k8s.agent.state;

import java.time.Instant;
import java.util.Objects;

/**
 * 文件快照记录，与 Claude Code 的 FileHistorySnapshot 对齐。
 *
 * @param filePath 文件路径
 * @param content 快照时的内容
 * @param snapshotTime 快照时间
 * @param contentHash 内容哈希（用于快速比较）
 * @param attributedToMessageId 导致修改的消息 ID
 * @param operation 操作类型（create/modify/delete）
 */
public record FileSnapshot(
        String filePath,
        String content,
        Instant snapshotTime,
        String contentHash,
        String attributedToMessageId,
        Operation operation
) {
    public enum Operation {
        CREATE,
        MODIFY,
        DELETE
    }

    public FileSnapshot {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(snapshotTime, "snapshotTime");
        Objects.requireNonNull(operation, "operation");
    }

    public static FileSnapshot create(String filePath, String content, String attributedToMessageId) {
        return new FileSnapshot(
                filePath,
                content,
                Instant.now(),
                hash(content),
                attributedToMessageId,
                Operation.CREATE
        );
    }

    public static FileSnapshot modify(String filePath, String content, String attributedToMessageId) {
        return new FileSnapshot(
                filePath,
                content,
                Instant.now(),
                hash(content),
                attributedToMessageId,
                Operation.MODIFY
        );
    }

    public static FileSnapshot delete(String filePath, String attributedToMessageId) {
        return new FileSnapshot(
                filePath,
                null,
                Instant.now(),
                null,
                attributedToMessageId,
                Operation.DELETE
        );
    }

    private static String hash(String content) {
        if (content == null) return null;
        // 简单的哈希计算（生产环境建议使用 CRC32 或 MD5）
        return String.valueOf(content.hashCode());
    }

    /**
     * 获取内容长度（用于估算）
     */
    public int getContentLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * 检查是否是同一个文件的不同版本
     */
    public boolean isSameFile(String otherPath) {
        return this.filePath.equals(otherPath);
    }

    /**
     * 检查内容是否发生变化
     */
    public boolean contentChanged(FileSnapshot other) {
        if (other == null) return true;
        return !Objects.equals(this.contentHash, other.contentHash);
    }
}
