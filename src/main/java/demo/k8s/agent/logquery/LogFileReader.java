package demo.k8s.agent.logquery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final String querySource;
    private final String lokiUrl;
    private final String lokiUsername;
    private final String lokiPassword;
    private final int lokiTimeoutSeconds;
    private final HttpClient httpClient;

    public LogFileReader(
            @Value("${demo.logs.query-source:auto}") String querySource,
            @Value("${LOKI_URL:}") String lokiUrl,
            @Value("${LOKI_USERNAME:}") String lokiUsername,
            @Value("${LOKI_PASSWORD:}") String lokiPassword,
            @Value("${demo.logs.loki-timeout-seconds:15}") int lokiTimeoutSeconds
    ) {
        this.logDir = System.getProperty("user.dir") + "/logs";
        this.querySource = querySource != null ? querySource.trim().toLowerCase(Locale.ROOT) : "auto";
        this.lokiUrl = lokiUrl != null ? lokiUrl.trim() : "";
        this.lokiUsername = lokiUsername != null ? lokiUsername.trim() : "";
        this.lokiPassword = lokiPassword != null ? lokiPassword.trim() : "";
        this.lokiTimeoutSeconds = lokiTimeoutSeconds <= 0 ? 15 : lokiTimeoutSeconds;
        this.httpClient = HttpClient.newBuilder().build();

        // 打印 Loki 配置信息，用于调试
        log.info("[LokiConfig] query-source={}, lokiUrl={}, lokiUsername={}, lokiPassword={}, timeout={}s",
                this.querySource,
                this.lokiUrl,
                this.lokiUsername,
                this.lokiPassword != null && !this.lokiPassword.isEmpty() ? "***configured***" : "<empty>",
                this.lokiTimeoutSeconds);
        log.info("[LokiConfig] isLokiConfigured={}", isLokiConfigured());
    }

    /**
     * 查询结构化日志
     */
    public List<LogEntry> queryLogs(LogQuery query) {
        SourceMode mode = SourceMode.from(querySource);
        if (mode == SourceMode.LOKI || mode == SourceMode.AUTO) {
            if (isLokiConfigured()) {
                try {
                    List<LogEntry> lokiResults = queryFromLoki(query);
                    if (mode == SourceMode.LOKI) {
                        return paginateAndSort(lokiResults, query);
                    }
                    if (!lokiResults.isEmpty()) {
                        return paginateAndSort(lokiResults, query);
                    }
                } catch (Exception e) {
                    if (mode == SourceMode.LOKI) {
                        log.warn("Loki 查询失败（loki 模式，返回空）: {}", e.getMessage());
                        return List.of();
                    }
                    log.warn("Loki 查询失败，回退文件查询: {}", e.getMessage());
                }
            } else if (mode == SourceMode.LOKI) {
                log.warn("query-source=loki 但 Loki 未配置，返回空结果");
                return List.of();
            }
        }

        // file 模式，或 auto 模式下 Loki 无可用结果时回退
        return queryFromFiles(query);
    }

    private List<LogEntry> queryFromFiles(LogQuery query) {
        Path logDirPath = Paths.get(logDir);
        if (!Files.exists(logDirPath)) {
            return List.of();
        }

        List<Path> candidatePaths;
        try {
            candidatePaths = getCandidateLogPaths(query.date);
        } catch (IOException e) {
            log.warn("获取日志候选文件失败，返回空结果: {}", e.getMessage());
            return List.of();
        }
        if (candidatePaths.isEmpty()) {
            return List.of();
        }

        List<LogEntry> results = new ArrayList<>();
        for (Path path : candidatePaths) {
            if (!Files.exists(path)) {
                continue;
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(path);
            } catch (IOException e) {
                log.warn("读取日志文件失败，已跳过 {}: {}", path.getFileName(), e.getMessage());
                continue;
            }
            for (String line : lines) {
                try {
                    JsonEntry entry = parseJsonLine(line);
                    if (matches(entry, query)) {
                        results.add(toLogEntry(entry, line));
                    }
                } catch (Exception e) {
                    log.debug("跳过无法解析的日志行（{}）：{}", path.getFileName(), line.substring(0, Math.min(100, line.length())));
                }
            }
        }
        return paginateAndSort(results, query);
    }

    private List<LogEntry> paginateAndSort(List<LogEntry> results, LogQuery query) {
        // 排序（兼容部分日志缺失 timestamp 的情况，避免全量查询时 500）
        results.sort(Comparator.comparing(LogEntry::timestamp, Comparator.nullsLast(String::compareTo)).reversed());

        // 分页
        int fromIndex = (query.page() - 1) * query.size();
        int toIndex = Math.min(fromIndex + query.size(), results.size());

        return fromIndex < results.size() ? results.subList(fromIndex, toIndex) : results;
    }

    private boolean isLokiConfigured() {
        return !lokiUrl.isBlank() && !lokiUsername.isBlank() && !lokiPassword.isBlank();
    }

    private List<LogEntry> queryFromLoki(LogQuery query) throws IOException, InterruptedException {
        String queryExpr = "{service_name=~\".+\"}";
        List<String> terms = buildSearchTerms(query);
        for (String term : terms) {
            queryExpr += " |= " + "\"" + escapeLokiString(term) + "\"";
        }

        LocalDate date = query.date() != null ? query.date() : LocalDate.now();
        long startNs = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() * 1_000_000L;
        long endNs = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() * 1_000_000L - 1;
        int limit = Math.min(Math.max(query.size() * 5, 200), 1000);

        String base = normalizeLokiBaseUrl(lokiUrl);
        String url = base
                + "/loki/api/v1/query_range?query=" + URLEncoder.encode(queryExpr, StandardCharsets.UTF_8)
                + "&start=" + startNs
                + "&end=" + endNs
                + "&limit=" + limit
                + "&direction=backward";

        String basicAuth = java.util.Base64.getEncoder()
                .encodeToString((lokiUsername + ":" + lokiPassword).getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(lokiTimeoutSeconds))
                .header("Authorization", "Basic " + basicAuth)
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Loki query failed, status=" + resp.statusCode());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode result = root.path("data").path("result");
        List<LogEntry> entries = new ArrayList<>();
        if (!result.isArray()) {
            return entries;
        }
        for (JsonNode stream : result) {
            JsonNode values = stream.path("values");
            if (!values.isArray()) {
                continue;
            }
            for (JsonNode pair : values) {
                if (!pair.isArray() || pair.size() < 2) {
                    continue;
                }
                String line = pair.get(1).asText();
                try {
                    JsonEntry entry = parseJsonLine(line);
                    if (matches(entry, query)) {
                        entries.add(toLogEntry(entry, line));
                    }
                } catch (Exception ignored) {
                    // 忽略非结构化行
                }
            }
        }
        return entries;
    }

    private static List<String> buildSearchTerms(LogQuery query) {
        List<String> terms = new ArrayList<>();
        if (query.requestId() != null && !query.requestId().isBlank()) terms.add(query.requestId());
        if (query.traceId() != null && !query.traceId().isBlank()) terms.add(query.traceId());
        if (query.sessionId() != null && !query.sessionId().isBlank()) terms.add(query.sessionId());
        if (query.runId() != null && !query.runId().isBlank()) terms.add(query.runId());
        if (query.taskId() != null && !query.taskId().isBlank()) terms.add(query.taskId());
        if (query.event() != null && !query.event().isBlank()) terms.add(query.event());
        if (query.eventType() != null && !query.eventType().isBlank()) terms.add(query.eventType());
        if (query.errorCode() != null && !query.errorCode().isBlank()) terms.add(query.errorCode());
        if (query.keyword() != null && !query.keyword().isBlank()) terms.add(query.keyword());
        return terms;
    }

    private static String escapeLokiString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String normalizeLokiBaseUrl(String url) {
        String u = url.trim();
        if (u.endsWith("/loki/api/v1/push")) {
            return u.substring(0, u.length() - "/loki/api/v1/push".length());
        }
        if (u.endsWith("/")) {
            return u.substring(0, u.length() - 1);
        }
        return u;
    }

    private List<Path> getCandidateLogPaths(LocalDate date) throws IOException {
        Path logDirPath = Paths.get(logDir);
        if (!Files.exists(logDirPath)) {
            return List.of();
        }

        LocalDate targetDate = date != null ? date : LocalDate.now();
        String targetDateStr = targetDate.format(DATE_FORMATTER);
        String previousDateStr = targetDate.minusDays(1).format(DATE_FORMATTER);

        Set<Path> files = new LinkedHashSet<>();

        // 优先读取结构化日志文件（纯 JSON 行）
        files.add(Paths.get(logDir, "structured-events." + targetDateStr + ".jsonl"));
        files.add(Paths.get(logDir, "structured-events." + previousDateStr + ".jsonl"));
        files.add(Paths.get(logDir, "structured-events.jsonl"));

        // 同时读取应用滚动日志，兼容 "prefix + JSON" 的行格式
        try (Stream<Path> paths = Files.list(logDirPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .filter(p -> {
                        String filename = p.getFileName().toString();
                        return filename.contains(targetDateStr)
                                || filename.contains(previousDateStr)
                                || filename.endsWith(".jsonl");
                    })
                    .forEach(files::add);
        }

        return new ArrayList<>(files);
    }

    /**
     * 解析 JSON 行
     */
    private JsonEntry parseJsonLine(String line) throws JsonProcessingException {
        JsonNode node = parseJsonNode(line);
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
                node.has("errorCode") ? node.get("errorCode").asText() : getNestedString(node, "data", "error"),
                getNestedString(node, "data", "runId"),
                getNestedString(node, "metadata", "runId"),
                getNestedString(node, "data", "taskId"),
                getNestedString(node, "metadata", "taskId"),
                node
        );
    }

    private JsonNode parseJsonNode(String line) throws JsonProcessingException {
        try {
            return objectMapper.readTree(line);
        } catch (JsonProcessingException ignored) {
            // 兼容带前缀日志行："... StructuredLogger : {json}"
            int firstBrace = line.indexOf('{');
            int lastBrace = line.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                String jsonPart = line.substring(firstBrace, lastBrace + 1);
                return objectMapper.readTree(jsonPart);
            }
            throw ignored;
        }
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
        if (query.onlyErrors() && !hasErrorSignal(entry)) {
            return false;
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String s = query.keyword().toLowerCase();
            String haystack = (safe(entry.timestamp())
                    + " " + safe(entry.event())
                    + " " + safe(entry.traceId())
                    + " " + safe(entry.requestId())
                    + " " + safe(entry.userId())
                    + " " + safe(entry.sessionId())
                    + " " + safe(entry.eventType())
                    + " " + safe(entry.skill())
                    + " " + safe(entry.skillType())
                    + " " + safe(entry.feature())
                    + " " + safe(entry.errorCode())
                    + " " + safe(entry.runId())
                    + " " + safe(entry.taskId())
                    + " " + (entry.node() != null ? entry.node().toString() : ""))
                    .toLowerCase();
            if (!haystack.contains(s)) {
                return false;
            }
        }
        return true;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static boolean hasErrorSignal(JsonEntry entry) {
        if ("error".equalsIgnoreCase(safe(entry.event()))) {
            return true;
        }
        if (!safe(entry.errorCode()).isBlank()) {
            return true;
        }
        String rawNode = entry.node() != null ? entry.node().toString().toLowerCase() : "";
        return rawNode.contains("\"error\":");
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
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .map(p -> {
                        try {
                            String filename = p.getFileName().toString();
                            int firstDot = filename.indexOf('.');
                            int lastDot = filename.lastIndexOf('.');
                            if (firstDot < 0 || lastDot <= firstDot) {
                                return null;
                            }
                            String dateStr = filename.substring(firstDot + 1, lastDot);
                            if (!dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                                return null;
                            }
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
            boolean onlyErrors,
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
        private boolean onlyErrors;
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

        public LogQueryBuilder onlyErrors(boolean onlyErrors) {
            this.onlyErrors = onlyErrors;
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
                    eventType, event, skill, errorCode, onlyErrors, keyword, page, size);
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

    private enum SourceMode {
        AUTO, LOKI, FILE;

        static SourceMode from(String value) {
            if ("loki".equalsIgnoreCase(value)) {
                return LOKI;
            }
            if ("file".equalsIgnoreCase(value)) {
                return FILE;
            }
            return AUTO;
        }
    }
}
