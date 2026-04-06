package demo.k8s.agent.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * API Key 配额计划 Repository
 */
@Repository
public interface ApiKeyQuotaPlanRepository extends JpaRepository<ApiKeyQuotaPlan, String> {

    /**
     * 查询所有激活的计划
     */
    List<ApiKeyQuotaPlan> findByIsActiveTrueOrderByPriceCentsAsc();

    /**
     * 查询所有高级计划
     */
    List<ApiKeyQuotaPlan> findByIsPremiumTrueAndIsActiveTrue();

    /**
     * 按价格区间查询计划
     */
    @Query("SELECT p FROM ApiKeyQuotaPlan p WHERE p.isActive = true " +
           "AND p.priceCents BETWEEN :minPrice AND :maxPrice " +
           "ORDER BY p.priceCents ASC")
    List<ApiKeyQuotaPlan> findPlansByPriceRange(
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice
    );
}
