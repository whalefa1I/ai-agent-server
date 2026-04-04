package demo.k8s.agent.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * 内存用户仓库实现（用于开发和测试）
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserRepository.class);

    private final ConcurrentMap<String, User> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> usernameToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> emailToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> apiKeyToId = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        // 如果是新用户，建立索引
        if (!usersById.containsKey(user.id())) {
            usernameToId.put(user.username(), user.id());
            emailToId.put(user.email(), user.id());
            if (user.apiKey() != null) {
                apiKeyToId.put(user.apiKey(), user.id());
            }
        } else {
            // 更新用户，处理 API Key 变更
            User existing = usersById.get(user.id());
            if (existing.apiKey() != null && !existing.apiKey().equals(user.apiKey())) {
                apiKeyToId.remove(existing.apiKey());
            }
            if (user.apiKey() != null) {
                apiKeyToId.put(user.apiKey(), user.id());
            }
        }

        usersById.put(user.id(), user);
        log.debug("保存用户：{} ({})", user.username(), user.id());
    }

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String id = usernameToId.get(username);
        return id != null ? Optional.ofNullable(usersById.get(id)) : Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String id = emailToId.get(email);
        return id != null ? Optional.ofNullable(usersById.get(id)) : Optional.empty();
    }

    @Override
    public Optional<User> findByApiKey(String apiKey) {
        String id = apiKeyToId.get(apiKey);
        return id != null ? Optional.ofNullable(usersById.get(id)) : Optional.empty();
    }

    @Override
    public List<User> findAll() {
        return usersById.values().stream().toList();
    }

    @Override
    public List<User> findAll(int page, int size) {
        return usersById.values().stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    @Override
    public void delete(String id) {
        User user = usersById.remove(id);
        if (user != null) {
            usernameToId.remove(user.username());
            emailToId.remove(user.email());
            if (user.apiKey() != null) {
                apiKeyToId.remove(user.apiKey());
            }
            log.info("删除用户：{} ({})", user.username(), user.id());
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return usernameToId.containsKey(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return emailToId.containsKey(email);
    }

    @Override
    public long count() {
        return usersById.size();
    }
}
