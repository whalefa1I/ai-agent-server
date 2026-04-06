package demo.k8s.agent.web;

import demo.k8s.agent.auth.ApiKeyGenerator;
import demo.k8s.agent.user.QuotaService;
import demo.k8s.agent.user.User;
import demo.k8s.agent.user.UserRole;
import demo.k8s.agent.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final QuotaService quotaService;
    private final ApiKeyGenerator apiKeyGenerator;

    public UserController(UserService userService,
                          QuotaService quotaService,
                          ApiKeyGenerator apiKeyGenerator) {
        this.userService = userService;
        this.quotaService = quotaService;
        this.apiKeyGenerator = apiKeyGenerator;
    }

    /**
     * 创建用户
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String email = request.get("email");
        String password = request.get("password");
        String role = request.getOrDefault("role", "user");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱不能为空"));
        }
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少需要 6 个字符"));
        }

        try {
            User user = userService.createUser(username, email, password, UserRole.fromCode(role));

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.id());
            response.put("username", user.username());
            response.put("email", user.email());
            response.put("role", user.role().getCode());
            response.put("createdAt", user.createdAt().toString());

            return ResponseEntity.ok(response);
        } catch (UserService.UsernameAlreadyExistsException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        } catch (UserService.EmailAlreadyExistsException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱已存在"));
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String usernameOrEmail = request.get("username");
        String password = request.get("password");

        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名或邮箱不能为空"));
        }
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码不能为空"));
        }

        try {
            UserService.LoginResult result = userService.login(usernameOrEmail, password);

            Map<String, Object> response = new HashMap<>();
            response.put("token", result.token());
            response.put("user", Map.of(
                    "id", result.user().id(),
                    "username", result.user().username(),
                    "email", result.user().email(),
                    "role", result.user().role().getCode()
            ));

            return ResponseEntity.ok(response);
        } catch (UserService.InvalidCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (UserService.AccountDisabledException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        // 实际使用中需要从认证上下文获取当前用户
        // 这里简化处理
        return ResponseEntity.ok(Map.of("error", "需要从认证上下文获取用户"));
    }

    /**
     * 获取用户配额使用情况
     */
    @GetMapping("/{userId}/quota")
    public ResponseEntity<Map<String, Object>> getUserQuota(@PathVariable String userId) {
        try {
            User user = userService.getUser(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            var status = quotaService.getQuotaStatus(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("role", user.role().getCode());
            response.put("requestsUsed", status.requestsUsed());
            response.put("requestsLimit", status.maxRequests());
            response.put("tokensUsed", status.tokensUsed());
            response.put("tokensLimit", status.maxTokens());
            response.put("nextReset", status.nextReset().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 生成/重置 API Key
     */
    @PostMapping("/{userId}/api-key")
    public ResponseEntity<Map<String, Object>> regenerateApiKey(@PathVariable String userId) {
        try {
            String newApiKey = userService.regenerateApiKey(userId);

            return ResponseEntity.ok(Map.of(
                    "apiKey", newApiKey,
                    "message", "API Key 已生成，请妥善保存（只会显示一次）"
            ));
        } catch (UserService.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
    }

    /**
     * 列出所有用户
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<User> users = userService.listUsers(page, size);

        List<Map<String, Object>> response = users.stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.id(),
                        "username", user.username(),
                        "email", user.email(),
                        "role", user.role().getCode(),
                        "active", user.active(),
                        "createdAt", user.createdAt().toString(),
                        "lastLoginAt", user.lastLoginAt() != null ? user.lastLoginAt().toString() : null
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * 更新用户角色
     */
    @PatchMapping("/{userId}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {

        String role = request.get("role");
        if (role == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "角色不能为空"));
        }

        try {
            User updatedUser = userService.updateUserRole(userId, UserRole.fromCode(role));

            return ResponseEntity.ok(Map.of(
                    "id", updatedUser.id(),
                    "username", updatedUser.username(),
                    "role", updatedUser.role().getCode()
            ));
        } catch (UserService.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
    }

    /**
     * 禁用用户
     */
    @PostMapping("/{userId}/disable")
    public ResponseEntity<Map<String, String>> disableUser(@PathVariable String userId) {
        try {
            userService.disableUser(userId);
            return ResponseEntity.ok(Map.of("message", "用户已禁用"));
        } catch (UserService.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
    }

    /**
     * 启用用户
     */
    @PostMapping("/{userId}/enable")
    public ResponseEntity<Map<String, String>> enableUser(@PathVariable String userId) {
        try {
            userService.enableUser(userId);
            return ResponseEntity.ok(Map.of("message", "用户已启用"));
        } catch (UserService.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String userId) {
        try {
            userService.deleteUser(userId);
            return ResponseEntity.ok(Map.of("message", "用户已删除"));
        } catch (UserService.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/{userId}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "需要提供原密码和新密码"));
        }

        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "新密码至少需要 6 个字符"));
        }

        try {
            userService.changePassword(userId, oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "密码已修改"));
        } catch (UserService.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        } catch (UserService.InvalidCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "原密码错误"));
        }
    }

    /**
     * 重置密码
     */
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String userId) {
        try {
            String tempPassword = userService.resetPassword(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "密码已重置为临时密码",
                    "temporaryPassword", tempPassword
            ));
        } catch (UserService.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
    }
}
