package demo.k8s.agent.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 子 Agent 结果外置存储服务。
 * <p>
 * 将子任务执行结果写入文件系统，避免将完整结果注入主 Agent 上下文导致 Token 爆炸。
 * 主 Agent 仅接收结果路径和简短摘要，需要时可调用 file_read 按需读取。
 */
@Component
public class SubagentResultStorage {

    private static final Logger log = LoggerFactory.getLogger(SubagentResultStorage.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * 结果存储根目录（默认：{java.io.tmpdir}/subagent-results）
     */
    private Path resultsRoot;

    @Value("${subagent.results.dir:}")
    private String configResultsDir;

    @PostConstruct
    public void init() {
        if (configResultsDir != null && !configResultsDir.isBlank()) {
            resultsRoot = Path.of(configResultsDir);
        } else {
            resultsRoot = Path.of(System.getProperty("java.io.tmpdir"), "subagent-results");
        }

        try {
            Files.createDirectories(resultsRoot);
            log.info("[SubagentResultStorage] Initialized with results directory: {}", resultsRoot);
        } catch (IOException e) {
            log.error("[SubagentResultStorage] Failed to create results directory: {}", resultsRoot, e);
            throw new RuntimeException("Cannot initialize subagent results storage", e);
        }
    }

    /**
     * 写入子任务结果到文件。
     *
     * @param batchId  批次 ID（可为 null，表示非批次任务）
     * @param runId    子任务运行 ID
     * @param result   执行结果
     * @param status   执行状态（COMPLETED/FAILED/TIMEOUT/CANCELLED）
     * @return 结果文件路径（相对路径，可用于 file_read）
     */
    public String writeResult(String batchId, String runId, String result, String status) {
        // 目录结构：{root}/{batchId-or-date}/run-{runId}.txt
        String subDir = batchId != null && !batchId.isBlank() ? batchId : LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        Path batchDir = resultsRoot.resolve(subDir);

        try {
            Files.createDirectories(batchDir);
        } catch (IOException e) {
            log.error("[SubagentResultStorage] Failed to create batch directory: {}", batchDir, e);
            throw new RuntimeException("Cannot create batch directory", e);
        }

        Path resultFile = batchDir.resolve("run-" + runId + ".txt");
        String content = buildResultFileContent(runId, result, status);

        try {
            Files.writeString(resultFile, content);
            log.info("[SubagentResultStorage] Wrote result to: {}", resultFile);

            // 同时写入一个 meta 文件（包含状态和摘要）
            Path metaFile = batchDir.resolve("run-" + runId + ".meta.json");
            Files.writeString(metaFile, buildMetaJson(runId, status, result));

            // 返回相对路径（供主 Agent 使用）
            return resultsRoot.relativize(resultFile).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("[SubagentResultStorage] Failed to write result file: {}", resultFile, e);
            return null;
        }
    }

    /**
     * 读取子任务结果。
     *
     * @param batchId  批次 ID
     * @param runId    子任务运行 ID
     * @return 结果内容（null 表示文件不存在）
     */
    public String readResult(String batchId, String runId) {
        Path resultFile = resultsRoot.resolve(batchId).resolve("run-" + runId + ".txt");
        try {
            return Files.readString(resultFile);
        } catch (IOException e) {
            log.debug("[SubagentResultStorage] Result file not found: {}", resultFile);
            return null;
        }
    }

    /**
     * 获取结果文件路径。
     *
     * @param batchId  批次 ID
     * @param runId    子任务运行 ID
     * @return 结果文件绝对路径
     */
    public String getResultPath(String batchId, String runId) {
        return resultsRoot.resolve(batchId).resolve("run-" + runId + ".txt").toString();
    }

    /**
     * 生成简短摘要（用于注入主上下文）。
     *
     * @param result   完整结果
     * @param maxLen   最大长度
     * @return 截断后的摘要
     */
    public String summarize(String result, int maxLen) {
        if (result == null || result.isBlank()) {
            return "(no output)";
        }
        String trimmed = result.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        // 智能截断：尝试在句子边界截断
        String prefix = trimmed.substring(0, maxLen);
        int lastSentenceEnd = Math.max(
                prefix.lastIndexOf(".\n"),
                Math.max(prefix.lastIndexOf("!\n"), prefix.lastIndexOf("?\n"))
        );
        if (lastSentenceEnd > maxLen / 2) {
            return prefix.substring(0, lastSentenceEnd + 1) + " [...]";
        }
        return prefix + " [...]";
    }

    private String buildResultFileContent(String runId, String result, String status) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Subagent Result ===\n");
        sb.append("Run ID: ").append(runId).append("\n");
        sb.append("Status: ").append(status).append("\n");
        sb.append("Timestamp: ").append(Instant.now().toString()).append("\n");
        sb.append("\n--- Result Content ---\n");
        if (result != null) {
            sb.append(result);
        } else {
            sb.append("(no result)");
        }
        sb.append("\n--- End of Result ---\n");
        return sb.toString();
    }

    private String buildMetaJson(String runId, String status, String result) {
        String summary = summarize(result, 100).replace("\"", "\\\"");
        return String.format(
                "{\"runId\":\"%s\",\"status\":\"%s\",\"summary\":\"%s\",\"timestamp\":\"%s\"}",
                runId, status, summary, Instant.now().toString()
        );
    }
}
