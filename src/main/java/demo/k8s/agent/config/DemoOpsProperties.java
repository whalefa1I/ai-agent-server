package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运维探针 API（{@code /api/ops/**}）开关与鉴权。
 * <p>
 * 生产环境建议：{@code enabled=true} 且设置足够长的 {@code secret}，仅内网或 VPN 可达；
 * 请求头携带 {@code X-Ops-Secret: <secret>}。
 */
@ConfigurationProperties(prefix = "demo.ops")
public class DemoOpsProperties {

    /**
     * 为 false 时所有 {@code /api/ops/**} 返回 404，避免暴露攻击面。
     */
    private boolean enabled = false;

    /**
     * 与请求头 {@code X-Ops-Secret} 比对；建议至少 32 字符随机串。
     */
    private String secret = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean isSecretConfigured() {
        return secret != null && !secret.isBlank();
    }
}
