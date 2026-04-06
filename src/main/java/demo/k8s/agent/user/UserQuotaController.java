package demo.k8s.agent.user;

import demo.k8s.agent.user.QuotaService.QuotaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户配额管理 API
 */
@RestController
@RequestMapping("/api/user/quota")
public class UserQuotaController {

    private static final Logger log = LoggerFactory.getLogger(UserQuotaController.class);

    private final QuotaService quotaService;

    public UserQuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /**
     * 获取当前用户配额状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getQuotaStatus(
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestParam(required = false) String explicitUserId
    ) {
        String targetUserId = explicitUserId != null ? explicitUserId : userId;
        if (targetUserId == null || targetUserId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "MISSING_USER_ID",
                    "message", "请提供用户 ID（通过 X-User-ID 请求头或 userId 参数）"
            ));
        }

        QuotaStatus status = quotaService.getQuotaStatus(targetUserId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "userId", targetUserId,
                "requests", Map.of(
                        "used", status.requestsUsed(),
                        "max", status.maxRequests(),
                        "usagePercent", String.format("%.2f%%", status.requestUsagePercent())
                ),
                "tokens", Map.of(
                        "used", status.tokensUsed(),
                        "max", status.maxTokens(),
                        "usagePercent", String.format("%.2f%%", status.tokenUsagePercent())
                ),
                "nextReset", status.nextReset().toString()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * 升级用户套餐
     */
    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, Object>> upgradePlan(
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestParam(required = false) String explicitUserId,
            @RequestParam String planId
    ) {
        String targetUserId = explicitUserId != null ? explicitUserId : userId;
        if (targetUserId == null || targetUserId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "MISSING_USER_ID",
                    "message", "请提供用户 ID"
            ));
        }

        try {
            quotaService.upgradePlan(targetUserId, planId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "套餐升级成功");
            response.put("data", Map.of(
                    "userId", targetUserId,
                    "newPlanId", planId
            ));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "INVALID_PLAN_ID",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("套餐升级失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "UPGRADE_FAILED",
                    "message", "套餐升级失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 获取配额超限用户列表（管理员接口）
     */
    @GetMapping("/exceeded")
    public ResponseEntity<Map<String, Object>> getExceededQuotas() {
        try {
            List<UserQuota> exceeded = quotaService.getExceededQuotas();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", exceeded.stream().map(q -> Map.of(
                    "userId", q.getUserId(),
                    "requestsUsed", q.getRequestsUsedToday(),
                    "maxRequests", q.getMaxRequestsPerDay(),
                    "tokensUsed", q.getTokensUsedToday(),
                    "maxTokens", q.getMaxTokensPerDay()
            )).toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取超限用户列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "QUERY_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 手动重置用户配额（管理员接口）
     */
    @PostMapping("/reset/{userId}")
    public ResponseEntity<Map<String, Object>> resetQuota(@PathVariable String userId) {
        try {
            UserQuota quota = quotaService.getOrCreateQuota(userId);
            quota.resetDailyQuota();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "用户配额已重置");
            response.put("data", Map.of(
                    "userId", userId,
                    "requestsUsed", 0,
                    "tokensUsed", 0
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("重置用户配额失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "RESET_FAILED",
                    "message", e.getMessage()
            ));
        }
    }
}
