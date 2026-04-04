package demo.k8s.agent.quota;

import demo.k8s.agent.user.User;
import demo.k8s.agent.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 配额管理服务
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final QuotaConfig quotaConfig;

    /**
     * 用户请求计数（滑动窗口）
     */
    private final Map<String, UserQuotaState> userQuotaStates = new ConcurrentHashMap<>();

    public QuotaService(QuotaConfig quotaConfig) {
        this.quotaConfig = quotaConfig;
    }

    /**
     * 检查用户是否有可用配额
     *
     * @return true = 有配额，false = 配额已用尽
     */
    public synchronized boolean checkQuota(User user, int requestedTokens) {
        QuotaLimit limit = getQuotaLimit(user.role());
        UserQuotaState state = getUserQuotaState(user.id());

        Instant now = Instant.now();

        // 检查小时配额
        if (!state.requestsThisHour.tryAcquire(now, limit.maxRequestsPerHour())) {
            log.warn("用户 {} 小时请求配额已用尽 ({}/{})",
                    user.username(), state.requestsThisHour.getCount(), limit.maxRequestsPerHour());
            return false;
        }

        // 检查 Token 配额
        long currentTokens = state.tokensThisHour.get();
        if (currentTokens + requestedTokens > limit.maxTokensPerHour()) {
            log.warn("用户 {} Token 配额已用尽 ({} + {} > {})",
                    user.username(), currentTokens, requestedTokens, limit.maxTokensPerHour());
            return false;
        }
        state.tokensThisHour.addAndGet(requestedTokens);

        // 检查并发 Session 数
        int concurrentSessions = state.concurrentSessions.get();
        if (concurrentSessions >= limit.maxConcurrentSessions()) {
            log.warn("用户 {} 并发 Session 数已达上限 ({}/{})",
                    user.username(), concurrentSessions, limit.maxConcurrentSessions());
            return false;
        }

        return true;
    }

    /**
     * 记录 Token 使用
     */
    public void recordTokenUsage(User user, int tokens) {
        UserQuotaState state = getUserQuotaState(user.id());
        state.tokensThisHour.addAndGet(tokens);
        state.totalTokensToday.addAndGet(tokens);
    }

    /**
     * 增加并发 Session 计数
     */
    public void incrementSessionCount(User user) {
        UserQuotaState state = getUserQuotaState(user.id());
        state.concurrentSessions.incrementAndGet();
    }

    /**
     * 减少并发 Session 计数
     */
    public void decrementSessionCount(User user) {
        UserQuotaState state = getUserQuotaState(user.id());
        state.concurrentSessions.decrementAndGet();
    }

    /**
     * 获取用户的配额限制
     */
    public QuotaLimit getQuotaLimit(UserRole role) {
        QuotaConfig.QuotaLimitConfig config = switch (role) {
            case PREMIUM -> quotaConfig.getPremium();
            case ADMIN -> quotaConfig.getAdmin();
            case SERVICE -> quotaConfig.getService();
            default -> quotaConfig.getDefaultQuota();
        };
        return new QuotaLimit(
                config.getMaxRequestsPerHour(),
                config.getMaxTokensPerHour(),
                config.getMaxTokensPerRequest(),
                config.getMaxConcurrentSessions(),
                Duration.ofSeconds(config.getSessionTimeoutSeconds())
        );
    }

    /**
     * 获取用户配额状态
     */
    public UserQuotaState getUserQuotaState(String userId) {
        return userQuotaStates.computeIfAbsent(userId, k -> new UserQuotaState());
    }

    /**
     * 重置用户配额（管理员操作）
     */
    public void resetUserQuota(String userId) {
        userQuotaStates.remove(userId);
        log.info("重置用户配额：{}", userId);
    }

    /**
     * 清理过期配额状态（定期调用）
     */
    public void cleanupExpiredQuotas() {
        Instant now = Instant.now();
        userQuotaStates.entrySet().removeIf(entry -> {
            UserQuotaState state = entry.getValue();
            // 如果超过 2 小时没有活动，清理状态
            return Duration.between(state.lastActivityTime, now).toHours() > 2;
        });
        log.debug("清理过期配额状态完成");
    }

    /**
     * 获取配额使用详情
     */
    public QuotaUsage getQuotaUsage(User user) {
        QuotaLimit limit = getQuotaLimit(user.role());
        UserQuotaState state = getUserQuotaState(user.id());

        return new QuotaUsage(
                state.requestsThisHour.getCount(),
                limit.maxRequestsPerHour(),
                state.tokensThisHour.get(),
                limit.maxTokensPerHour(),
                state.totalTokensToday.get(),
                state.concurrentSessions.get(),
                limit.maxConcurrentSessions()
        );
    }

    /**
     * 配额限制配置
     */
    public record QuotaLimit(
            int maxRequestsPerHour,
            int maxTokensPerHour,
            int maxTokensPerRequest,
            int maxConcurrentSessions,
            Duration sessionTimeout
    ) {}

    /**
     * 配额使用详情
     */
    public record QuotaUsage(
            int requestsUsed,
            int requestsLimit,
            long tokensUsed,
            long tokensLimit,
            long totalTokensToday,
            int concurrentSessions,
            int maxConcurrentSessions
    ) {}

    /**
     * 用户配额状态（线程安全）
     */
    public static class UserQuotaState {

        private final SlidingWindowCounter requestsThisHour = new SlidingWindowCounter(Duration.ofHours(1));
        private final AtomicLong tokensThisHour = new AtomicLong(0);
        private final AtomicLong totalTokensToday = new AtomicLong(0);
        private final AtomicInteger concurrentSessions = new AtomicInteger(0);
        private volatile Instant lastActivityTime = Instant.now();

        public void updateActivityTime() {
            this.lastActivityTime = Instant.now();
        }
    }
}
