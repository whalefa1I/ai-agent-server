package demo.k8s.agent.analytics;

import demo.k8s.agent.analytics.UserFunnelTracker.FunnelState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户漏斗分析 API
 */
@RestController
@RequestMapping("/api/analytics/funnel")
public class FunnelAnalyticsController {

    private final UserFunnelTracker funnelTracker;

    public FunnelAnalyticsController(UserFunnelTracker funnelTracker) {
        this.funnelTracker = funnelTracker;
    }

    /**
     * 获取漏斗统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getFunnelStats() {
        Map<String, Object> stats = funnelTracker.getFunnelStats();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", stats);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户漏斗状态
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserFunnelState(@PathVariable String userId) {
        FunnelState state = funnelTracker.getFunnelState(userId);

        if (state == null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", null,
                    "message", "用户暂无漏斗记录"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "userId", state.userId(),
                "currentStage", state.currentStage().name(),
                "visitAt", state.visitAt() != null ? state.visitAt().toString() : null,
                "registerAt", state.registerAt() != null ? state.registerAt().toString() : null,
                "activateAt", state.activateAt() != null ? state.activateAt().toString() : null,
                "firstRequestAt", state.firstRequestAt() != null ? state.firstRequestAt().toString() : null,
                "retainedAt", state.retainedAt() != null ? state.retainedAt().toString() : null
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * 追踪用户访问事件
     */
    @PostMapping("/track/visit")
    public ResponseEntity<Map<String, Object>> trackVisit(
            @RequestParam String userId,
            @RequestBody(required = false) Map<String, Object> metadata
    ) {
        funnelTracker.trackVisit(userId, metadata);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "访问事件已记录");

        return ResponseEntity.ok(response);
    }

    /**
     * 追踪用户注册事件
     */
    @PostMapping("/track/register")
    public ResponseEntity<Map<String, Object>> trackRegister(
            @RequestParam String userId,
            @RequestBody(required = false) Map<String, Object> metadata
    ) {
        funnelTracker.trackRegister(userId, metadata);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "注册事件已记录");

        return ResponseEntity.ok(response);
    }

    /**
     * 追踪用户激活事件
     */
    @PostMapping("/track/activate")
    public ResponseEntity<Map<String, Object>> trackActivate(
            @RequestParam String userId,
            @RequestBody(required = false) Map<String, Object> metadata
    ) {
        funnelTracker.trackActivate(userId, metadata);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "激活事件已记录");

        return ResponseEntity.ok(response);
    }

    /**
     * 追踪用户首次请求事件
     */
    @PostMapping("/track/first-request")
    public ResponseEntity<Map<String, Object>> trackFirstRequest(
            @RequestParam String userId,
            @RequestBody(required = false) Map<String, Object> metadata
    ) {
        funnelTracker.trackFirstRequest(userId, metadata);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "首次请求事件已记录");

        return ResponseEntity.ok(response);
    }

    /**
     * 追踪用户留存事件
     */
    @PostMapping("/track/retained")
    public ResponseEntity<Map<String, Object>> trackRetained(
            @RequestParam String userId,
            @RequestBody(required = false) Map<String, Object> metadata
    ) {
        funnelTracker.trackRetained(userId, metadata);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "留存事件已记录");

        return ResponseEntity.ok(response);
    }
}
