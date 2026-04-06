package demo.k8s.agent.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 配额管理服务
 *
 * 负责用户配额检查、扣减、重置等操作
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final UserQuotaRepository quotaRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;

    public QuotaService(
            UserQuotaRepository quotaRepository,
            UserSubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository) {
        this.quotaRepository = quotaRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    /**
     * 获取或创建用户配额
     * 新用户会自动根据订阅套餐创建默认配额
     */
    @Transactional
    public UserQuota getOrCreateQuota(String userId) {
        return quotaRepository.findById(userId)
                .orElseGet(() -> {
                    // 获取用户订阅
                    UserSubscription subscription = subscriptionRepository.findById(userId).orElse(null);
                    String planId = subscription != null ? subscription.getPlanId() : "free";

                    // 根据套餐创建配额
                    SubscriptionPlan plan = planRepository.findByPlanIdAndIsActiveTrue(planId)
                            .orElseGet(() -> planRepository.findByPlanIdAndIsActiveTrue("free").orElse(null));

                    UserQuota quota = new UserQuota(userId);
                    if (plan != null) {
                        quota.setMaxRequestsPerDay(plan.getMaxRequestsPerDay());
                        quota.setMaxTokensPerDay(plan.getMaxTokensPerDay());
                        quota.setMaxConcurrentSessions(plan.getMaxConcurrentSessions());
                        quota.setMaxFileSizeBytes(plan.getMaxFileSizeBytes());
                    }
                    quota.setQuotaResetAt(Instant.now().plus(1, ChronoUnit.DAYS));

                    log.info("为用户 {} 创建配额，套餐：{}", userId, planId);
                    return quotaRepository.save(quota);
                });
    }

    /**
     * 检查并增加请求计数
     * @return true 如果成功，false 如果配额不足
     */
    @Transactional
    public boolean tryIncrementRequest(String userId) {
        UserQuota quota = getOrCreateQuota(userId);

        // 检查是否需要重置
        if (quota.needsReset()) {
            quota.resetDailyQuota();
            log.info("用户 {} 配额已重置", userId);
        }

        // 检查配额
        if (quota.getRequestsUsedToday() >= quota.getMaxRequestsPerDay()) {
            log.warn("用户 {} 请求配额已用完：{}/{}", userId,
                    quota.getRequestsUsedToday(), quota.getMaxRequestsPerDay());
            return false;
        }

        quota.setRequestsUsedToday(quota.getRequestsUsedToday() + 1);
        quotaRepository.save(quota);
        log.debug("用户 {} 请求计数：{}", userId, quota.getRequestsUsedToday());
        return true;
    }

    /**
     * 检查并增加 Token 计数
     * @param userId 用户 ID
     * @param tokens Token 数量
     * @return true 如果成功，false 如果配额不足
     */
    @Transactional
    public boolean tryIncrementTokens(String userId, int tokens) {
        UserQuota quota = getOrCreateQuota(userId);

        // 检查是否需要重置
        if (quota.needsReset()) {
            quota.resetDailyQuota();
        }

        // 检查配额
        if (quota.getTokensUsedToday() + tokens > quota.getMaxTokensPerDay()) {
            log.warn("用户 {} Token 配额已用完：{}/{}", userId,
                    quota.getTokensUsedToday(), quota.getMaxTokensPerDay());
            return false;
        }

        quota.setTokensUsedToday(quota.getTokensUsedToday() + tokens);
        quotaRepository.save(quota);
        log.debug("用户 {} Token 计数：{} (+{})", userId, quota.getTokensUsedToday(), tokens);
        return true;
    }

    /**
     * 记录请求和 Token 使用
     * 用于统计和审计
     */
    @Transactional
    public void recordUsage(String userId, int tokens) {
        // 原子更新
        quotaRepository.incrementRequestCount(userId);
        if (tokens > 0) {
            quotaRepository.incrementTokenCount(userId, tokens);
        }
    }

    /**
     * 检查用户配额状态
     */
    public QuotaStatus getQuotaStatus(String userId) {
        UserQuota quota = getOrCreateQuota(userId);

        // 检查是否需要重置
        if (quota.needsReset()) {
            quota.resetDailyQuota();
        }

        return new QuotaStatus(
                quota.getRequestsUsedToday(),
                quota.getMaxRequestsPerDay(),
                quota.getTokensUsedToday(),
                quota.getMaxTokensPerDay(),
                quota.getRequestUsagePercent(),
                quota.getTokenUsagePercent(),
                quota.getQuotaResetAt()
        );
    }

    /**
     * 重置用户每日配额（定时任务调用）
     */
    @Transactional
    public void resetDailyQuotas() {
        List<UserQuota> quotasToReset = quotaRepository.findQuotasNeedingReset(Instant.now());
        for (UserQuota quota : quotasToReset) {
            quota.resetDailyQuota();
            quotaRepository.save(quota);
            log.info("已重置用户 {} 的每日配额", quota.getUserId());
        }
    }

    /**
     * 获取配额超限的用户列表
     */
    public List<UserQuota> getExceededQuotas() {
        return quotaRepository.findExceededQuotas();
    }

    /**
     * 升级用户套餐
     */
    @Transactional
    public void upgradePlan(String userId, String newPlanId) {
        // 获取新套餐配置
        SubscriptionPlan newPlan = planRepository.findByPlanIdAndIsActiveTrue(newPlanId)
                .orElseThrow(() -> new IllegalArgumentException("无效的套餐 ID: " + newPlanId));

        // 更新或创建订阅
        UserSubscription subscription = subscriptionRepository.findById(userId).orElse(null);
        if (subscription == null) {
            subscription = new UserSubscription(userId, newPlanId, newPlan.getPlanName());
        } else {
            subscription.setPlanId(newPlanId);
            subscription.setPlanName(newPlan.getPlanName());
        }
        subscriptionRepository.save(subscription);

        // 更新配额
        UserQuota quota = getOrCreateQuota(userId);
        quota.setMaxRequestsPerDay(newPlan.getMaxRequestsPerDay());
        quota.setMaxTokensPerDay(newPlan.getMaxTokensPerDay());
        quota.setMaxConcurrentSessions(newPlan.getMaxConcurrentSessions());
        quota.setMaxFileSizeBytes(newPlan.getMaxFileSizeBytes());
        quotaRepository.save(quota);

        log.info("用户 {} 套餐已升级为 {}", userId, newPlanId);
    }

    /**
     * 配额状态记录
     */
    public record QuotaStatus(
            int requestsUsed,
            int maxRequests,
            int tokensUsed,
            int maxTokens,
            double requestUsagePercent,
            double tokenUsagePercent,
            Instant nextReset
    ) {}
}
