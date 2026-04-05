package demo.k8s.agent.skills;

import com.fasterxml.jackson.databind.JsonNode;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册表 - 管理所有已注册的技能
 */
@Service
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    /**
     * 技能接口定义
     */
    public interface Skill {
        String getName();
        String getDescription();
        List<ClaudeLikeTool> getTools();
        boolean isEnabled();
        void setEnabled(boolean enabled);
    }

    // 已注册的技能
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    public SkillRegistry() {
        log.info("初始化技能注册表");
    }

    /**
     * 注册技能
     */
    public void registerSkill(Skill skill) {
        log.info("注册技能：{}", skill.getName());
        skills.put(skill.getName(), skill);
    }

    /**
     * 注销技能
     */
    public void unregisterSkill(String skillName) {
        Skill removed = skills.remove(skillName);
        if (removed != null) {
            log.info("注销技能：{}", skillName);
        }
    }

    /**
     * 获取技能
     */
    public Skill getSkill(String skillName) {
        return skills.get(skillName);
    }

    /**
     * 获取所有技能
     */
    public List<Skill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 获取已启用的技能
     */
    public List<Skill> getEnabledSkills() {
        return skills.values().stream()
                .filter(Skill::isEnabled)
                .toList();
    }

    /**
     * 获取技能的所有工具回调
     */
    public List<ClaudeLikeTool> getSkillTools() {
        List<ClaudeLikeTool> tools = new ArrayList<>();
        for (Skill skill : getEnabledSkills()) {
            tools.addAll(skill.getTools());
        }
        return tools;
    }

    /**
     * 检查技能是否存在
     */
    public boolean hasSkill(String skillName) {
        return skills.containsKey(skillName);
    }

    /**
     * 获取技能数量
     */
    public int getSkillCount() {
        return skills.size();
    }
}
