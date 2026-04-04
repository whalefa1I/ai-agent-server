package demo.k8s.agent.web;

import demo.k8s.agent.observability.ModelCallMetrics;
import demo.k8s.agent.observability.SessionStats;
import demo.k8s.agent.observability.ToolCallMetrics;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 可观测性 HTTP 端点，提供统计查询和指标导出。
 * TODO: Prometheus 相关功能暂时注释
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ObservabilityController {

    private final SessionStats sessionStats;

    public ObservabilityController(SessionStats sessionStats) {
        this.sessionStats = sessionStats;
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "timestamp", String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 获取会话统计摘要
     */
    @GetMapping("/stats")
    public SessionStats.StatsSnapshot getStats() {
        return sessionStats.getSnapshot();
    }

    /**
     * 获取 Prometheus 格式的指标
     * TODO: 暂时返回空字符串
     */
    @GetMapping(value = "/metrics", produces = "text/plain; version=0.0.4; charset=utf-8")
    public String getPrometheusMetrics() {
        return "# Prometheus metrics temporarily disabled\n";
    }

    /**
     * 获取最近的工具调用历史
     */
    @GetMapping("/tool-calls")
    public List<ToolCallMetrics> getRecentToolCalls(
            @RequestParam(defaultValue = "20") int limit) {
        List<ToolCallMetrics> all = sessionStats.getRecentToolCalls();
        return all.stream().limit(limit).toList();
    }

    /**
     * 获取最近的模型调用历史
     */
    @GetMapping("/model-calls")
    public List<ModelCallMetrics> getRecentModelCalls(
            @RequestParam(defaultValue = "10") int limit) {
        List<ModelCallMetrics> all = sessionStats.getRecentModelCalls();
        return all.stream().limit(limit).toList();
    }

    /**
     * 重置会话统计（用于调试）
     */
    @PostMapping("/reset")
    public Map<String, Object> resetStats() {
        return Map.of(
                "message", "SessionStats is @SessionScope, cannot reset manually.",
                "sessionId", sessionStats.getSessionId()
        );
    }
}
