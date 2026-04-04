package demo.k8s.agent.toolsystem;

import com.fasterxml.jackson.databind.JsonNode;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.observability.events.Event.PermissionRequestedEvent;
import demo.k8s.agent.observability.logging.StructuredLogger;
import demo.k8s.agent.observability.metrics.MetricsCollector;
import demo.k8s.agent.observability.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 权限管理器，与 Claude Code 的 {@code src/utils/permissions.ts} 对齐。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理会话级授权缓存（内存）</li>
 *   <li>管理持久化授权（文件系统）</li>
 *   <li>检查工具调用是否需要用户确认</li>
 *   <li>处理用户授权响应</li>
 * </ul>
 */
@Service
public class PermissionManager {

    private static final Logger log = LoggerFactory.getLogger(PermissionManager.class);

    private final EventBus eventBus;
    private final MetricsCollector metricsCollector;

    /**
     * 会话级授权缓存：工具名 -> 授权记录列表
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PermissionGrant>> sessionGrants = new ConcurrentHashMap<>();

    /**
     * 持久化授权工具集合（从文件加载）
     */
    private final Set<String> alwaysAllowedTools = ConcurrentHashMap.newKeySet();

    /**
     * 待确认的权限请求队列
     */
    private final CopyOnWriteArrayList<PermissionRequest> pendingRequests = new CopyOnWriteArrayList<>();

    /**
     * 持久化授权文件路径
     */
    private final Path persistentGrantsFile;

    public PermissionManager(EventBus eventBus, MetricsCollector metricsCollector) {
        this.eventBus = eventBus;
        this.metricsCollector = metricsCollector;

        // 默认存储位置：用户主目录/.claude/permission-grants.json
        String userHome = System.getProperty("user.home");
        Path claudeDir = Path.of(userHome, ".claude");
        try {
            Files.createDirectories(claudeDir);
        } catch (IOException e) {
            log.warn("创建配置目录失败：{}", claudeDir, e);
        }
        this.persistentGrantsFile = claudeDir.resolve("permission-grants.json");
        loadPersistentGrants();
    }

    /**
     * 检查是否需要用户确认
     *
     * @param tool 工具定义
     * @param input 工具输入 JSON
     * @param ctx 权限上下文
     * @return null = 允许执行；Otherwise = 需要确认的请求
     */
    public PermissionRequest requiresPermission(
            ClaudeLikeTool tool,
            JsonNode input,
            ToolPermissionContext ctx) {

        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(input, "input");

        // 1. BYPASS 模式：直接放行
        if (ctx.mode() == ToolPermissionMode.BYPASS) {
            log.debug("权限模式为 BYPASS，工具 {} 直接放行", tool.name());
            return null;
        }

        // 2. READ_ONLY 模式：只读工具放行
        if (ctx.mode() == ToolPermissionMode.READ_ONLY) {
            if (tool.isReadOnly(input) || tool.defaultReadOnlyHint()) {
                log.debug("READ_ONLY 模式下工具 {} 为只读，放行", tool.name());
                return null;
            }
            // 非只读工具需要确认
            return createConfirmationRequest(tool, input, "READ_ONLY 模式下需要确认");
        }

        // 3. 检查是否始终允许（持久化授权）
        if (alwaysAllowedTools.contains(tool.name())) {
            log.debug("工具 {} 在始终允许列表中，放行", tool.name());
            return null;
        }

        // 4. 检查会话授权
        List<PermissionGrant> grants = sessionGrants.get(tool.name());
        if (grants != null && !grants.isEmpty()) {
            // 获取最新的授权
            PermissionGrant latest = grants.stream()
                    .max(Comparator.comparing(g -> g.grantedAt()))
                    .orElse(null);

            if (latest != null && latest.isValid()) {
                log.debug("工具 {} 有有效的会话授权，放行", tool.name());
                return null;
            }
        }

        // 5. 只读工具直接放行
        if (tool.isReadOnly(input)) {
            log.debug("工具 {} 为只读操作，放行", tool.name());
            return null;
        }

        // 6. 需要用户确认
        return createConfirmationRequest(tool, input, null);
    }

    /**
     * 处理用户授权响应
     *
     * @param response 用户响应
     * @return 处理后的权限结果
     */
    public PermissionResult handlePermissionResponse(PermissionResponse response) {
        log.info("处理权限响应：requestId={}, choice={}", response.requestId(), response.choice());

        // 查找对应的请求
        PermissionRequest request = pendingRequests.stream()
                .filter(r -> r.id().equals(response.requestId()))
                .findFirst()
                .orElse(null);

        if (request == null) {
            log.warn("未找到权限请求：{}", response.requestId());
            return PermissionResult.deny("未找到权限请求：" + response.requestId());
        }

        // 从待确认队列移除
        pendingRequests.remove(request);

        // 记录权限响应（结构化日志和事件）
        String sessionId = TraceContext.getSessionId();
        String userId = TraceContext.getUserId();
        long responseTimeMs = Duration.between(request.createdAt(), java.time.Instant.now()).toMillis();

        // 处理用户选择
        if (response.choice() == PermissionChoice.DENY) {
            log.info("用户拒绝了工具调用：{}", request.toolName());

            // 记录Denied 事件
            StructuredLogger.logPermissionRequest(sessionId, userId, request.toolName(), "DENY", responseTimeMs);
            eventBus.publish(new PermissionRequestedEvent(sessionId, userId, request.toolName(), "DENY", responseTimeMs));
            metricsCollector.recordPermissionRequest(userId, false);

            return PermissionResult.deny("用户拒绝了工具调用：" + request.toolName());
        }

        // 记录 Allowed 事件
        StructuredLogger.logPermissionRequest(sessionId, userId, request.toolName(), response.choice().name(), responseTimeMs);
        eventBus.publish(new PermissionRequestedEvent(sessionId, userId, request.toolName(), response.choice().name(), responseTimeMs));
        metricsCollector.recordPermissionRequest(userId, true);

        // 创建授权记录
        PermissionGrant grant = PermissionGrant.create(
                request.toolName(),
                response.choice(),
                request.level()
        );

        // 根据选择类型处理
        return switch (response.choice()) {
            case ALLOW_ONCE -> {
                // 单次允许：添加临时授权（5 分钟）
                addSessionGrant(grant);
                log.info("工具 {} 已授权（本次）", request.toolName());
                yield PermissionResult.allow();
            }

            case ALLOW_SESSION -> {
                // 会话允许：使用用户指定的时长
                Duration duration = response.getSessionDuration();
                PermissionGrant sessionGrant = new PermissionGrant(
                        grant.id(),
                        grant.toolName(),
                        grant.choice(),
                        grant.level(),
                        grant.grantedAt(),
                        grant.grantedAt().plus(duration)
                );
                addSessionGrant(sessionGrant);
                log.info("工具 {} 已授权（会话，{} 分钟）", request.toolName(), duration.toMinutes());
                yield PermissionResult.allow();
            }

            case ALLOW_ALWAYS -> {
                // 始终允许：添加到持久化列表
                alwaysAllowedTools.add(request.toolName());
                savePersistentGrants();
                log.info("工具 {} 已授权（始终）", request.toolName());
                yield PermissionResult.allow();
            }

            case DENY -> {
                // 已在上面处理
                yield PermissionResult.deny("用户拒绝了工具调用");
            }
        };
    }

    /**
     * 获取待确认的权限请求列表
     */
    public List<PermissionRequest> getPendingRequests() {
        return List.copyOf(pendingRequests);
    }

    /**
     * 清除待确认请求
     */
    public void clearPendingRequest(String requestId) {
        pendingRequests.removeIf(r -> r.id().equals(requestId));
    }

    /**
     * 获取所有会话授权
     */
    public List<PermissionGrant> getSessionGrants() {
        return sessionGrants.values().stream()
                .flatMap(List::stream)
                .filter(g -> !g.isExpired())
                .toList();
    }

    /**
     * 获取始终允许的工具列表
     */
    public Set<String> getAlwaysAllowedTools() {
        return Set.copyOf(alwaysAllowedTools);
    }

    /**
     * 撤销始终允许的工具授权
     */
    public void revokeAlwaysAllowed(String toolName) {
        boolean removed = alwaysAllowedTools.remove(toolName);
        if (removed) {
            savePersistentGrants();
            log.info("已撤销工具 {} 的始终允许授权", toolName);
        }
    }

    /**
     * 清除所有会话授权
     */
    public void clearSessionGrants() {
        sessionGrants.clear();
        log.info("已清除所有会话授权");
    }

    // ===== 内部方法 =====

    private PermissionRequest createConfirmationRequest(
            ClaudeLikeTool tool,
            JsonNode input,
            String extraReason) {

        PermissionLevel level = determineLevel(tool, input);
        String inputSummary = summarizeInput(input, 200);
        String riskExplanation = buildRiskExplanation(level, tool, extraReason);

        PermissionRequest request = PermissionRequest.create(
                tool.name(),
                tool.description(),
                level,
                inputSummary,
                riskExplanation
        );

        pendingRequests.add(request);
        log.info("创建权限请求：{} ({}), 等级：{}", request.toolName(), request.id(), request.level());

        return request;
    }

    private PermissionLevel determineLevel(ClaudeLikeTool tool, JsonNode input) {
        // 检查工具是否自报告为破坏性
        try {
            if (tool.isDestructive(input)) {
                return PermissionLevel.DESTRUCTIVE;
            }
        } catch (Exception e) {
            log.debug("检查 isDestructive 失败，使用默认分类", e);
        }

        // 根据类别判断
        return switch (tool.category()) {
            case AGENT -> PermissionLevel.AGENT_SPAWN;
            case EXTERNAL -> PermissionLevel.NETWORK;
            case FILE, FILE_SYSTEM -> {
                // 文件系统操作：检查是否只读
                if (tool.isReadOnly(input)) {
                    yield PermissionLevel.READ_ONLY;
                }
                yield PermissionLevel.MODIFY_STATE;
            }
            case SHELL -> {
                // Shell 命令需要检查具体内容
                if (isDangerousShellCommand(input)) {
                    yield PermissionLevel.DESTRUCTIVE;
                }
                yield PermissionLevel.MODIFY_STATE;
            }
            case PLANNING -> PermissionLevel.READ_ONLY;
            case SCHEDULING -> PermissionLevel.READ_ONLY;
            case MEMORY -> PermissionLevel.READ_ONLY;
            case EXPERIMENTAL, OTHER -> PermissionLevel.MODIFY_STATE;
        };
    }

    private boolean isDangerousShellCommand(JsonNode input) {
        // 简单的危险命令检测
        String command = input.has("command") ? input.get("command").asText("") : "";
        String lower = command.toLowerCase();

        // 危险命令模式
        String[] dangerous = {"rm -rf", "rm -fr", "dd if=", "> /dev/", ":(){:|:&};", "chmod -R 777"};
        for (String pattern : dangerous) {
            if (lower.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    private String summarizeInput(JsonNode input, int maxLen) {
        try {
            String json = input.toString();
            if (json.length() <= maxLen) {
                return json;
            }
            return json.substring(0, maxLen) + "...";
        } catch (Exception e) {
            return "[无法解析输入]";
        }
    }

    private String buildRiskExplanation(PermissionLevel level, ClaudeLikeTool tool, String extraReason) {
        StringBuilder sb = new StringBuilder();

        sb.append(switch (level) {
            case READ_ONLY -> "这是一个只读操作，不会修改任何文件或系统状态。";
            case MODIFY_STATE -> "此操作将修改文件系统状态，建议确认修改内容。";
            case NETWORK -> "此操作将发起网络请求，可能泄露信息或受到外部攻击。";
            case DESTRUCTIVE -> "⚠️ 这是一个破坏性操作，可能导致数据丢失或系统损坏，请仔细确认！";
            case AGENT_SPAWN -> "此操作将启动子 Agent，可能执行多个子任务并产生额外费用。";
        });

        if (extraReason != null) {
            sb.append(" ").append(extraReason);
        }

        return sb.toString();
    }

    private void addSessionGrant(PermissionGrant grant) {
        sessionGrants
                .computeIfAbsent(grant.toolName(), k -> new CopyOnWriteArrayList<>())
                .add(grant);
    }

    // ===== 持久化授权管理 =====

    private void loadPersistentGrants() {
        if (!Files.exists(persistentGrantsFile)) {
            log.debug("持久化授权文件不存在：{}", persistentGrantsFile);
            return;
        }

        try {
            String content = Files.readString(persistentGrantsFile);
            if (content.isBlank()) {
                return;
            }

            // 简单的 JSON 数组解析：["Tool1", "Tool2", ...]
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);

            if (node.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : node) {
                    alwaysAllowedTools.add(item.asText());
                }
                log.info("已加载 {} 个持久化授权", alwaysAllowedTools.size());
            }
        } catch (IOException e) {
            log.warn("加载持久化授权失败：{}", persistentGrantsFile, e);
        }
    }

    private void savePersistentGrants() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(alwaysAllowedTools);
            Files.writeString(persistentGrantsFile, json);
            log.debug("已保存持久化授权到 {}", persistentGrantsFile);
        } catch (IOException e) {
            log.warn("保存持久化授权失败：{}", persistentGrantsFile, e);
        }
    }
}
