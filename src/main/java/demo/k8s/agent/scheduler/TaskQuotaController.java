package demo.k8s.agent.scheduler;

import demo.k8s.agent.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务配额管理 API 控制器
 */
@RestController
@RequestMapping("/api/scheduler/quota")
public class TaskQuotaController {

    private static final Logger log = LoggerFactory.getLogger(TaskQuotaController.class);

    private final TaskQuotaService quotaService;

    public TaskQuotaController(TaskQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /**
     * 获取用户配额状态
     */
    @GetMapping("/status")
    public ResponseEntity<TaskQuotaService.QuotaStatus> getQuotaStatus(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        TaskQuotaService.QuotaStatus status = quotaService.getQuotaStatus(userId);
        return ResponseEntity.ok(status);
    }

    /**
     * 检查能否创建任务
     */
    @GetMapping("/check/create")
    public ResponseEntity<TaskQuotaService.QuotaCheckResult> checkCanCreateTask(
            @RequestParam String taskType,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        TaskQuotaService.TaskType type = TaskQuotaService.TaskType.valueOf(taskType.toUpperCase());
        TaskQuotaService.QuotaCheckResult result = quotaService.canCreateTask(userId, type);

        if (!result.isAllowed()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 检查能否执行任务
     */
    @GetMapping("/check/execute")
    public ResponseEntity<TaskQuotaService.QuotaCheckResult> checkCanExecuteTask(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        TaskQuotaService.QuotaCheckResult result = quotaService.canExecuteTask(userId);

        if (!result.isAllowed()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取可用套餐列表
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanTaskQuota>> getAvailablePlans() {
        List<PlanTaskQuota> plans = quotaService.getAllowedPlans();
        return ResponseEntity.ok(plans);
    }

    /**
     * 升级套餐
     */
    @PostMapping("/upgrade")
    public ResponseEntity<UserTaskQuota> upgradePlan(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        String newPlanId = request.get("planId");

        log.info("User {} upgrading to plan: {}", userId, newPlanId);
        UserTaskQuota updatedQuota = quotaService.upgradePlan(userId, newPlanId);

        return ResponseEntity.ok(updatedQuota);
    }

    /**
     * 验证 Cron 精度是否符合配额
     */
    @GetMapping("/check/cron")
    public ResponseEntity<TaskQuotaService.QuotaCheckResult> checkCronPrecision(
            @RequestParam String cronExpression,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        TaskQuotaService.QuotaCheckResult result =
                quotaService.isValidCronPrecision(userId, cronExpression);

        if (!result.isAllowed()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 验证 Heartbeat 间隔是否符合配额
     */
    @GetMapping("/check/heartbeat")
    public ResponseEntity<TaskQuotaService.QuotaCheckResult> checkHeartbeatInterval(
            @RequestParam long intervalMs,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        TaskQuotaService.QuotaCheckResult result =
                quotaService.isValidHeartbeatInterval(userId, intervalMs);

        if (!result.isAllowed()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 验证 Payload 大小是否符合配额
     */
    @GetMapping("/check/payload")
    public ResponseEntity<TaskQuotaService.QuotaCheckResult> checkPayloadSize(
            @RequestParam int payloadSizeBytes,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        TaskQuotaService.QuotaCheckResult result =
                quotaService.isValidPayloadSize(userId, payloadSizeBytes);

        if (!result.isAllowed()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
