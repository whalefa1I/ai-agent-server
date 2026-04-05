package demo.k8s.agent.commandsystem.executors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import demo.k8s.agent.commandsystem.SlashCommandExecutor;
import demo.k8s.agent.commandsystem.SlashCommandResult;
import demo.k8s.agent.state.ConversationSession;

/**
 * /plan 命令执行器 - 计划模式
 *
 * 功能：
 * 1. 启用/禁用计划模式
 * 2. 查看当前计划
 * 3. 在编辑器中打开计划文件
 *
 * 用法：
 * - /plan - 启用计划模式或查看当前计划
 * - /plan open - 在编辑器中打开计划文件
 * - /plan <description> - 设置计划描述
 */
public class PlanCommandExecutor implements SlashCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlanCommandExecutor.class);

    private final ConversationSession conversationSession;

    /**
     * 计划文件路径
     */
    private static final String PLAN_FILE_NAME = ".claude/plan.md";

    public PlanCommandExecutor(ConversationSession conversationSession) {
        this.conversationSession = conversationSession;
    }

    @Override
    public SlashCommandResult execute(String sessionId, String args) {
        try {
            log.info("执行 /plan 命令，sessionId={}, args={}", sessionId, args);

            // 解析参数
            if (args == null || args.isBlank()) {
                // 无参数：启用计划模式或查看当前计划
                return handlePlanToggle();
            }

            String trimmedArgs = args.trim().toLowerCase();

            if ("open".equals(trimmedArgs)) {
                // /plan open - 在编辑器中打开
                return handlePlanOpen();
            }

            if ("off".equals(trimmedArgs) || "disable".equals(trimmedArgs)) {
                // /plan off - 禁用计划模式
                return handlePlanDisable();
            }

            // 其他参数：设置计划描述
            return handlePlanSetDescription(args);

        } catch (Exception e) {
            log.error("/plan 命令执行失败", e);
            return SlashCommandResult.Error.of("计划模式操作失败：" + e.getMessage());
        }
    }

    /**
     * 处理计划模式切换
     */
    private SlashCommandResult handlePlanToggle() {
        // 检查当前是否已启用计划模式
        Boolean planEnabled = conversationSession.getMetadata("plan_enabled");
        String planContent = conversationSession.getMetadata("plan_content");
        String planFilePath = conversationSession.getMetadata("plan_file_path");

        if (planEnabled == null || !planEnabled) {
            // 启用计划模式
            conversationSession.setMetadata("plan_enabled", true);

            // 创建默认计划文件
            String defaultPlan = createDefaultPlan();
            String filePath = ensurePlanFile(defaultPlan);

            conversationSession.setMetadata("plan_content", defaultPlan);
            conversationSession.setMetadata("plan_file_path", filePath);

            return SlashCommandResult.PlanMode.enabled(defaultPlan, filePath);
        } else {
            // 已启用，显示当前计划
            if (planContent == null) {
                planContent = "暂无计划内容";
            }
            if (planFilePath == null) {
                planFilePath = "未指定文件路径";
            }

            String result = String.format(
                "📋 计划模式已启用\n\n" +
                "计划文件：%s\n\n" +
                "当前计划：\n%s",
                planFilePath,
                planContent
            );

            return SlashCommandResult.Text.of(result);
        }
    }

    /**
     * 处理打开计划文件
     */
    private SlashCommandResult handlePlanOpen() {
        String planFilePath = conversationSession.getMetadata("plan_file_path");

        if (planFilePath == null) {
            // 创建默认计划文件
            String defaultPlan = createDefaultPlan();
            planFilePath = ensurePlanFile(defaultPlan);
            conversationSession.setMetadata("plan_file_path", planFilePath);
        }

        // 检查文件是否存在
        Path planPath = Paths.get(planFilePath);
        if (!Files.exists(planPath)) {
            // 重新创建文件
            String defaultPlan = createDefaultPlan();
            ensurePlanFile(defaultPlan);
        }

        String result = String.format(
            "📋 计划文件：%s\n\n" +
            "请使用编辑器打开该文件查看和编辑计划。\n\n" +
            "提示：在许多编辑器中，可以使用 Ctrl+P 或 Cmd+P 快速打开文件。",
            planFilePath
        );

        return SlashCommandResult.Text.of(result);
    }

    /**
     * 处理禁用计划模式
     */
    private SlashCommandResult handlePlanDisable() {
        conversationSession.setMetadata("plan_enabled", false);

        String result = "📋 计划模式已禁用\n\n" +
                "计划文件仍然保留，再次使用 /plan 可重新启用。";

        return SlashCommandResult.Text.of(result);
    }

    /**
     * 处理设置计划描述
     */
    private SlashCommandResult handlePlanSetDescription(String description) {
        // 启用计划模式（如果未启用）
        Boolean planEnabled = conversationSession.getMetadata("plan_enabled");
        if (planEnabled == null || !planEnabled) {
            conversationSession.setMetadata("plan_enabled", true);
        }

        // 更新计划内容
        String existingPlan = conversationSession.getMetadata("plan_content");
        String newPlan;

        if (existingPlan != null && !existingPlan.isBlank()) {
            newPlan = existingPlan + "\n\n## 更新\n\n" + description;
        } else {
            newPlan = "## 计划\n\n" + description;
        }

        conversationSession.setMetadata("plan_content", newPlan);

        // 更新计划文件
        String planFilePath = conversationSession.getMetadata("plan_file_path");
        if (planFilePath != null) {
            writePlanFileStr(newPlan, planFilePath);
        } else {
            planFilePath = ensurePlanFile(newPlan);
            conversationSession.setMetadata("plan_file_path", planFilePath);
        }

        return SlashCommandResult.PlanMode.enabled(newPlan, planFilePath);
    }

    /**
     * 创建默认计划
     */
    private String createDefaultPlan() {
        return """
            # 项目计划

            ## 目标

            暂无具体目标，请在下方添加。

            ## 任务列表

            - [ ] 待添加任务

            ## 备注

            暂无备注。

            ---
            *此计划由 AI 助手生成，使用 /plan 命令管理*
            """;
    }

    /**
     * 确保计划文件存在
     */
    private String ensurePlanFile(String content) {
        String userHome = System.getProperty("user.home");
        Path planDir = Paths.get(userHome, ".claude");

        try {
            // 创建目录
            if (!Files.exists(planDir)) {
                Files.createDirectories(planDir);
            }

            // 创建文件
            Path planFile = planDir.resolve("plan.md");
            return writePlanFileStr(content, planFile.toString());

        } catch (IOException e) {
            log.error("创建计划文件失败", e);
            throw new RuntimeException("创建计划文件失败：" + e.getMessage(), e);
        }
    }

    /**
     * 写入计划文件
     */
    private String writePlanFileStr(String content, String planFilePath) {
        try {
            Path planFile = Paths.get(planFilePath);
            Files.writeString(planFile, content);
            return planFile.toString();
        } catch (IOException e) {
            log.error("写入计划文件失败", e);
            throw new RuntimeException("写入计划文件失败：" + e.getMessage(), e);
        }
    }
}
