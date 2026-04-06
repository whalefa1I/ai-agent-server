package demo.k8s.agent.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 套餐任务配额 Repository
 */
@Repository
public interface PlanTaskQuotaRepository extends JpaRepository<PlanTaskQuota, String> {

    /**
     * 查询所有激活的套餐
     */
    List<PlanTaskQuota> findByIsActiveTrue();

    /**
     * 按价格排序查询套餐
     */
    List<PlanTaskQuota> findByIsActiveTrueOrderByPriceCentsAsc();

    /**
     * 查询特定价格区间的套餐
     */
    @Query("SELECT p FROM PlanTaskQuota p WHERE p.isActive = true " +
           "AND p.priceCents BETWEEN :minPrice AND :maxPrice " +
           "ORDER BY p.priceCents ASC")
    List<PlanTaskQuota> findPlansByPriceRange(
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice
    );
}
