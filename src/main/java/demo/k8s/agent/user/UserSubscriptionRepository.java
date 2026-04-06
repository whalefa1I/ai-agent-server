package demo.k8s.agent.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户订阅 Repository
 */
@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, String> {

    /**
     * 查找所有活跃订阅
     */
    @Query("SELECT us FROM UserSubscription us WHERE us.cancelledAt IS NULL AND (us.expiresAt IS NULL OR us.expiresAt > CURRENT_TIMESTAMP)")
    List<UserSubscription> findActiveSubscriptions();

    /**
     * 根据套餐 ID 查找订阅
     */
    List<UserSubscription> findByPlanId(String planId);

    /**
     * 查找即将过期的订阅
     */
    @Query("SELECT us FROM UserSubscription us WHERE us.expiresAt IS NOT NULL AND us.expiresAt <= :threshold AND us.cancelledAt IS NULL")
    List<UserSubscription> findExpiringSubscriptions(@Param("threshold") java.time.Instant threshold);

    /**
     * 查找有 Stripe 客户 ID 的订阅
     */
    List<UserSubscription> findByStripeCustomerIdNotNull();

    /**
     * 查找已取消的订阅
     */
    @Query("SELECT us FROM UserSubscription us WHERE us.cancelledAt IS NOT NULL")
    List<UserSubscription> findCancelledSubscriptions();
}
