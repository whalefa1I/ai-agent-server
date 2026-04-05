package demo.k8s.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能执行器注册表
 *
 * 管理所有技能执行器的注册和查找
 */
@Component
public class SkillExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillExecutorRegistry.class);

    // 技能名称 -> 执行器映射
    private final Map<String, SkillExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 注册执行器
     */
    public void registerExecutor(SkillExecutor executor) {
        if (executor != null) {
            executors.put(executor.getSkillName(), executor);
            log.info("注册技能执行器：{}", executor.getSkillName());
        }
    }

    /**
     * 获取执行器
     */
    public SkillExecutor getExecutor(String skillName) {
        return executors.get(skillName);
    }

    /**
     * 是否有执行器
     */
    public boolean hasExecutor(String skillName) {
        return executors.containsKey(skillName);
    }

    /**
     * 执行技能
     */
    public String executeSkill(String skillName, Map<String, Object> args) {
        SkillExecutor executor = executors.get(skillName);
        if (executor == null) {
            return "错误：未找到技能执行器：" + skillName;
        }
        try {
            return executor.execute(args);
        } catch (Exception e) {
            log.error("执行技能失败：{}", skillName, e);
            return "执行失败：" + e.getMessage();
        }
    }
}
