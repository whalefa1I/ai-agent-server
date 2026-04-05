package demo.k8s.agent.skills;

import java.util.Map;

/**
 * 技能执行器接口
 *
 * 每个技能实现这个接口来定义具体的执行逻辑
 * 执行器负责调用技能目录中的脚本文件（如 calc.py）来完成实际工作
 */
public interface SkillExecutor {

    /**
     * 获取支持的技能名称
     */
    String getSkillName();

    /**
     * 执行技能
     *
     * @param args 执行参数（由 LLM 调用工具时提供）
     * @return 执行结果
     */
    String execute(Map<String, Object> args);
}
