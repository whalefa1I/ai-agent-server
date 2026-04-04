package demo.k8s.agent.quota;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 配额配置
 */
@Component
@ConfigurationProperties(prefix = "demo.quota")
public class QuotaConfig {

    /**
     * 默认配额（普通用户）
     */
    private QuotaLimitConfig defaultQuota = new QuotaLimitConfig();

    /**
     * 高级用户配额
     */
    private QuotaLimitConfig premium = new QuotaLimitConfig();

    /**
     * 管理员配额（无限制）
     */
    private QuotaLimitConfig admin = new QuotaLimitConfig();

    /**
     * 服务账户配额
     */
    private QuotaLimitConfig service = new QuotaLimitConfig();

    public QuotaLimitConfig getDefaultQuota() {
        return defaultQuota;
    }

    public QuotaLimitConfig getPremium() {
        return premium;
    }

    public QuotaLimitConfig getAdmin() {
        return admin;
    }

    public QuotaLimitConfig getService() {
        return service;
    }

    public void setDefaultQuota(QuotaLimitConfig defaultQuota) {
        this.defaultQuota = defaultQuota;
    }

    public void setPremium(QuotaLimitConfig premium) {
        this.premium = premium;
    }

    public void setAdmin(QuotaLimitConfig admin) {
        this.admin = admin;
    }

    public void setService(QuotaLimitConfig service) {
        this.service = service;
    }

    /**
     * 配额配置项
     */
    public static class QuotaLimitConfig {

        /**
         * 每小时最大请求数
         */
        private int maxRequestsPerHour = 100;

        /**
         * 每小时最大 Token 数
         */
        private int maxTokensPerHour = 100000;

        /**
         * 单次请求最大 Token 数
         */
        private int maxTokensPerRequest = 50000;

        /**
         * 最大并发 Session 数
         */
        private int maxConcurrentSessions = 5;

        /**
         * Session 超时时间（秒）
         */
        private int sessionTimeoutSeconds = 3600;

        public int getMaxRequestsPerHour() {
            return maxRequestsPerHour;
        }

        public void setMaxRequestsPerHour(int maxRequestsPerHour) {
            this.maxRequestsPerHour = maxRequestsPerHour;
        }

        public int getMaxTokensPerHour() {
            return maxTokensPerHour;
        }

        public void setMaxTokensPerHour(int maxTokensPerHour) {
            this.maxTokensPerHour = maxTokensPerHour;
        }

        public int getMaxTokensPerRequest() {
            return maxTokensPerRequest;
        }

        public void setMaxTokensPerRequest(int maxTokensPerRequest) {
            this.maxTokensPerRequest = maxTokensPerRequest;
        }

        public int getMaxConcurrentSessions() {
            return maxConcurrentSessions;
        }

        public void setMaxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = maxConcurrentSessions;
        }

        public int getSessionTimeoutSeconds() {
            return sessionTimeoutSeconds;
        }

        public void setSessionTimeoutSeconds(int sessionTimeoutSeconds) {
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        }
    }
}
