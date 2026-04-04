package demo.k8s.agent.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 生成器
 */
@Component
public class ApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int API_KEY_LENGTH = 32;
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final int TOKEN_LENGTH = 64;

    /**
     * 生成 API Key
     */
    public String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        RANDOM.nextBytes(randomBytes);
        return "sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 生成临时密码
     */
    public String generateTempPassword() {
        byte[] randomBytes = new byte[TEMP_PASSWORD_LENGTH];
        RANDOM.nextBytes(randomBytes);
        // 使用字母和数字的组合
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            sb.append(chars.charAt(Math.abs(b) % chars.length()));
        }
        return sb.toString();
    }

    /**
     * 生成认证 Token
     */
    public String generateToken(String userId) {
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        RANDOM.nextBytes(randomBytes);
        String tokenBody = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        // 格式：tok_{userId}_{random}
        return "tok_" + userId + "_" + tokenBody.substring(0, 32);
    }
}
