package demo.k8s.agent.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户配额 Repository
 */
@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuota, String> {

    /**
     * 查找配额已用完的用户
     */
    @Query("SELECT uq FROM UserQuota uq WHERE uq.requestsUsedToday >= uq.maxRequestsPerDay OR uq.tokensUsedToday >= uq.maxTokensPerDay")
    List<UserQuota> findExceededQuotas();

    /**
     * 检查配额是否需要重置
     */
    @Query("SELECT uq FROM UserQuota uq WHERE uq.quotaResetAt IS NOT NULL AND uq.quotaResetAt < :now")
    List<UserQuota> findQuotasNeedingReset(@Param("now") Instant now);

    /**
     * 增加请求计数（原子操作）
     */
    @Modifying
    @Query("UPDATE UserQuota uq SET uq.requestsUsedToday = uq.requestsUsedToday + 1 WHERE uq.userId = :userId")
    int incrementRequestCount(@Param("userId") String userId);

    /**
     * 增加 Token 计数
     */
    @Modifying
    @Query("UPDATE UserQuota uq SET uq.tokensUsedToday = uq.tokensUsedToday + :tokens WHERE uq.userId = :userId")
    int incrementTokenCount(@Param("userId") String userId, @Param("tokens") int tokens);

    /**
     * 重置每日配额
     */
    @Modifying
    @Query("UPDATE UserQuota uq SET uq.requestsUsedToday = 0, uq.tokensUsedToday = 0, uq.quotaResetAt = :nextReset WHERE uq.userId = :userId")
    int resetDailyQuota(@Param("userId") String userId, @Param("nextReset") Instant nextReset);
}
