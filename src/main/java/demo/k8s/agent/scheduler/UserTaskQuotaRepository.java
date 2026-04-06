package demo.k8s.agent.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户任务配额 Repository
 */
@Repository
public interface UserTaskQuotaRepository extends JpaRepository<UserTaskQuota, String> {

    /**
     * 按用户 ID 查询配额
     */
    Optional<UserTaskQuota> findByUserId(String userId);

    /**
     * 检查用户配额是否存在
     */
    boolean existsByUserId(String userId);

    /**
     * 查询所有启用的配额
     */
    List<UserTaskQuota> findByEnabledTrue();

    /**
     * 查询指定套餐的所有用户配额
     */
    List<UserTaskQuota> findByPlanId(String planId);

    /**
     * 查询配额超限的用户
     */
    @Query("SELECT q FROM UserTaskQuota q WHERE q.enabled = true AND q.quotaExceededAction != 'NOTIFY'")
    List<UserTaskQuota> findUsersWithBlockingQuota();

    /**
     * 统计各套餐用户数量
     */
    @Query("SELECT q.planId, COUNT(q) FROM UserTaskQuota q GROUP BY q.planId")
    List<Object[]> countUsersByPlan();
}
