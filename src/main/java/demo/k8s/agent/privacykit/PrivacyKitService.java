package demo.k8s.agent.privacykit;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * PrivacyKitService - 隐私加密工具服务
 *
 * 提供 base64 编码/解码功能，对应 happy-server 的 privacy-kit 模块
 */
@Service
public class PrivacyKitService {

    /**
     * Base64 编码
     */
    public String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 解码
     */
    public byte[] decodeBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    /**
     * String Base64 编码
     */
    public String encodeBase64(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * String Base64 解码
     */
    public String decodeBase64ToString(String base64) {
        if (base64 == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}
