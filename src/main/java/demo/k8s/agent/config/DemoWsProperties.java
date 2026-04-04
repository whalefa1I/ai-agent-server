package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WebSocket 配置属性
 */
@ConfigurationProperties(prefix = "demo.ws")
@Component
public class DemoWsProperties {

    /**
     * 是否启用 Token 认证（生产环境建议启用）
     */
    private boolean authEnabled = false;

    /**
     * Token 有效期（秒），默认 24 小时
     */
    private int tokenValiditySeconds = 86400;

    /**
     * 预共享密钥（用于简单部署）
     */
    private String psk = "";

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public int getTokenValiditySeconds() {
        return tokenValiditySeconds;
    }

    public void setTokenValiditySeconds(int tokenValiditySeconds) {
        this.tokenValiditySeconds = tokenValiditySeconds;
    }

    public String getPsk() {
        return psk;
    }

    public void setPsk(String psk) {
        this.psk = psk;
    }
}
