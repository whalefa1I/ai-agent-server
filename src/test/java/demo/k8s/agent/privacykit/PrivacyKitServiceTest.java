package demo.k8s.agent.privacykit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrivacyKitService 测试
 */
@DisplayName("PrivacyKitService 测试")
class PrivacyKitServiceTest {

    private PrivacyKitService privacyKit;

    @BeforeEach
    void setUp() {
        privacyKit = new PrivacyKitService();
    }

    @Test
    @DisplayName("测试 encodeBase64 字节数组")
    void testEncodeBase64Bytes() {
        byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);
        String encoded = privacyKit.encodeBase64(data);
        String expected = Base64.getEncoder().encodeToString(data);
        assertEquals(expected, encoded);
    }

    @Test
    @DisplayName("测试 decodeBase64 字节数组")
    void testDecodeBase64Bytes() {
        String encoded = Base64.getEncoder().encodeToString("Test".getBytes(StandardCharsets.UTF_8));
        byte[] decoded = privacyKit.decodeBase64(encoded);
        assertArrayEquals("Test".getBytes(StandardCharsets.UTF_8), decoded);
    }

    @Test
    @DisplayName("测试 encodeBase64 字符串")
    void testEncodeBase64String() {
        String data = "Hello World";
        String encoded = privacyKit.encodeBase64(data);
        String expected = Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, encoded);
    }

    @Test
    @DisplayName("测试 encodeBase64 空字符串")
    void testEncodeBase64EmptyString() {
        String encoded = privacyKit.encodeBase64("");
        assertEquals("", encoded);
    }

    @Test
    @DisplayName("测试 encodeBase64 null")
    void testEncodeBase64Null() {
        String encoded = privacyKit.encodeBase64((String) null);
        assertNull(encoded);
    }

    @Test
    @DisplayName("测试 decodeBase64ToString")
    void testDecodeBase64ToString() {
        String original = "Test Message";
        String encoded = privacyKit.encodeBase64(original);
        String decoded = privacyKit.decodeBase64ToString(encoded);
        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("测试 decodeBase64ToString null")
    void testDecodeBase64ToStringNull() {
        String decoded = privacyKit.decodeBase64ToString(null);
        assertNull(decoded);
    }

    @Test
    @DisplayName("测试编解码往返")
    void testEncodeDecodeRoundTrip() {
        String original = "Test Round Trip";
        String encoded = privacyKit.encodeBase64(original);
        String decoded = privacyKit.decodeBase64ToString(encoded);
        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("测试中文字符编解码")
    void testChineseCharacters() {
        String original = "你好世界";
        String encoded = privacyKit.encodeBase64(original);
        String decoded = privacyKit.decodeBase64ToString(encoded);
        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("测试特殊字符编解码")
    void testSpecialCharacters() {
        String original = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
        String encoded = privacyKit.encodeBase64(original);
        String decoded = privacyKit.decodeBase64ToString(encoded);
        assertEquals(original, decoded);
    }
}
