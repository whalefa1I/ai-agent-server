package demo.k8s.agent.scheduler;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 任务执行历史 Repository
 */
@Repository
public interface TaskExecutionHistoryRepository extends JpaRepository<TaskExecutionHistory, String> {

    /**
     * 按任务 ID 查询执行历史
     */
    Page<TaskExecutionHistory> findByTaskId(String taskId, Pageable pageable);

    /**
     * 按用户 ID 查询执行历史
     */
    Page<TaskExecutionHistory> findByUserId(String userId, Pageable pageable);

    /**
     * 按任务 ID 和状态查询
     */
    Page<TaskExecutionHistory> findByTaskIdAndStatus(String taskId, TaskExecutionHistory.ExecutionStatus status, Pageable pageable);

    /**
     * 按用户 ID 和状态查询
     */
    Page<TaskExecutionHistory> findByUserIdAndStatus(String userId, TaskExecutionHistory.ExecutionStatus status, Pageable pageable);

    /**
     * 查询最近执行失败的任务
     */
    @Query("SELECT h FROM TaskExecutionHistory h WHERE h.status = 'FAILED' " +
           "AND h.actualEndTime > :since ORDER BY h.actualEndTime DESC")
    List<TaskExecutionHistory> findRecentFailures(@Param("since") Instant since, Pageable pageable);

    /**
     * 按时间段查询执行历史
     */
    @Query("SELECT h FROM TaskExecutionHistory h WHERE h.userId = :userId " +
           "AND h.actualStartTime BETWEEN :startTime AND :endTime " +
           "ORDER BY h.actualStartTime DESC")
    Page<TaskExecutionHistory> findByUserIdAndTimeRange(
        @Param("userId") String userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    /**
     * 统计任务执行次数
     */
    long countByTaskId(String taskId);

    /**
     * 统计用户执行次数
     */
    long countByUserId(String userId);

    /**
     * 按状态统计执行次数
     */
    @Query("SELECT h.status, COUNT(h) FROM TaskExecutionHistory h WHERE h.userId = :userId GROUP BY h.status")
    List<Object[]> countStatusByUser(@Param("userId") String userId);

    /**
     * 按天统计执行次数
     */
    @Query("SELECT DATE(h.actualStartTime), COUNT(h) FROM TaskExecutionHistory h " +
           "WHERE h.userId = :userId AND h.actualStartTime >= :since " +
           "GROUP BY DATE(h.actualStartTime) ORDER BY DATE(h.actualStartTime)")
    List<Object[]> countByDay(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * 统计成功率
     */
    @Query("SELECT " +
           "SUM(CASE WHEN h.status = 'SUCCESS' THEN 1 ELSE 0 END) * 1.0 / COUNT(h) * 100 " +
           "FROM TaskExecutionHistory h WHERE h.userId = :userId")
    Double getSuccessRateByUser(@Param("userId") String userId);

    /**
     * 查找正在运行的任务执行
     */
    List<TaskExecutionHistory> findByStatus(TaskExecutionHistory.ExecutionStatus status);
}
