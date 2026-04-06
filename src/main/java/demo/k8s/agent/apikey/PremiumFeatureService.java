package demo.k8s.agent.apikey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 核心收益点服务
 * 对需要单独收费/限制的核心功能进行权限收拢和计量计费
 */
@Service
public class PremiumFeatureService {

    private static final Logger log = LoggerFactory.getLogger(PremiumFeatureService.class);

    private final ApiKeyService apiKeyService;

    // 核心收益点配置 (实际应该从数据库加载)
    private static final Map<String, PremiumFeatureConfig> FEATURE_CONFIGS = new HashMap<>();

    static {
        // AI 模型调用
        FEATURE_CONFIGS.put("ai_model_call", new PremiumFeatureConfig(
                "ai_model_call", "AI 模型调用", "AI_MODEL",
                BillingType.PER_TOKEN, 1L, "token", 10000, 1000000
        ));

        // 远程工具执行
        FEATURE_CONFIGS.put("remote_tool_execution", new PremiumFeatureConfig(
                "remote_tool_execution", "远程工具执行", "REMOTE_TOOL",
                BillingType.PER_REQUEST, 10L, "request", 100, 10000
        ));

        // 向量搜索
        FEATURE_CONFIGS.put("vector_search", new PremiumFeatureConfig(
                "vector_search", "向量搜索", "AI_MODEL",
                BillingType.PER_REQUEST, 5L, "request", 100, 1000
        ));

        // 定时任务执行
        FEATURE_CONFIGS.put("scheduled_task", new PremiumFeatureConfig(
                "scheduled_task", "定时任务执行", "SCHEDULER",
                BillingType.PER_EXECUTION, 1L, "execution", 100, 10000
        ));

        // 心跳服务
        FEATURE_CONFIGS.put("heartbeat_service", new PremiumFeatureConfig(
                "heartbeat_service", "心跳服务", "SCHEDULER",
                BillingType.PER_MINUTE, 1L, "minute", 60, 1440
        ));

        // 文件存储
        FEATURE_CONFIGS.put("file_storage", new PremiumFeatureConfig(
                "file_storage", "文件存储", "STORAGE",
                BillingType.PER_GB_MONTH, 500L, "GB·月", 1, 100
        ));
    }

    public PremiumFeatureService(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * 检查用户是否可以使用某个核心功能
     */
    public PremiumFeatureCheckResult checkFeaturePermission(
            String userId, String apiKeyId, String featureId, long requestedAmount) {

        // 1. 检查功能配置是否存在
        PremiumFeatureConfig config = FEATURE_CONFIGS.get(featureId);
        if (config == null) {
            return PremiumFeatureCheckResult.failed("未知的功能：" + featureId);
        }

        // 2. 检查功能是否启用
        if (!config.isActive) {
            return PremiumFeatureCheckResult.failed("功能已禁用：" + featureId);
        }

        // 3. 检查用户配额
        // 实际实现需要查询数据库中的用户配额和使用情况
        // 这里简化处理

        // 4. 检查用户今日使用量
        long currentUsage = getUserDailyUsage(userId, featureId);
        if (currentUsage + requestedAmount > config.defaultLimit) {
            return PremiumFeatureCheckResult.quotaExceeded(
                    String.format("超出功能配额限制：%d/%d",
                            currentUsage + requestedAmount, config.defaultLimit));
        }

        // 5. 计算费用
        long cost = config.calculateCost(requestedAmount);

        return PremiumFeatureCheckResult.allowed(cost);
    }

    /**
     * 记录核心功能使用
     */
    @Transactional
    public void recordFeatureUsage(
            String userId, String apiKeyId, String featureId, long amount) {

        PremiumFeatureConfig config = FEATURE_CONFIGS.get(featureId);
        if (config == null) {
            log.warn("记录未知功能使用：{}", featureId);
            return;
        }

        long cost = config.calculateCost(amount);

        log.info("记录核心功能使用：user={}, feature={}, amount={}, cost={} 分",
                userId, featureId, amount, cost);

        // 实际实现应该：
        // 1. 写入 user_premium_usage 表
        // 2. 更新 api_key 的使用统计
        // 3. 累加用户总费用
    }

    /**
     * 获取核心功能列表
     */
    public List<PremiumFeatureConfig> getAllFeatures() {
        return new ArrayList<>(FEATURE_CONFIGS.values());
    }

    /**
     * 获取功能配置
     */
    public PremiumFeatureConfig getFeatureConfig(String featureId) {
        return FEATURE_CONFIGS.get(featureId);
    }

    /**
     * 获取用户每日使用量 (简化实现)
     */
    private long getUserDailyUsage(String userId, String featureId) {
        // 实际实现需要查询数据库
        // SELECT usage_amount FROM user_premium_usage
        // WHERE user_id = ? AND feature_id = ? AND usage_date = CURRENT_DATE
        return 0L;
    }

    /**
     * 核心收益点配置
     */
    public static class PremiumFeatureConfig {
        private final String featureId;
        private final String featureName;
        private final String category;
        private final BillingType billingType;
        private final long pricePerUnit;
        private final String unitName;
        private final int defaultLimit;
        private final int maxLimit;
        private final boolean isActive;

        public PremiumFeatureConfig(String featureId, String featureName, String category,
                                     BillingType billingType, long pricePerUnit, String unitName,
                                     int defaultLimit, int maxLimit) {
            this.featureId = featureId;
            this.featureName = featureName;
            this.category = category;
            this.billingType = billingType;
            this.pricePerUnit = pricePerUnit;
            this.unitName = unitName;
            this.defaultLimit = defaultLimit;
            this.maxLimit = maxLimit;
            this.isActive = true;
        }

        public long calculateCost(long amount) {
            return pricePerUnit * amount;
        }

        public String getFeatureId() { return featureId; }
        public String getFeatureName() { return featureName; }
        public String getCategory() { return category; }
        public BillingType getBillingType() { return billingType; }
        public long getPricePerUnit() { return pricePerUnit; }
        public String getUnitName() { return unitName; }
        public int getDefaultLimit() { return defaultLimit; }
        public int getMaxLimit() { return maxLimit; }
        public boolean isActive() { return isActive; }
    }

    public enum BillingType {
        PER_REQUEST,      // 按次计费
        PER_TOKEN,        // 按 Token 计费
        PER_MINUTE,       // 按分钟计费
        PER_GB_MONTH,     // 按 GB·月计费
        PER_EXECUTION,    // 按执行次数计费
        FLAT              // 包月/包年
    }

    public static class PremiumFeatureCheckResult {
        private final boolean allowed;
        private final String errorMessage;
        private final Long cost;

        private PremiumFeatureCheckResult(boolean allowed, String errorMessage, Long cost) {
            this.allowed = allowed;
            this.errorMessage = errorMessage;
            this.cost = cost;
        }

        public static PremiumFeatureCheckResult allowed(long cost) {
            return new PremiumFeatureCheckResult(true, null, cost);
        }

        public static PremiumFeatureCheckResult failed(String errorMessage) {
            return new PremiumFeatureCheckResult(false, errorMessage, null);
        }

        public static PremiumFeatureCheckResult quotaExceeded(String errorMessage) {
            return new PremiumFeatureCheckResult(false, errorMessage, null);
        }

        public boolean isAllowed() { return allowed; }
        public String getErrorMessage() { return errorMessage; }
        public Long getCost() { return cost; }
    }
}
