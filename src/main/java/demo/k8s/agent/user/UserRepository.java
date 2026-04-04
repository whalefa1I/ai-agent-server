package demo.k8s.agent.user;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓库接口
 */
public interface UserRepository {

    /**
     * 保存用户
     */
    void save(User user);

    /**
     * 根据 ID 获取用户
     */
    Optional<User> findById(String id);

    /**
     * 根据用户名获取用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱获取用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据 API Key 获取用户
     */
    Optional<User> findByApiKey(String apiKey);

    /**
     * 列出所有用户
     */
    List<User> findAll();

    /**
     * 列出所有用户（分页）
     */
    List<User> findAll(int page, int size);

    /**
     * 删除用户
     */
    void delete(String id);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 统计用户数量
     */
    long count();
}
