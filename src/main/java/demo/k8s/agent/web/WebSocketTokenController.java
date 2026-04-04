package demo.k8s.agent.web;

import demo.k8s.agent.ws.WebSocketTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket Token 管理 API
 */
@RestController
@RequestMapping("/api/ws")
public class WebSocketTokenController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTokenController.class);

    private final WebSocketTokenService tokenService;

    public WebSocketTokenController(WebSocketTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * 生成新的 Token
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> generateToken() {
        String token = tokenService.generateToken();
        String tokenHash = tokenService.hashToken(token);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("tokenHash", tokenHash);
        response.put("validitySeconds", tokenService.getTokenValiditySeconds());
        response.put("expiresIn", tokenService.getTokenValiditySeconds());

        log.info("生成新 WebSocket Token: hash={}", tokenHash);
        return ResponseEntity.ok(response);
    }

    /**
     * 验证 Token
     */
    @PostMapping("/token/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        boolean valid = tokenService.validateToken(token);
        String tokenHash = tokenService.hashToken(token);

        Map<String, Object> response = new HashMap<>();
        response.put("valid", valid);
        response.put("tokenHash", tokenHash);

        if (!valid) {
            response.put("reason", "Token 不存在或已过期");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 撤销 Token
     */
    @PostMapping("/token/revoke")
    public ResponseEntity<Map<String, Object>> revokeToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String tokenHash = tokenService.hashToken(token);

        tokenService.revokeToken(token);

        Map<String, Object> response = new HashMap<>();
        response.put("revoked", true);
        response.put("tokenHash", tokenHash);

        log.info("撤销 WebSocket Token: hash={}", tokenHash);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取认证配置信息
     */
    @GetMapping("/auth/config")
    public ResponseEntity<Map<String, Object>> getAuthConfig() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", tokenService.isAuthEnabled());
        response.put("tokenValiditySeconds", tokenService.getTokenValiditySeconds());

        return ResponseEntity.ok(response);
    }
}
