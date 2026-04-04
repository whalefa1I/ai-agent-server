package demo.k8s.agent.config;

/**
 * Task 工具输入。
 */
public record TaskInput(
        String name,
        String goal,
        String subagent_type,
        boolean run_in_background
) {}
