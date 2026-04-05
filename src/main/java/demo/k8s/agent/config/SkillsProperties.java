package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Skills 配置属性
 */
@ConfigurationProperties(prefix = "demo.skills")
public class SkillsProperties {

    /**
     * 是否启用 Skills 系统
     */
    private boolean enabled = true;

    /**
     * ClawHub 配置
     */
    private Clawhub clawhub = new Clawhub();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Clawhub getClawhub() { return clawhub; }
    public void setClawhub(Clawhub clawhub) { this.clawhub = clawhub; }

    public static class Clawhub {
        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
