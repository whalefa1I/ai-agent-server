package demo.k8s.agent.logquery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Log Query API Controller
 */
@RestController
@RequestMapping("/api/logs")
public class LogQueryController {

    private static final Logger log = LoggerFactory.getLogger(LogQueryController.class);

    private final LogFileReader logFileReader;

    public LogQueryController(LogFileReader logFileReader) {
        this.logFileReader = logFileReader;
    }

    /**
     * Query structured logs
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> queryLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String event,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        try {
            LogFileReader.LogQuery query = LogFileReader.LogQuery.builder()
                    .date(date)
                    .userId(userId)
                    .traceId(traceId)
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .runId(runId)
                    .taskId(taskId)
                    .eventType(eventType)
                    .event(event)
                    .skill(skill)
                    .keyword(keyword)
                    .page(page)
                    .size(size)
                    .build();

            List<LogFileReader.LogEntry> entries = logFileReader.queryLogs(query);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", entries);
            response.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "total", entries.size()
            ));

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to query logs", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to query logs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get available log dates
     */
    @GetMapping("/dates")
    public ResponseEntity<Map<String, Object>> getAvailableDates() {
        try {
            List<LocalDate> dates = logFileReader.getAvailableLogDates();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dates);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to get available dates", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to get available dates: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Query logs by user ID
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserLogs(
            @RequestParam String userId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        try {
            LogFileReader.LogQuery query = LogFileReader.LogQuery.builder()
                    .date(date)
                    .userId(userId)
                    .page(page)
                    .size(size)
                    .build();

            List<LogFileReader.LogEntry> entries = logFileReader.queryLogs(query);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", entries);
            response.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "total", entries.size()
            ));

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to query user logs", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to query user logs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Query logs by session ID
     */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> getSessionLogs(
            @RequestParam String sessionId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        try {
            LogFileReader.LogQuery query = LogFileReader.LogQuery.builder()
                    .date(date)
                    .sessionId(sessionId)
                    .page(page)
                    .size(size)
                    .build();

            List<LogFileReader.LogEntry> entries = logFileReader.queryLogs(query);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", entries);
            response.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "total", entries.size()
            ));

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to query session logs", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to query session logs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 全链路查询：优先建议按 requestId 或 traceId 检索
     */
    @GetMapping("/chain")
    public ResponseEntity<Map<String, Object>> getChainLogs(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "100") int size
    ) {
        try {
            LogFileReader.LogQuery query = LogFileReader.LogQuery.builder()
                    .date(date)
                    .requestId(requestId)
                    .traceId(traceId)
                    .sessionId(sessionId)
                    .runId(runId)
                    .taskId(taskId)
                    .keyword(keyword)
                    .page(page)
                    .size(size)
                    .build();
            List<LogFileReader.LogEntry> entries = logFileReader.queryLogs(query);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", entries);
            response.put("filters", Map.of(
                    "requestId", requestId != null ? requestId : "",
                    "traceId", traceId != null ? traceId : "",
                    "sessionId", sessionId != null ? sessionId : "",
                    "runId", runId != null ? runId : "",
                    "taskId", taskId != null ? taskId : "",
                    "keyword", keyword != null ? keyword : ""
            ));
            response.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "total", entries.size()
            ));
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to query chain logs", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to query chain logs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Query error logs
     */
    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> getErrorLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        try {
            LogFileReader.LogQuery query = LogFileReader.LogQuery.builder()
                    .date(date)
                    .userId(userId)
                    .eventType("error")
                    .page(page)
                    .size(size)
                    .build();

            List<LogFileReader.LogEntry> entries = logFileReader.queryLogs(query);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", entries);
            response.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "total", entries.size()
            ));

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to query error logs", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to query error logs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("logDir", System.getProperty("user.dir") + "/logs");
        return ResponseEntity.ok(response);
    }
}
