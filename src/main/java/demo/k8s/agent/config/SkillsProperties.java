package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.HashMap;

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

    /**
     * 技能配置（用于 gating 的 config 检查）
     * key: skill key（技能名或自定义标识）
     * value: 配置项（必须是 truthy 值才能通过 gating）
     */
    private Map<String, Object> entries = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Clawhub getClawhub() { return clawhub; }
    public void setClawhub(Clawhub clawhub) { this.clawhub = clawhub; }

    public Map<String, Object> getEntries() { return entries; }
    public void setEntries(Map<String, Object> entries) { this.entries = entries; }

    /**
     * 检查某个 skill key 的配置是否为 truthy
     */
    public boolean isConfigTruthy(String skillKey) {
        Object value = entries.get(skillKey);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase();
            return !s.isEmpty() && !s.equals("false") && !s.equals("0") && !s.equals("no");
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        return true; // 其他非 null 对象视为 truthy
    }

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
