package demo.k8s.agent.scheduler;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 定时任务 Repository
 */
@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, String> {

    /**
     * 按用户 ID 查询任务
     */
    Page<ScheduledTask> findByUserId(String userId, Pageable pageable);

    /**
     * 按用户 ID 和状态查询任务
     */
    Page<ScheduledTask> findByUserIdAndStatus(String userId, ScheduledTask.TaskStatus status, Pageable pageable);

    /**
     * 查询所有启用的任务
     */
    List<ScheduledTask> findByEnabledTrue();

    /**
     * 查询所有活跃且启用的任务（用于调度器）
     */
    @Query("SELECT t FROM ScheduledTask t WHERE t.enabled = true AND t.status = 'ACTIVE' " +
           "AND (t.startAt IS NULL OR t.startAt <= CURRENT_TIMESTAMP) " +
           "AND (t.endAt IS NULL OR t.endAt > CURRENT_TIMESTAMP)")
    List<ScheduledTask> findActiveTasks();

    /**
     * 按用户 ID 查询启用的任务
     */
    List<ScheduledTask> findByUserIdAndEnabledTrue(String userId);

    /**
     * 按任务名称查询（用户级别）
     */
    Optional<ScheduledTask> findByUserIdAndTaskName(String userId, String taskName);

    /**
     * 统计用户任务数量
     */
    long countByUserId(String userId);

    /**
     * 统计用户活跃任务数量
     */
    long countByUserIdAndStatus(String userId, ScheduledTask.TaskStatus status);

    /**
     * 查询所有用户的任务统计
     */
    @Query("SELECT t.userId, t.status, COUNT(t) FROM ScheduledTask t GROUP BY t.userId, t.status")
    List<Object[]> countTaskStatusByUser();

    /**
     * 按任务类型统计
     */
    @Query("SELECT t.taskType, COUNT(t) FROM ScheduledTask t GROUP BY t.taskType")
    List<Object[]> countTaskType();
}
