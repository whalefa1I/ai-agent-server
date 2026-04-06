package demo.k8s.agent.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 定时任务调度器配置
 */
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    @Value("${scheduler.thread-pool.size:10}")
    private int threadPoolSize;

    private final ScheduledTaskRepository taskRepository;
    private final TaskExecutionHistoryRepository executionHistoryRepository;
    private final TaskExecutionLogRepository executionLogRepository;
    private final ScheduledTaskService taskService;

    public SchedulerConfig(
            ScheduledTaskRepository taskRepository,
            TaskExecutionHistoryRepository executionHistoryRepository,
            TaskExecutionLogRepository executionLogRepository,
            ScheduledTaskService taskService) {
        this.taskRepository = taskRepository;
        this.executionHistoryRepository = executionHistoryRepository;
        this.executionLogRepository = executionLogRepository;
        this.taskService = taskService;
    }

    /**
     * 配置任务调度器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());

        // 注册定时任务扫描器 - 每分钟执行一次
        taskRegistrar.addTriggerTask(
                () -> {
                    log.debug("Scanning for scheduled tasks to execute...");
                    scanAndExecuteTasks();
                },
                triggerContext -> {
                    // 每分钟执行一次
                    Instant now = Instant.now();
                    return java.time.Instant.ofEpochMilli(
                            ((now.toEpochMilli() / 60000) + 1) * 60000
                    );
                }
        );

        // 注册清理任务 - 每天凌晨执行
        taskRegistrar.addTriggerTask(
                () -> {
                    log.info("Running cleanup task for old execution logs...");
                    cleanupOldLogs();
                },
                triggerContext -> {
                    // 每天凌晨 2 点执行
                    ZonedDateTime next = ZonedDateTime.now(ZoneId.systemDefault())
                            .withHour(2)
                            .withMinute(0)
                            .withSecond(0)
                            .withNano(0)
                            .plusDays(1);
                    return next.toInstant();
                }
        );

        log.info("Scheduler configured with {} threads", threadPoolSize);
    }

    /**
     * 任务调度器线程池
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(threadPoolSize);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 扫描并执行到期的任务
     */
    private void scanAndExecuteTasks() {
        try {
            List<ScheduledTask> activeTasks = taskRepository.findActiveTasks();
            log.debug("Found {} active scheduled tasks", activeTasks.size());

            for (ScheduledTask task : activeTasks) {
                try {
                    if (shouldExecuteNow(task)) {
                        executeTask(task);
                    }
                } catch (Exception e) {
                    log.error("Error executing scheduled task {}: {}", task.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error scanning scheduled tasks: {}", e.getMessage());
        }
    }

    /**
     * 判断任务是否应该现在执行
     */
    private boolean shouldExecuteNow(ScheduledTask task) {
        // 检查执行次数限制
        if (task.getMaxExecutions() > 0 && task.getExecutionCount() >= task.getMaxExecutions()) {
            return false;
        }

        // 检查时间范围
        Instant now = Instant.now();
        if (task.getStartAt() != null && task.getStartAt().isAfter(now)) {
            return false;
        }
        if (task.getEndAt() != null && task.getEndAt().isBefore(now)) {
            return false;
        }

        // 检查 Cron 表达式
        if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            return isCronDue(task.getCronExpression(), task.getTimezone());
        }

        // 固定延迟或固定频率模式
        if (task.getFixedDelayMs() != null || task.getFixedRateMs() != null) {
            // 简单实现：总是执行
            return true;
        }

        return false;
    }

    /**
     * 检查 Cron 表达式是否到期
     */
    private boolean isCronDue(String cronExpression, String timezone) {
        try {
            ZoneId zoneId = ZoneId.of(timezone != null ? timezone : "UTC");
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            // 解析 Cron 表达式 (简单的 5 字段格式：分 时 日 月 周)
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length != 5) {
                log.warn("Invalid cron expression (expected 5 fields): {}", cronExpression);
                return false;
            }

            int minute = Integer.parseInt(parts[0].equals("*") ? String.valueOf(now.getMinute()) : parts[0]);
            int hour = Integer.parseInt(parts[1].equals("*") ? String.valueOf(now.getHour()) : parts[1]);
            int dayOfMonth = Integer.parseInt(parts[2].equals("*") ? String.valueOf(now.getDayOfMonth()) : parts[2]);
            int month = Integer.parseInt(parts[3].equals("*") ? String.valueOf(now.getMonthValue()) : parts[3]);
            int dayOfWeek = Integer.parseInt(parts[4].equals("*") ? String.valueOf(now.getDayOfWeek().getValue()) : parts[4]);

            boolean minuteMatch = (parts[0].equals("*") || minute == now.getMinute());
            boolean hourMatch = (parts[1].equals("*") || hour == now.getHour());
            boolean dayMatch = (parts[2].equals("*") || dayOfMonth == now.getDayOfMonth());
            boolean monthMatch = (parts[3].equals("*") || month == now.getMonthValue());
            boolean dowMatch = (parts[4].equals("*") || dayOfWeek == now.getDayOfWeek().getValue());

            //  cron 匹配规则：月和日匹配 且 (日匹配 或 周匹配)
            return minuteMatch && hourMatch && monthMatch && (dayOfMonth == now.getDayOfMonth() || parts[2].equals("*") || parts[4].equals("*"));

        } catch (Exception e) {
            log.warn("Error parsing cron expression '{}': {}", cronExpression, e.getMessage());
            return false;
        }
    }

    /**
     * 执行单个任务
     */
    private void executeTask(ScheduledTask task) {
        log.info("Executing scheduled task: {} (user: {})", task.getTaskName(), task.getUserId());

        try {
            // 异步执行任务
            taskService.dryRunTask(task.getId(), task.getUserId());
        } catch (Exception e) {
            log.error("Failed to execute task {}: {}", task.getId(), e.getMessage());

            // 根据错误处理策略处理
            handleTaskError(task, e);
        }
    }

    /**
     * 处理任务执行错误
     */
    private void handleTaskError(ScheduledTask task, Exception e) {
        switch (task.getErrorHandling()) {
            case STOP:
                task.setStatus(ScheduledTask.TaskStatus.ERROR);
                task.setEnabled(false);
                taskRepository.save(task);
                log.warn("Task {} stopped due to error", task.getId());
                break;

            case RETRY:
                // 简单实现：记录重试
                log.info("Task {} will be retried next cycle", task.getId());
                break;

            case CONTINUE:
            default:
                // 继续执行下一次
                log.info("Task {} will continue on next cycle", task.getId());
                break;
        }
    }

    /**
     * 清理旧日志
     */
    private void cleanupOldLogs() {
        try {
            Instant thirtyDaysAgo = Instant.now().minusSeconds(30 * 24 * 60 * 60);
            executionLogRepository.deleteOlderThan(thirtyDaysAgo);
            log.info("Cleaned up execution logs older than 30 days");
        } catch (Exception e) {
            log.error("Error cleaning up old logs: {}", e.getMessage());
        }
    }

    /**
     * 后台执行器服务（用于异步任务执行）
     */
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r);
            t.setName("scheduled-task-executor-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }
}
