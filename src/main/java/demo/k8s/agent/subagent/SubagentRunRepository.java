package demo.k8s.agent.subagent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 子 Agent 运行记录仓库（v1 M5 持久化）。
 */
public interface SubagentRunRepository extends JpaRepository<SubagentRun, String> {

    /**
     * 按会话 ID 和状态查询（用于恢复）
     */
    List<SubagentRun> findBySessionIdAndStatusIn(String sessionId, List<SubagentRun.RunStatus> statuses);

    /**
     * 按状态列表查询（启动恢复、全量 reconcile 时避免扫描已完成记录）
     */
    List<SubagentRun> findByStatusIn(List<SubagentRun.RunStatus> statuses);

    /**
     * 按租户 ID 和状态查询（多租户隔离）
     */
    List<SubagentRun> findByTenantIdAndStatusIn(String tenantId, List<SubagentRun.RunStatus> statuses);

    /**
     * 查找运行中的任务（用于 reconcile）
     */
    @Query("SELECT r FROM SubagentRun r WHERE r.status = 'RUNNING' AND r.deadlineAt < :now")
    List<SubagentRun> findOverdueRuns(@Param("now") Instant now);

    /**
     * 按运行 ID 和会话 ID 查询（额外鉴权）
     */
    Optional<SubagentRun> findByRunIdAndSessionId(String runId, String sessionId);

    /**
     * 按父运行 ID 查询子任务
     */
    List<SubagentRun> findByParentRunId(String parentRunId);

    /**
     * 统计会话中运行中的任务数
     */
    int countBySessionIdAndStatusIn(String sessionId, List<SubagentRun.RunStatus> statuses);

    /**
     * 运维列表：按会话最近创建的运行记录
     */
    List<SubagentRun> findTop50BySessionIdOrderByCreatedAtDesc(String sessionId);

    /**
     * 按批次 ID 查询所有子任务（Fan-in 场景）
     */
    List<SubagentRun> findByBatchId(String batchId);

    /**
     * 按批次与会话查询（避免跨会话 batchId 碰撞）
     */
    List<SubagentRun> findByBatchIdAndSessionId(String batchId, String sessionId);

    /**
     * 统计批次中 pending/running 状态的任务数（用于检测批次是否全部完成）
     */
    int countByBatchIdAndStatusIn(String batchId, List<SubagentRun.RunStatus> statuses);

    /**
     * 按批次、会话与状态统计（与会话隔离）
     */
    int countByBatchIdAndSessionIdAndStatusIn(String batchId, String sessionId, List<SubagentRun.RunStatus> statuses);

    /**
     * 按主运行 ID 查询（用于查找需要唤醒的主 Agent）
     */
    List<SubagentRun> findByMainRunId(String mainRunId);
}
