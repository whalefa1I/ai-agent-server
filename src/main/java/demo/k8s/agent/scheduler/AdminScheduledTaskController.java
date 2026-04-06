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
 * 定时任务管理 API 控制器（管理员用）
 */
@RestController
@RequestMapping("/api/admin/scheduler")
public class AdminScheduledTaskController {

    private static final Logger log = LoggerFactory.getLogger(AdminScheduledTaskController.class);

    private final ScheduledTaskService taskService;
    private final TaskQuotaService quotaService;

    public AdminScheduledTaskController(ScheduledTaskService taskService, TaskQuotaService quotaService) {
        this.taskService = taskService;
        this.quotaService = quotaService;
    }

    /**
     * 获取所有用户任务汇总统计
     */
    @GetMapping("/stats/aggregate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScheduledTaskService.AggregateStatistics> getAggregateStatistics() {
        ScheduledTaskService.AggregateStatistics stats = taskService.getAggregateStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * 查询所有用户的任务（管理员）
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status) {

        var pageable = org.springframework.data.domain.PageRequest.of(page, size);

        org.springframework.data.domain.Page<ScheduledTask> taskPage;
        if (userId != null) {
            taskPage = taskService.getUserTasks(userId, page, size, "createdAt", "desc");
        } else {
            // 如果没有指定 userId，返回空页面或所有任务
            // 这里可以根据需要扩展 Service 层方法
            taskPage = org.springframework.data.domain.Page.empty();
        }

        return ResponseEntity.ok(Map.of(
                "content", taskPage.getContent(),
                "totalElements", taskPage.getTotalElements(),
                "totalPages", taskPage.getTotalPages(),
                "number", taskPage.getNumber(),
                "size", taskPage.getSize()
        ));
    }

    /**
     * 按用户 ID 查询任务统计
     */
    @GetMapping("/stats/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScheduledTaskService.TaskStatistics> getUserStatistics(
            @PathVariable String userId) {
        ScheduledTaskService.TaskStatistics stats = taskService.getUserStatistics(userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 查询所有用户的执行历史
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllExecutionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status) {

        org.springframework.data.domain.Page<TaskExecutionHistory> historyPage;

        if (userId != null) {
            if (status != null) {
                historyPage = taskService.getUserExecutionHistory(userId, page, size);
                // 需要扩展 Service 层支持按状态过滤
            } else {
                historyPage = taskService.getUserExecutionHistory(userId, page, size);
            }
        } else {
            historyPage = taskService.getUserExecutionHistory("", page, size);
        }

        return ResponseEntity.ok(Map.of(
                "content", historyPage.getContent(),
                "totalElements", historyPage.getTotalElements(),
                "totalPages", historyPage.getTotalPages(),
                "number", historyPage.getNumber(),
                "size", historyPage.getSize()
        ));
    }

    /**
     * 强制删除任务（管理员）
     */
    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTaskAdmin(@PathVariable String taskId) {
        // 需要扩展 Service 层支持管理员删除任意任务
        log.warn("Admin delete task requested: {} - not implemented yet", taskId);
        return ResponseEntity.notFound().build();
    }

    /**
     * 获取最近失败的任务执行
     */
    @GetMapping("/history/recent-failures")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TaskExecutionHistory>> getRecentFailures(
            @RequestParam(defaultValue = "24") int hoursAgo) {

        // 需要扩展 Repository 层
        log.warn("Recent failures endpoint not implemented yet");
        return ResponseEntity.ok(List.of());
    }

    /**
     * 获取所有用户配额使用汇总
     */
    @GetMapping("/quota/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getQuotaSummary() {
        // 需要扩展 Service 层方法
        return ResponseEntity.ok(Map.of(
                "message", "Not implemented yet",
                "totalUsers", 0,
                "usersByPlan", Map.of()
        ));
    }

    /**
     * 查询指定用户的配额状态
     */
    @GetMapping("/quota/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaskQuotaService.QuotaStatus> getUserQuota(
            @PathVariable String userId) {
        TaskQuotaService.QuotaStatus status = quotaService.getQuotaStatus(userId);
        return ResponseEntity.ok(status);
    }

    /**
     * 获取所有可用套餐
     */
    @GetMapping("/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PlanTaskQuota>> getAllPlans() {
        List<PlanTaskQuota> plans = quotaService.getAllowedPlans();
        return ResponseEntity.ok(plans);
    }
}
