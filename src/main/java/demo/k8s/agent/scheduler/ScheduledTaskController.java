package demo.k8s.agent.scheduler;

import demo.k8s.agent.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务 API 控制器
 */
@RestController
@RequestMapping("/api/scheduler/tasks")
public class ScheduledTaskController {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskController.class);

    private final ScheduledTaskService taskService;

    public ScheduledTaskController(ScheduledTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 创建定时任务
     */
    @PostMapping
    public ResponseEntity<ScheduledTask> createTask(
            @RequestBody ScheduledTaskService.TaskCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        log.info("Creating scheduled task for user: {}", userId);

        ScheduledTask task = taskService.createTask(request, userId);
        return ResponseEntity.ok(task);
    }

    /**
     * 更新定时任务
     */
    @PutMapping("/{taskId}")
    public ResponseEntity<ScheduledTask> updateTask(
            @PathVariable String taskId,
            @RequestBody ScheduledTaskService.TaskUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        log.info("Updating scheduled task: {} for user: {}", taskId, userId);

        ScheduledTask task = taskService.updateTask(taskId, request, userId);
        return ResponseEntity.ok(task);
    }

    /**
     * 删除定时任务
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        log.info("Deleting scheduled task: {} for user: {}", taskId, userId);

        taskService.deleteTask(taskId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<ScheduledTask> getTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ScheduledTask task = taskService.getTask(taskId, userId);
        return ResponseEntity.ok(task);
    }

    /**
     * 分页查询用户任务列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        var taskPage = taskService.getUserTasks(userId, page, size, sortBy, direction);

        return ResponseEntity.ok(Map.of(
                "content", taskPage.getContent(),
                "totalElements", taskPage.getTotalElements(),
                "totalPages", taskPage.getTotalPages(),
                "number", taskPage.getNumber(),
                "size", taskPage.getSize(),
                "first", taskPage.isFirst(),
                "last", taskPage.isLast()
        ));
    }

    /**
     * 查询用户所有任务（不分页）
     */
    @GetMapping("/all")
    public ResponseEntity<List<ScheduledTask>> getAllUserTasks(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        List<ScheduledTask> tasks = taskService.getAllUserTasks(userId);
        return ResponseEntity.ok(tasks);
    }

    /**
     * 启用任务
     */
    @PostMapping("/{taskId}/enable")
    public ResponseEntity<ScheduledTask> enableTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ScheduledTask task = taskService.enableTask(taskId, userId);
        return ResponseEntity.ok(task);
    }

    /**
     * 暂停任务
     */
    @PostMapping("/{taskId}/pause")
    public ResponseEntity<ScheduledTask> pauseTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ScheduledTask task = taskService.pauseTask(taskId, userId);
        return ResponseEntity.ok(task);
    }

    /**
     * 恢复任务
     */
    @PostMapping("/{taskId}/resume")
    public ResponseEntity<ScheduledTask> resumeTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ScheduledTask task = taskService.resumeTask(taskId, userId);
        return ResponseEntity.ok(task);
    }

    /**
     * 取消任务
     */
    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<ScheduledTask> cancelTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ScheduledTask task = taskService.cancelTask(taskId, userId);
        return ResponseEntity.ok(task);
    }

    /**
     * 试运行任务
     */
    @PostMapping("/{taskId}/dry-run")
    public ResponseEntity<ScheduledTaskService.TaskExecutionResult> dryRunTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ScheduledTaskService.TaskExecutionResult result = taskService.dryRunTask(taskId, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询任务执行历史
     */
    @GetMapping("/{taskId}/history")
    public ResponseEntity<Map<String, Object>> getTaskExecutionHistory(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        var historyPage = taskService.getTaskExecutionHistory(taskId, userId, page, size);

        return ResponseEntity.ok(Map.of(
                "content", historyPage.getContent(),
                "totalElements", historyPage.getTotalElements(),
                "totalPages", historyPage.getTotalPages(),
                "number", historyPage.getNumber(),
                "size", historyPage.getSize()
        ));
    }

    /**
     * 查询用户执行历史
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getUserExecutionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        var historyPage = taskService.getUserExecutionHistory(userId, page, size);

        return ResponseEntity.ok(Map.of(
                "content", historyPage.getContent(),
                "totalElements", historyPage.getTotalElements(),
                "totalPages", historyPage.getTotalPages(),
                "number", historyPage.getNumber(),
                "size", historyPage.getSize()
        ));
    }

    /**
     * 获取执行日志
     */
    @GetMapping("/history/{executionId}/logs")
    public ResponseEntity<List<TaskExecutionLog>> getExecutionLogs(
            @PathVariable String executionId) {

        List<TaskExecutionLog> logs = taskService.getExecutionLogs(executionId);
        return ResponseEntity.ok(logs);
    }

    /**
     * 获取用户任务统计
     */
    @GetMapping("/stats")
    public ResponseEntity<ScheduledTaskService.TaskStatistics> getUserStatistics(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = UserContext.getUserIdFromJwt(jwt);
        ScheduledTaskService.TaskStatistics stats = taskService.getUserStatistics(userId);
        return ResponseEntity.ok(stats);
    }
}
