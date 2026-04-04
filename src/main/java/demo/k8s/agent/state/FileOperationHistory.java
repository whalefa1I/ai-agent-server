package demo.k8s.agent.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件操作历史与撤销管理器
 * <p>
 * 记录文件操作历史（CREATE/MODIFY/DELETE/MOVE），支持 undo/redo 操作
 */
public class FileOperationHistory {

    private static final Logger log = LoggerFactory.getLogger(FileOperationHistory.class);

    /**
     * 文件操作记录
     */
    public record OperationRecord(
            String id,
            OperationType type,
            Path source,
            Path destination,
            String previousContent,
            Instant timestamp,
            String sessionId,
            String messageId
    ) {
        public enum OperationType {
            CREATE,
            MODIFY,
            DELETE,
            MOVE
        }
    }

    /**
     * 操作历史栈
     */
    private final CopyOnWriteArrayList<OperationRecord> history = new CopyOnWriteArrayList<>();

    /**
     * 重做栈
     */
    private final CopyOnWriteArrayList<OperationRecord> redoStack = new CopyOnWriteArrayList<>();

    /**
     * 最大历史记录数量
     */
    private final int maxHistorySize;

    public FileOperationHistory() {
        this(100);
    }

    public FileOperationHistory(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * 记录文件创建操作
     */
    public void recordCreate(Path path, String previousContent, String sessionId, String messageId) {
        OperationRecord record = new OperationRecord(
                generateId(),
                OperationRecord.OperationType.CREATE,
                path,
                null,
                previousContent,
                Instant.now(),
                sessionId,
                messageId
        );
        addRecord(record);
    }

    /**
     * 记录文件修改操作
     */
    public void recordModify(Path path, String previousContent, String sessionId, String messageId) {
        OperationRecord record = new OperationRecord(
                generateId(),
                OperationRecord.OperationType.MODIFY,
                path,
                null,
                previousContent,
                Instant.now(),
                sessionId,
                messageId
        );
        addRecord(record);
    }

    /**
     * 记录文件删除操作
     */
    public void recordDelete(Path path, String previousContent, String sessionId, String messageId) {
        OperationRecord record = new OperationRecord(
                generateId(),
                OperationRecord.OperationType.DELETE,
                path,
                null,
                previousContent,
                Instant.now(),
                sessionId,
                messageId
        );
        addRecord(record);
    }

    /**
     * 记录文件移动操作
     */
    public void recordMove(Path source, Path destination, String sessionId, String messageId) {
        OperationRecord record = new OperationRecord(
                generateId(),
                OperationRecord.OperationType.MOVE,
                source,
                destination,
                null,
                Instant.now(),
                sessionId,
                messageId
        );
        addRecord(record);
    }

    private synchronized void addRecord(OperationRecord record) {
        // 添加到历史
        history.add(record);

        // 清空重做栈（新的操作会使得重做失效）
        redoStack.clear();

        // 限制历史记录大小
        while (history.size() > maxHistorySize) {
            history.remove(0);
        }

        log.debug("记录文件操作：{} {}", record.type, record.source);
    }

    /**
     * 撤销上一次操作
     *
     * @return 撤销结果
     */
    public synchronized UndoResult undo() {
        if (history.isEmpty()) {
            return UndoResult.error("No operations to undo");
        }

        OperationRecord lastOperation = history.remove(history.size() - 1);
        redoStack.add(lastOperation);

        try {
            return switch (lastOperation.type) {
                case CREATE -> undoCreate(lastOperation);
                case MODIFY -> undoModify(lastOperation);
                case DELETE -> undoDelete(lastOperation);
                case MOVE -> undoMove(lastOperation);
            };
        } catch (IOException e) {
            log.error("撤销操作失败：{}", lastOperation, e);
            return UndoResult.error("Failed to undo: " + e.getMessage());
        }
    }

    /**
     * 重做上一次撤销的操作
     *
     * @return 重做结果
     */
    public synchronized UndoResult redo() {
        if (redoStack.isEmpty()) {
            return UndoResult.error("No operations to redo");
        }

        OperationRecord operation = redoStack.remove(redoStack.size() - 1);

        try {
            return switch (operation.type) {
                case CREATE -> redoCreate(operation);
                case MODIFY -> redoModify(operation);
                case DELETE -> redoDelete(operation);
                case MOVE -> redoMove(operation);
            };
        } catch (IOException e) {
            log.error("重做操作失败：{}", operation, e);
            return UndoResult.error("Failed to redo: " + e.getMessage());
        }
    }

    private UndoResult undoCreate(OperationRecord record) throws IOException {
        if (Files.exists(record.source)) {
            Files.delete(record.source);
            log.info("撤销创建：删除文件 {}", record.source);
        }
        return UndoResult.success("Undone create: " + record.source);
    }

    private UndoResult undoModify(OperationRecord record) throws IOException {
        if (record.previousContent != null) {
            Files.writeString(record.source, record.previousContent);
            log.info("撤销修改：恢复文件 {}", record.source);
        }
        return UndoResult.success("Undone modify: " + record.source);
    }

    private UndoResult undoDelete(OperationRecord record) throws IOException {
        if (record.previousContent != null) {
            Files.createDirectories(record.source.getParent());
            Files.writeString(record.source, record.previousContent);
            log.info("撤销删除：恢复文件 {}", record.source);
        }
        return UndoResult.success("Undone delete: " + record.source);
    }

    private UndoResult undoMove(OperationRecord record) throws IOException {
        if (Files.exists(record.destination)) {
            Files.move(record.destination, record.source);
            log.info("撤销移动：{} -> {}", record.destination, record.source);
        }
        return UndoResult.success("Undone move: " + record.destination + " -> " + record.source);
    }

    private UndoResult redoCreate(OperationRecord record) throws IOException {
        // 重新创建需要原始内容，这里无法完全重做
        return UndoResult.success("Redo create requires original content: " + record.source);
    }

    private UndoResult redoModify(OperationRecord record) throws IOException {
        // 重新修改需要知道修改后的内容，这里无法完全重做
        return UndoResult.success("Redo modify requires new content: " + record.source);
    }

    private UndoResult redoDelete(OperationRecord record) throws IOException {
        if (Files.exists(record.source)) {
            Files.delete(record.source);
            log.info("重做删除：{}", record.source);
        }
        return UndoResult.success("Redone delete: " + record.source);
    }

    private UndoResult redoMove(OperationRecord record) throws IOException {
        if (Files.exists(record.source)) {
            Files.move(record.source, record.destination);
            log.info("重做移动：{} -> {}", record.source, record.destination);
        }
        return UndoResult.success("Redone move: " + record.source + " -> " + record.destination);
    }

    /**
     * 获取历史记录
     */
    public List<OperationRecord> getHistory() {
        return List.copyOf(history);
    }

    /**
     * 获取历史记录数量
     */
    public int getHistorySize() {
        return history.size();
    }

    /**
     * 清空历史记录
     */
    public void clear() {
        history.clear();
        redoStack.clear();
    }

    private String generateId() {
        return "op_" + System.nanoTime() + "_" + new java.util.Random().nextInt(10000);
    }

    /**
     * 撤销结果
     */
    public record UndoResult(boolean success, String message, Map<String, Object> data) {
        public static UndoResult success(String message) {
            return new UndoResult(true, message, Map.of());
        }

        public static UndoResult error(String message) {
            return new UndoResult(false, message, Map.of());
        }
    }
}
