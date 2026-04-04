package demo.k8s.agent.coordinator;

import java.time.Instant;
import java.util.Queue;

/**
 * 任务状态记录。
 *
 * @param taskId 任务 ID
 * @param name 任务名称
 * @param goal 任务目标
 * @param assignedTo 分配的 Agent 类型（general/explore/plan/bash）
 * @param status 当前状态
 * @param messages 收件箱消息队列
 * @param outputs 输出历史
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record TaskState(
        String taskId,
        String name,
        String goal,
        String assignedTo,
        TaskStatus status,
        Queue<String> messages,
        Queue<String> outputs,
        Instant createdAt,
        Instant updatedAt
) {}
