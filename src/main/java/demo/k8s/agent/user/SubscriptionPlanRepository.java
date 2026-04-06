package demo.k8s.agent.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 订阅套餐配置 Repository
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {

    /**
     * 查找所有激活的套餐
     */
    List<SubscriptionPlan> findByIsActiveTrue();

    /**
     * 按价格排序查找套餐
     */
    List<SubscriptionPlan> findByIsActiveTrueOrderByPriceCentsAsc();

    /**
     * 查找特定套餐
     */
    Optional<SubscriptionPlan> findByPlanIdAndIsActiveTrue(String planId);
}
