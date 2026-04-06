package demo.k8s.agent.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API Key Repository
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    /**
     * 按 Key Hash 查询
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * 按用户 ID 查询所有 Key
     */
    List<ApiKey> findByUserId(String userId);

    /**
     * 按用户 ID 和状态查询
     */
    List<ApiKey> findByUserIdAndStatus(String userId, ApiKey.KeyStatus status);

    /**
     * 查询所有启用的 Key
     */
    List<ApiKey> findByEnabledTrue();

    /**
     * 查询活跃且启用的 Key
     */
    @Query("SELECT k FROM ApiKey k WHERE k.enabled = true AND k.status = 'ACTIVE' " +
           "AND (k.expiresAt IS NULL OR k.expiresAt > CURRENT_TIMESTAMP)")
    List<ApiKey> findActiveKeys();

    /**
     * 按用户 ID 查询活跃 Key
     */
    @Query("SELECT k FROM ApiKey k WHERE k.userId = :userId AND k.enabled = true " +
           "AND k.status = 'ACTIVE' AND (k.expiresAt IS NULL OR k.expiresAt > CURRENT_TIMESTAMP)")
    List<ApiKey> findActiveKeysByUser(@Param("userId") String userId);

    /**
     * 按前缀查询 Key (用于管理界面展示)
     */
    List<ApiKey> findByKeyPrefixStartingWith(String prefix);

    /**
     * 统计用户 Key 数量
     */
    long countByUserId(String userId);

    /**
     * 统计活跃 Key 数量
     */
    long countByEnabledTrueAndStatus(ApiKey.KeyStatus status);

    /**
     * 查询即将过期的 Key
     */
    @Query("SELECT k FROM ApiKey k WHERE k.expiresAt IS NOT NULL " +
           "AND k.expiresAt BETWEEN CURRENT_TIMESTAMP AND :futureDate " +
           "AND k.enabled = true AND k.status = 'ACTIVE'")
    List<ApiKey> findExpiringKeys(@Param("futureDate") java.time.Instant futureDate);

    /**
     * 查询今日使用量高的 Key
     */
    @Query("SELECT k FROM ApiKey k WHERE k.requestsToday > :threshold " +
           "ORDER BY k.requestsToday DESC")
    List<ApiKey> findHighUsageKeys(@Param("threshold") int threshold);
}
