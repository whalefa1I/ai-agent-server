package demo.k8s.agent.coordinator;

import demo.k8s.agent.toolsystem.McpToolProvider;
import demo.k8s.agent.toolsystem.ToolFeatureFlags;
import demo.k8s.agent.toolsystem.ToolPermissionContext;
import demo.k8s.agent.toolsystem.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Worker Agent 执行器，与 Claude Code 的 Worker Agent 循环对齐。
 * <p>
 * 职责：
 * <ul>
 *   <li>为不同类型的 Worker 选择专属工具集</li>
 *   <li>执行 Worker Agent 的独立对话循环</li>
 *   <li>通过邮箱与 Coordinator 通信</li>
 * </ul>
 */
@Service
public class WorkerAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkerAgentExecutor.class);

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final ToolRegistry toolRegistry;
    private final ToolPermissionContext toolPermissionContext;
    private final ToolFeatureFlags toolFeatureFlags;
    private final McpToolProvider mcpToolProvider;
    private final CoordinatorState coordinatorState;
    private final InMemoryWorkerMailbox mailbox;

    public WorkerAgentExecutor(
            ChatModel chatModel,
            ToolCallingManager toolCallingManager,
            ToolRegistry toolRegistry,
            ToolPermissionContext toolPermissionContext,
            ToolFeatureFlags toolFeatureFlags,
            McpToolProvider mcpToolProvider,
            CoordinatorState coordinatorState,
            InMemoryWorkerMailbox mailbox) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolRegistry = toolRegistry;
        this.toolPermissionContext = toolPermissionContext;
        this.toolFeatureFlags = toolFeatureFlags;
        this.mcpToolProvider = mcpToolProvider;
        this.coordinatorState = coordinatorState;
        this.mailbox = mailbox;
    }

    /**
     * 异步执行 Worker Agent
     */
    @Async("workerExecutor")
    public void executeWorker(String taskId, String agentType) {
        log.info("启动 Worker Agent: taskId={}, agentType={}", taskId, agentType);

        // 获取任务状态
        var taskOpt = coordinatorState.getTask(taskId);
        if (taskOpt.isEmpty()) {
            log.error("任务不存在：{}", taskId);
            return;
        }

        var task = taskOpt.get();
        if (!coordinatorState.startTask(taskId)) {
            log.error("无法启动任务：{} (状态：{})", taskId, task.status());
            return;
        }

        // 构建 Worker 专属工具集
        List<ToolCallback> workerTools = selectToolsForAgent(agentType);
        log.debug("Worker {} 加载工具数量：{}", agentType, workerTools.size());

        // 构建 Worker 系统提示
        String systemPrompt = buildWorkerSystemPrompt(agentType, task.goal());

        // Worker 对话循环
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // 添加初始目标
        messages.add(new UserMessage("你的任务是：" + task.goal()));

        int turnCount = 0;
        int maxTurns = 20; // Worker 最大轮次

        try {
            while (true) {
                // 检查取消请求
                if (mailbox.isCancelRequested(taskId)) {
                    log.info("Worker 收到取消请求：{}", taskId);
                    coordinatorState.stopTask(taskId);
                    break;
                }

                // 检查超时（10 分钟）
                if (Duration.between(task.createdAt(), Instant.now()).toMinutes() > 10) {
                    log.warn("Worker 超时：{}", taskId);
                    coordinatorState.failTask(taskId, "timeout");
                    break;
                }

                // 检查最大轮次
                if (turnCount >= maxTurns) {
                    log.warn("Worker 达到最大轮次：{} ({} turns)", taskId, turnCount);
                    coordinatorState.failTask(taskId, "max_turns_exceeded");
                    break;
                }

                turnCount++;

                // 处理收件箱消息
                List<String> newMessages = coordinatorState.drainMessages(taskId);
                for (String msg : newMessages) {
                    messages.add(new UserMessage(msg));
                    log.debug("Worker 收到新消息：{}", truncate(msg, 50));
                }

                // 如果没有新消息且队列为空，等待一会儿
                if (newMessages.isEmpty() && messages.size() <= 2) {
                    Thread.sleep(500);
                    continue;
                }

                // 调用模型
                ToolCallingChatOptions options =
                        ToolCallingChatOptions.builder().toolCallbacks(workerTools).build();
                Prompt prompt = new Prompt(messages, options);

                ChatResponse response;
                try {
                    response = chatModel.call(prompt);
                } catch (Exception e) {
                    log.error("Worker 模型调用失败：{}", taskId, e);
                    coordinatorState.failTask(taskId, "model_error: " + e.getMessage());
                    break;
                }

                // 检查是否有工具调用
                if (hasToolCalls(response)) {
                    log.debug("Worker 有 {} 个工具调用", response.getResult().getOutput().getToolCalls().size());

                    // 执行工具
                    ToolExecutionResult toolResult;
                    try {
                        toolResult = toolCallingManager.executeToolCalls(prompt, response);
                    } catch (Exception e) {
                        log.error("Worker 工具执行失败：{}", taskId, e);
                        coordinatorState.failTask(taskId, "tool_error: " + e.getMessage());
                        break;
                    }

                    // 处理结果
                    if (toolResult.returnDirect()) {
                        // 直接返回结果
                        String finalOutput = extractText(response);
                        coordinatorState.completeTask(taskId, finalOutput);
                        log.info("Worker 完成（returnDirect）: {} - {} chars", taskId, finalOutput.length());
                        break;
                    }

                    // 更新消息历史
                    messages = toolResult.conversationHistory();

                    // 将输出发送到 Coordinator
                    for (var toolCall : response.getResult().getOutput().getToolCalls()) {
                        String output = findToolResponse(toolResult, toolCall.id());
                        if (output != null && !output.isBlank()) {
                            coordinatorState.addOutput(taskId, output);
                        }
                    }

                    log.debug("Worker turn {} 完成，消息数：{}", turnCount, messages.size());

                } else {
                    // 没有工具调用，任务完成
                    String finalAnswer = extractText(response);
                    coordinatorState.completeTask(taskId, finalAnswer);
                    log.info("Worker 完成：{} - {} chars", taskId, finalAnswer.length());
                    break;
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Worker 被中断：{}", taskId);
            coordinatorState.failTask(taskId, "interrupted");
        } catch (Exception e) {
            log.error("Worker 异常：{}", taskId, e);
            coordinatorState.failTask(taskId, "error: " + e.getMessage());
        }
    }

    /**
     * 后台启动 Worker，立即返回
     */
    public CompletableFuture<CoordinatorState.TaskResult> spawnBackgroundWorker(
            String taskId,
            String agentType) {

        executeWorker(taskId, agentType);
        return coordinatorState.waitForTaskAsync(taskId);
    }

    /**
     * 同步执行 Worker（阻塞直到完成）
     */
    public CoordinatorState.TaskResult runSynchronousWorker(
            String taskId,
            String agentType,
            Duration timeout) throws Exception {

        CompletableFuture<CoordinatorState.TaskResult> future = coordinatorState.waitForTaskAsync(taskId);
        executeWorker(taskId, agentType);
        return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // ===== 工具选择 =====

    /**
     * 根据 Agent 类型选择工具集
     */
    private List<ToolCallback> selectToolsForAgent(String agentType) {
        List<ToolCallback> allTools =
                toolRegistry.filteredCallbacks(
                        toolPermissionContext, toolFeatureFlags, mcpToolProvider.loadMcpTools());

        return switch (agentType) {
            case "bash" -> {
                // Bash Agent：仅 Bash 相关工具
                yield filterToolsByPrefix(allTools, "Bash");
            }
            case "explore" -> {
                // Explore Agent：只读工具
                yield filterReadonlyTools(allTools);
            }
            case "plan" -> {
                // Plan Agent：规划类工具
                yield filterToolsByCategory(allTools, demo.k8s.agent.toolsystem.ToolCategory.PLANNING);
            }
            case "edit" -> {
                // Edit Agent：文件编辑工具
                yield filterToolsByPrefix(allTools, "Write", "Edit", "Read");
            }
            default -> {
                // General Agent：所有工具
                yield allTools;
            }
        };
    }

    private List<ToolCallback> filterToolsByPrefix(List<ToolCallback> tools, String... prefixes) {
        Set<String> prefixSet = Set.of(prefixes);
        return tools.stream()
                .filter(t -> prefixSet.stream().anyMatch(p -> t.getToolDefinition().name().startsWith(p)))
                .toList();
    }

    private List<ToolCallback> filterToolsByCategory(
            List<ToolCallback> tools,
            demo.k8s.agent.toolsystem.ToolCategory category) {
        // 简化实现：实际需要根据 ToolCategory 过滤
        return tools;
    }

    private List<ToolCallback> filterReadonlyTools(List<ToolCallback> tools) {
        // 简化实现：实际需要根据 isReadOnly 过滤
        return tools.stream()
                .filter(t -> {
                    String name = t.getToolDefinition().name().toLowerCase();
                    return name.contains("read") || name.contains("grep") || name.contains("glob")
                            || name.contains("skill");
                })
                .toList();
    }

    // ===== 系统提示构建 =====

    /**
     * 构建 Worker 专属系统提示
     */
    private String buildWorkerSystemPrompt(String agentType, String goal) {
        String basePrompt = """
                你是一个专业的 AI 助手 Worker，正在执行一项委派任务。
                请专注于你的专长领域，高效完成任务。
                如果有问题或需要更多信息，请向 Coordinator 请求。
                完成后请输出最终结果。
                """;

        String rolePrompt = switch (agentType) {
            case "bash" -> """
                    【角色】Shell 专家
                    【专长】执行 shell 命令、脚本编写、系统操作
                    【注意】请确保命令安全，避免破坏性操作
                    """;
            case "explore" -> """
                    【角色】代码探索专家
                    【专长】读取文件、搜索代码、理解项目结构
                    【注意】只读操作，不修改任何文件
                    """;
            case "plan" -> """
                    【角色】规划专家
                    【专长】任务分解、方案设计、风险评估
                    【注意】输出清晰的结构化计划
                    """;
            case "edit" -> """
                    【角色】代码编辑专家
                    【专长】文件读写、代码修改、重构
                    【注意】修改前请确认理解需求
                    """;
            default -> """
                    【角色】通用助手
                    【专长】综合问题解决
                    【注意】合理委派子任务或亲自处理
                    """;
        };

        return basePrompt + "\n" + rolePrompt + "\n任务目标：" + goal;
    }

    // ===== 辅助方法 =====

    private static boolean hasToolCalls(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return false;
        }
        return !response.getResult().getOutput().getToolCalls().isEmpty();
    }

    private static String extractText(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }

    private static String findToolResponse(ToolExecutionResult result, String toolCallId) {
        // 简化实现
        return "executed";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
