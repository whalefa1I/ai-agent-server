package demo.k8s.agent.user;

import demo.k8s.agent.auth.ApiKeyGenerator;
import demo.k8s.agent.auth.PasswordEncoder;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event.UserLoginEvent;
import demo.k8s.agent.observability.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户服务
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyGenerator apiKeyGenerator;
    private final EventBus eventBus;
    private final MetricsCollector metricsCollector;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ApiKeyGenerator apiKeyGenerator,
                       EventBus eventBus,
                       MetricsCollector metricsCollector) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.apiKeyGenerator = apiKeyGenerator;
        this.eventBus = eventBus;
        this.metricsCollector = metricsCollector;
    }

    /**
     * 创建新用户
     */
    public User createUser(String username, String email, String password, UserRole role) {
        // 检查用户名是否存在
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }

        // 检查邮箱是否存在
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        // 加密密码
        String passwordHash = passwordEncoder.encode(password);

        // 创建用户
        User user = User.create(username, email, passwordHash, role);
        userRepository.save(user);

        log.info("创建新用户：{} ({})", user.username(), user.id());
        return user;
    }

    /**
     * 用户登录（用户名/邮箱 + 密码）
     */
    public LoginResult login(String usernameOrEmail, String password) {
        // 尝试通过用户名或邮箱查找用户
        Optional<User> userOpt = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail));

        if (userOpt.isEmpty()) {
            log.warn("用户不存在：{}", usernameOrEmail);
            throw new InvalidCredentialsException("用户名或密码错误");
        }

        User user = userOpt.get();

        // 检查账户是否激活
        if (!user.active()) {
            log.warn("用户账户已禁用：{}", usernameOrEmail);
            throw new AccountDisabledException("账户已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            log.warn("密码错误：{}", usernameOrEmail);
            throw new InvalidCredentialsException("用户名或密码错误");
        }

        // 更新最后登录时间
        User updatedUser = user.withLastLogin(Instant.now());
        userRepository.save(updatedUser);

        // 生成 Token
        String token = apiKeyGenerator.generateToken(user.id());

        log.info("用户登录成功：{} ({})", user.username(), user.id());

        // 发布登录事件
        eventBus.publish(new UserLoginEvent(user.id(), user.username(), user.id()));

        // 记录指标
        metricsCollector.recordRequest(user.id(), true);

        return new LoginResult(updatedUser, token);
    }

    /**
     * 通过 API Key 认证
     */
    public Optional<User> authenticateByApiKey(String apiKey) {
        return userRepository.findByApiKey(apiKey)
                .filter(User::active);
    }

    /**
     * 获取用户
     */
    public Optional<User> getUser(String userId) {
        return userRepository.findById(userId);
    }

    /**
     * 根据用户名获取用户
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 更新用户角色
     */
    public User updateUserRole(String userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        User updatedUser = user.withRole(newRole);
        userRepository.save(updatedUser);

        log.info("更新用户角色：{} -> {}", userId, newRole);
        return updatedUser;
    }

    /**
     * 生成/重置 API Key
     */
    public String regenerateApiKey(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String newApiKey = apiKeyGenerator.generateApiKey();
        User updatedUser = user.withApiKey(newApiKey);
        userRepository.save(updatedUser);

        log.info("为用户 {} 生成新的 API Key", userId);
        return newApiKey;
    }

    /**
     * 禁用用户
     */
    public User disableUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        User updatedUser = user.withActive(false);
        userRepository.save(updatedUser);

        log.info("禁用用户：{} ({})", user.username(), userId);
        return updatedUser;
    }

    /**
     * 启用用户
     */
    public User enableUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        User updatedUser = user.withActive(true);
        userRepository.save(updatedUser);

        log.info("启用用户：{} ({})", user.username(), userId);
        return updatedUser;
    }

    /**
     * 列出所有用户
     */
    public List<User> listUsers(int page, int size) {
        return userRepository.findAll(page, size);
    }

    /**
     * 统计用户数量
     */
    public long getUserCount() {
        return userRepository.count();
    }

    /**
     * 删除用户
     */
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        userRepository.delete(userId);
        log.info("删除用户：{} ({})", user.username(), userId);
    }

    /**
     * 修改密码
     */
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.passwordHash())) {
            throw new InvalidCredentialsException("原密码错误");
        }

        // 更新密码
        String newPasswordHash = passwordEncoder.encode(newPassword);
        User updatedUser = new User(
                user.id(),
                user.username(),
                user.email(),
                newPasswordHash,
                user.role(),
                user.createdAt(),
                user.lastLoginAt(),
                user.metadata(),
                user.apiKey(),
                user.active()
        );
        userRepository.save(updatedUser);

        log.info("用户修改密码：{} ({})", user.username(), userId);
    }

    /**
     * 重置密码
     */
    public String resetPassword(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 生成临时密码
        String tempPassword = apiKeyGenerator.generateTempPassword();
        String tempPasswordHash = passwordEncoder.encode(tempPassword);

        User updatedUser = new User(
                user.id(),
                user.username(),
                user.email(),
                tempPasswordHash,
                user.role(),
                user.createdAt(),
                user.lastLoginAt(),
                user.metadata(),
                user.apiKey(),
                user.active()
        );
        userRepository.save(updatedUser);

        log.info("用户密码已重置：{} ({})", user.username(), userId);
        return tempPassword;
    }

    /**
     * 登录结果
     */
    public record LoginResult(User user, String token) {}

    /**
     * 用户未找到异常
     */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String userId) {
            super("User not found: " + userId);
        }
    }

    /**
     * 用户名已存在异常
     */
    public static class UsernameAlreadyExistsException extends RuntimeException {
        public UsernameAlreadyExistsException(String username) {
            super("Username already exists: " + username);
        }
    }

    /**
     * 邮箱已存在异常
     */
    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String email) {
            super("Email already exists: " + email);
        }
    }

    /**
     * 无效凭证异常
     */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    /**
     * 账户禁用异常
     */
    public static class AccountDisabledException extends RuntimeException {
        public AccountDisabledException(String message) {
            super(message);
        }
    }
}
