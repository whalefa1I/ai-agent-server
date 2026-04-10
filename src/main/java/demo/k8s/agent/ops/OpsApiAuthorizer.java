package demo.k8s.agent.ops;

import demo.k8s.agent.config.DemoOpsProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 校验 {@code X-Ops-Secret} 与 {@link DemoOpsProperties#getSecret()}（常量时间比较）。
 */
@Component
public class OpsApiAuthorizer {

    private final DemoOpsProperties props;

    public OpsApiAuthorizer(DemoOpsProperties props) {
        this.props = props;
    }

    /**
     * @return null 表示通过；否则为需直接返回的 HTTP 响应
     */
    public ResponseEntity<Map<String, Object>> preflight(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Ops API disabled (demo.ops.enabled=false)"));
        }
        if (!props.isSecretConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", true, "message", "Ops API misconfigured: set demo.ops.secret"));
        }
        String header = request.getHeader("X-Ops-Secret");
        if (header == null || header.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", true, "message", "Missing X-Ops-Secret header"));
        }
        byte[] expected = props.getSecret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = header.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", true, "message", "Invalid X-Ops-Secret"));
        }
        return null;
    }
}
