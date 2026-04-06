package demo.k8s.agent.scheduler;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 任务执行日志 Repository
 */
@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, String> {

    /**
     * 按执行 ID 查询日志
     */
    List<TaskExecutionLog> findByExecutionIdOrderByLoggedAt(String executionId);

    /**
     * 按任务 ID 查询日志
     */
    List<TaskExecutionLog> findByTaskIdOrderByLoggedAtDesc(String taskId, Pageable pageable);

    /**
     * 按用户 ID 查询日志
     */
    List<TaskExecutionLog> findByUserIdOrderByLoggedAtDesc(String userId, Pageable pageable);

    /**
     * 按时间段查询日志
     */
    @Query("SELECT l FROM TaskExecutionLog l WHERE l.userId = :userId " +
           "AND l.loggedAt BETWEEN :startTime AND :endTime " +
           "ORDER BY l.loggedAt DESC")
    List<TaskExecutionLog> findByUserIdAndTimeRange(
        @Param("userId") String userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    /**
     * 按日志级别查询
     */
    List<TaskExecutionLog> findByLogLevel(String logLevel, Pageable pageable);

    /**
     * 删除指定时间之前的日志
     */
    @Query("DELETE FROM TaskExecutionLog l WHERE l.loggedAt < :before")
    void deleteOlderThan(@Param("before") Instant before);
}
