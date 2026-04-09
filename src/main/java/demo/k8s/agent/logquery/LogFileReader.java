package demo.k8s.agent.logquery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 日志文件读取器
 * 读取和查询结构化日志文件
 */
@Component
public class LogFileReader {

    private static final Logger log = LoggerFactory.getLogger(LogFileReader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String logDir;

    public LogFileReader() {
        this.logDir = System.getProperty("user.dir") + "/logs";
    }

    /**
     * 查询结构化日志
     */
    public List<LogEntry> queryLogs(LogQuery query) throws IOException {
        Path logPath = getLogPath(query.date);
        if (!Files.exists(logPath)) {
            return List.of();
        }

        List<LogEntry> results = new ArrayList<>();
        List<String> lines = Files.readAllLines(logPath);

        for (String line : lines) {
            try {
                JsonEntry entry = parseJsonLine(line);
                if (matches(entry, query)) {
                    results.add(toLogEntry(entry, line));
                }
            } catch (JsonProcessingException e) {
                log.debug("跳过无法解析的日志行：{}", line.substring(0, Math.min(100, line.length())));
            }
        }

        // 排序
        results.sort(Comparator.comparing(LogEntry::timestamp).reversed());

        // 分页
        int fromIndex = (query.page() - 1) * query.size();
        int toIndex = Math.min(fromIndex + query.size(), results.size());

        return fromIndex < results.size() ? results.subList(fromIndex, toIndex) : results;
    }

    /**
     * 获取日志文件路径
     */
    private Path getLogPath(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        String filename = "structured-events." + date.format(DATE_FORMATTER) + ".jsonl";
        return Paths.get(logDir, filename);
    }

    /**
     * 解析 JSON 行
     */
    private JsonEntry parseJsonLine(String line) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(line);
        return new JsonEntry(
                node.has("timestamp") ? node.get("timestamp").asText() : null,
                node.has("event") ? node.get("event").asText() : null,
                node.has("traceId") ? node.get("traceId").asText() : null,
                node.has("requestId") ? node.get("requestId").asText() : null,
                node.has("userId") ? node.get("userId").asText() : null,
                node.has("sessionId") ? node.get("sessionId").asText() : null,
                node.has("eventType") ? node.get("eventType").asText() : null,
                node.has("skill") ? node.get("skill").asText() : null,
                node.has("skillType") ? node.get("skillType").asText() : null,
                node.has("feature") ? node.get("feature").asText() : null,
                node.has("errorCode") ? node.get("errorCode").asText() : null,
                getNestedString(node, "data", "runId"),
                getNestedString(node, "metadata", "runId"),
                getNestedString(node, "data", "taskId"),
                getNestedString(node, "metadata", "taskId"),
                node
        );
    }

    private static String getNestedString(JsonNode root, String parent, String child) {
        JsonNode p = root.get(parent);
        if (p == null || p.isNull()) {
            return null;
        }
        JsonNode c = p.get(child);
        if (c == null || c.isNull()) {
            return null;
        }
        return c.asText();
    }

    /**
     * 检查是否匹配查询条件
     */
    private boolean matches(JsonEntry entry, LogQuery query) {
        if (query.userId() != null && !query.userId().equals(entry.userId())) {
            return false;
        }
        if (query.traceId() != null && !query.traceId().equals(entry.traceId())) {
            return false;
        }
        if (query.requestId() != null && !query.requestId().equals(entry.requestId())) {
            return false;
        }
        if (query.sessionId() != null && !query.sessionId().equals(entry.sessionId())) {
            return false;
        }
        if (query.runId() != null && !query.runId().equals(entry.runId())) {
            return false;
        }
        if (query.taskId() != null && !query.taskId().equals(entry.taskId())) {
            return false;
        }
        if (query.eventType() != null && !query.eventType().equals(entry.eventType())) {
            return false;
        }
        if (query.event() != null && !query.event().equals(entry.event())) {
            return false;
        }
        if (query.skill() != null && !query.skill().equals(entry.skill())) {
            return false;
        }
        if (query.errorCode() != null && !query.errorCode().equals(entry.errorCode())) {
            return false;
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String s = query.keyword().toLowerCase();
            String raw = entry.node().toString().toLowerCase();
            if (!raw.contains(s)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 转换为 LogEntry 对象
     */
    private LogEntry toLogEntry(JsonEntry json, String rawLine) {
        return new LogEntry(
                json.timestamp,
                json.event,
                json.traceId,
                json.requestId,
                json.userId,
                json.sessionId,
                json.runId(),
                json.taskId(),
                json.eventType,
                json.skill,
                json.skillType,
                json.feature,
                json.errorCode,
                rawLine
        );
    }

    /**
     * 获取可用的日志日期列表
     */
    public List<LocalDate> getAvailableLogDates() throws IOException {
        Path logDirPath = Paths.get(logDir);
        if (!Files.exists(logDirPath)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(logDirPath)) {
            return paths
                    .filter(p -> p.getFileName().toString().startsWith("structured-events."))
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .map(p -> {
                        try {
                            String filename = p.getFileName().toString();
                            String dateStr = filename.replace("structured-events.", "").replace(".jsonl", "");
                            return LocalDate.parse(dateStr, DATE_FORMATTER);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(d -> d != null)
                    .sorted(Comparator.reverseOrder())
                    .limit(30)
                    .toList();
        }
    }

    /**
     * 日志查询参数
     */
    public record LogQuery(
            LocalDate date,
            String userId,
            String traceId,
            String requestId,
            String sessionId,
            String runId,
            String taskId,
            String eventType,
            String event,
            String skill,
            String errorCode,
            String keyword,
            int page,
            int size
    ) {
        public LogQuery {
            page = page <= 0 ? 1 : page;
            size = size <= 0 ? 50 : size;
            size = Math.min(size, 200); // 最大 200 条
        }

        public static LogQueryBuilder builder() {
            return new LogQueryBuilder();
        }
    }

    public static class LogQueryBuilder {
        private LocalDate date = LocalDate.now();
        private String userId;
        private String traceId;
        private String requestId;
        private String sessionId;
        private String runId;
        private String taskId;
        private String eventType;
        private String event;
        private String skill;
        private String errorCode;
        private String keyword;
        private int page = 1;
        private int size = 50;

        public LogQueryBuilder date(LocalDate date) {
            this.date = date;
            return this;
        }

        public LogQueryBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public LogQueryBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public LogQueryBuilder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public LogQueryBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public LogQueryBuilder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public LogQueryBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public LogQueryBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public LogQueryBuilder event(String event) {
            this.event = event;
            return this;
        }

        public LogQueryBuilder skill(String skill) {
            this.skill = skill;
            return this;
        }

        public LogQueryBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public LogQueryBuilder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public LogQueryBuilder page(int page) {
            this.page = page;
            return this;
        }

        public LogQueryBuilder size(int size) {
            this.size = size;
            return this;
        }

        public LogQuery build() {
            return new LogQuery(date, userId, traceId, requestId, sessionId, runId, taskId,
                    eventType, event, skill, errorCode, keyword, page, size);
        }
    }

    /**
     * 日志条目响应
     */
    public record LogEntry(
            String timestamp,
            String event,
            String traceId,
            String requestId,
            String userId,
            String sessionId,
            String runId,
            String taskId,
            String eventType,
            String skill,
            String skillType,
            String feature,
            String errorCode,
            String rawLine
    ) {
        public String userId() { return userId; }
        public String traceId() { return traceId; }
        public String requestId() { return requestId; }
        public String sessionId() { return sessionId; }
        public String runId() { return runId; }
        public String taskId() { return taskId; }
        public String eventType() { return eventType; }
        public String event() { return event; }
        public String skill() { return skill; }
        public String errorCode() { return errorCode; }
    }

    /**
     * 内部 JSON 解析结果
     */
    private record JsonEntry(
            String timestamp,
            String event,
            String traceId,
            String requestId,
            String userId,
            String sessionId,
            String eventType,
            String skill,
            String skillType,
            String feature,
            String errorCode,
            String dataRunId,
            String metadataRunId,
            String dataTaskId,
            String metadataTaskId,
            JsonNode node
    ) {
        public String runId() {
            return dataRunId != null ? dataRunId : metadataRunId;
        }

        public String taskId() {
            return dataTaskId != null ? dataTaskId : metadataTaskId;
        }
    }
}
