package demo.k8s.agent.coordinator;

import demo.k8s.agent.toolsystem.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 子 Agent 工具定义，与 Claude Code 的 AgentTool 对齐。
 * <p>
 * 提供以下工具：
 * <ul>
 *   <li>{@code task} - 委派任务给子 Agent</li>
 *   <li>{@code send_message} - 发送消息给任务</li>
 *   <li>{@code task_stop} - 停止任务</li>
 * </ul>
 */
@Component
public class SubagentTools {

    private static final Logger log = LoggerFactory.getLogger(SubagentTools.class);

    private final AsyncSubagentExecutor asyncSubagentExecutor;
    private final CoordinatorState coordinatorState;

    public SubagentTools(
            AsyncSubagentExecutor asyncSubagentExecutor,
            CoordinatorState coordinatorState) {
        this.asyncSubagentExecutor = asyncSubagentExecutor;
        this.coordinatorState = coordinatorState;
    }

    /**
     * 委派任务给子 Agent
     *
     * @param name 任务名称
     * @param goal 任务目标
     * @param subagentType 子 Agent 类型（general/explore/plan/bash）
     * @param runInBackground 是否后台执行
     * @return 任务输出
     */
    public String createTask(
            String name,
            String goal,
            String subagentType,
            boolean runInBackground) {

        log.info("创建任务：name={}, goal={}, type={}, background={}",
                name, truncate(goal, 50), subagentType, runInBackground);

        try {
            if (runInBackground) {
                // 后台执行：立即返回 task_id
                CoordinatorState.TaskHandle handle =
                        asyncSubagentExecutor.spawnBackgroundAgent(name, goal, subagentType);

                return String.format(
                        "任务已后台启动。task_id=%s, name=%s, assignedTo=%s%n" +
                        "使用 send_message 发送跟进消息，使用 task_stop 停止任务。",
                        handle.taskId(), name, subagentType);

            } else {
                // 同步执行：等待完成
                CoordinatorState.TaskResult result =
                        asyncSubagentExecutor.runSynchronousAgent(
                                name, goal, subagentType, Duration.ofMinutes(10));

                if (result.error() != null && !result.error().equals("stopped")) {
                    return String.format("任务失败：%s (错误：%s)", name, result.error());
                }

                return String.format(
                        "任务完成：%s%n%s",
                        name,
                        result.output() != null ? result.output() : "(无输出)");
            }

        } catch (Exception e) {
            log.error("创建任务失败：{}", name, e);
            return String.format("任务失败：%s (错误：%s)", name, e.getMessage());
        }
    }

    /**
     * 发送消息给任务
     *
     * @param taskId 任务 ID
     * @param message 消息内容
     * @return 确认信息
     */
    public String sendMessage(String taskId, String message) {
        log.info("发送消息给任务：taskId={}, message={}", taskId, truncate(message, 50));

        if (!coordinatorState.hasTask(taskId)) {
            return "错误：未找到任务 " + taskId;
        }

        coordinatorState.sendMessage(taskId, message);

        return String.format("消息已发送到任务 %s。Worker 将在下一个轮次处理。", taskId);
    }

    /**
     * 停止任务
     *
     * @param taskId 任务 ID
     * @return 确认信息
     */
    public String stopTask(String taskId) {
        log.info("停止任务：{}", taskId);

        if (!coordinatorState.hasTask(taskId)) {
            return "错误：未找到任务 " + taskId;
        }

        coordinatorState.stopTask(taskId);

        return String.format("任务 %s 已停止。", taskId);
    }

    /**
     * 获取任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态 JSON
     */
    public String getTaskStatus(String taskId) {
        var taskOpt = coordinatorState.getTask(taskId);

        if (taskOpt.isEmpty()) {
            return "错误：未找到任务 " + taskId;
        }

        var task = taskOpt.get();
        return String.format(
                "任务状态:%n" +
                "  task_id: %s%n" +
                "  name: %s%n" +
                "  status: %s%n" +
                "  assignedTo: %s%n" +
                "  createdAt: %s%n" +
                "  updatedAt: %s",
                task.taskId(),
                task.name(),
                task.status(),
                task.assignedTo(),
                task.createdAt(),
                task.updatedAt());
    }

    /**
     * 获取所有活跃任务
     *
     * @return 任务列表
     */
    public String listActiveTasks() {
        var tasks = coordinatorState.getActiveTasks();

        if (tasks.isEmpty()) {
            return "当前没有活跃任务。";
        }

        StringBuilder sb = new StringBuilder("活跃任务:\n");
        for (var task : tasks) {
            sb.append(String.format(
                    "  - [%s] %s (status=%s, assignedTo=%s)%n",
                    task.taskId(),
                    task.name(),
                    task.status(),
                    task.assignedTo()));
        }
        return sb.toString();
    }

    /**
     * 获取协调器统计
     *
     * @return 统计信息
     */
    public String getCoordinatorStats() {
        var stats = coordinatorState.getStats();
        return String.format(
                "协调器统计:%n" +
                "  活跃任务：%d%n" +
                "  已完成任务：%d%n" +
                "  运行中：%d%n" +
                "  等待中：%d",
                stats.activeTasks(),
                stats.completedTasks(),
                stats.runningTasks(),
                stats.waitingTasks());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
