package demo.k8s.agent.ws;

import demo.k8s.agent.config.DemoWsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * WebSocket Token 验证服务，用于生产环境认证。
 */
@Service
public class WebSocketTokenService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTokenService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;

    private final DemoWsProperties properties;

    public WebSocketTokenService(DemoWsProperties properties) {
        this.properties = properties;
    }

    /**
     * 是否启用 Token 认证（生产环境建议启用）
     */
    public boolean isAuthEnabled() {
        return properties.isAuthEnabled();
    }

    /**
     * Token 有效期（秒），默认 24 小时
     */
    public int getTokenValiditySeconds() {
        return properties.getTokenValiditySeconds();
    }

    /**
     * 预共享密钥（用于简单部署）
     */
    public String getPreSharedKey() {
        return properties.getPsk();
    }

    /**
     * 已颁发 Token 的存储（Token -> 过期时间）
     */
    private final ConcurrentMap<String, Long> issuedTokens = new ConcurrentHashMap<>();

    /**
     * 生成新的 Token
     *
     * @return Base64 编码的 Token
     */
    public String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // 记录 Token 过期时间
        long expiryTime = System.currentTimeMillis() + (getTokenValiditySeconds() * 1000L);
        issuedTokens.put(token, expiryTime);

        log.info("生成新 Token，有效期 {} 秒", getTokenValiditySeconds());
        return token;
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token 待验证的 Token
     * @return true = 有效，false = 无效
     */
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            log.debug("Token 为空");
            return false;
        }

        // 检查是否为预共享密钥
        if (getPreSharedKey() != null && !getPreSharedKey().isEmpty() && getPreSharedKey().equals(token)) {
            log.debug("使用预共享密钥认证通过");
            return true;
        }

        // 检查已颁发的 Token
        Long expiryTime = issuedTokens.get(token);
        if (expiryTime == null) {
            log.debug("Token 不存在");
            return false;
        }

        // 检查是否过期
        if (System.currentTimeMillis() > expiryTime) {
            log.debug("Token 已过期");
            issuedTokens.remove(token);
            return false;
        }

        log.debug("Token 验证通过");
        return true;
    }

    /**
     * 撤销 Token
     *
     * @param token 要撤销的 Token
     */
    public void revokeToken(String token) {
        issuedTokens.remove(token);
        log.info("已撤销 Token");
    }

    /**
     * 清理过期 Token（定期调用）
     */
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        issuedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        log.debug("清理过期 Token 完成");
    }

    /**
     * 生成 Token 的哈希（用于日志记录，不记录原始 Token）
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
