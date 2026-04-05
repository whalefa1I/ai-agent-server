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
 *   <li>管理基于规则的权限（精确/前缀/通配符）</li>
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
     * 持久化权限规则列表（支持精确/前缀/通配符匹配）
     */
    private final CopyOnWriteArrayList<PermissionRule> permissionRules = new CopyOnWriteArrayList<>();

    /**
     * 待确认的权限请求队列
     */
    private final CopyOnWriteArrayList<PermissionRequest> pendingRequests = new CopyOnWriteArrayList<>();

    /**
     * 持久化授权文件路径
     */
    private final Path persistentGrantsFile;

    /**
     * 持久化规则文件路径
     */
    private final Path persistentRulesFile;

    public PermissionManager(EventBus eventBus, MetricsCollector metricsCollector) {
        this.eventBus = eventBus;
        this.metricsCollector = metricsCollector;

        // 默认存储位置：用户主目录/.claude/
        String userHome = System.getProperty("user.home");
        Path claudeDir = Path.of(userHome, ".claude");
        try {
            Files.createDirectories(claudeDir);
        } catch (IOException e) {
            log.warn("创建配置目录失败：{}", claudeDir, e);
        }
        this.persistentGrantsFile = claudeDir.resolve("permission-grants.json");
        this.persistentRulesFile = claudeDir.resolve("permission-rules.json");
        loadPersistentGrants();
        loadPersistentRules();
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

        // 3. PLAN 模式：不执行任何工具
        if (ctx.mode() == ToolPermissionMode.PLAN) {
            log.debug("权限模式为 PLAN，工具 {} 拒绝执行", tool.name());
            return createConfirmationRequest(tool, input, "PLAN 模式下不执行工具");
        }

        // 4. ACCEPT_EDITS 模式：自动接受编辑类工具
        if (ctx.mode() == ToolPermissionMode.ACCEPT_EDITS) {
            if (isEditTool(tool.name())) {
                log.debug("ACCEPT_EDITS 模式下工具 {} 为编辑类工具，自动放行", tool.name());
                return null;
            }
        }

        // 5. AUTO 模式：由工具自身的 checkPermissions 决定（后续可扩展为 AI 分类器）
        if (ctx.mode() == ToolPermissionMode.AUTO) {
            String validationResult = tool.validateInput(input);
            if (validationResult != null) {
                return createConfirmationRequest(tool, input, "AI 模式：输入验证失败 - " + validationResult);
            }
            // 工具自身的权限检查
            PermissionResult permissionResult = tool.checkPermissions(input.toString(), ctx);
            if (!permissionResult.isAllowed()) {
                String reason = permissionResult.getDenyReason() != null ? permissionResult.getDenyReason() : "权限检查拒绝";
                return createConfirmationRequest(tool, input, "AI 模式：" + reason);
            }
            return null;
        }

        // 6. DONT_ASK 模式：检查是否有历史授权记录
        if (ctx.mode() == ToolPermissionMode.DONT_ASK) {
            // 检查是否有该工具的授权记录
            if (hasHistoryAuthorization(tool.name(), input)) {
                log.debug("DONT_ASK 模式下工具 {} 有历史授权，放行", tool.name());
                return null;
            }
        }

        // 7. 检查是否始终允许（持久化授权）
        if (alwaysAllowedTools.contains(tool.name())) {
            log.debug("工具 {} 在始终允许列表中，放行", tool.name());
            return null;
        }

        // 8. 检查会话授权
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

        // 9. 检查持久化规则匹配
        if (matchesPermissionRules(tool.name(), input)) {
            log.debug("工具 {} 的调用匹配持久化规则，放行", tool.name());
            return null;
        }

        // 10. 只读工具直接放行
        if (tool.isReadOnly(input)) {
            log.debug("工具 {} 为只读操作，放行", tool.name());
            return null;
        }

        // 11. 需要用户确认
        return createConfirmationRequest(tool, input, null);
    }

    /**
     * 检查是否为编辑类工具
     */
    private boolean isEditTool(String toolName) {
        return "file_edit".equals(toolName) || "file_write".equals(toolName) ||
               "Edit".equals(toolName) || "Write".equals(toolName);
    }

    /**
     * 检查是否有历史授权（用于 DONT_ASK 模式）
     */
    private boolean hasHistoryAuthorization(String toolName, JsonNode input) {
        // 检查会话授权
        List<PermissionGrant> grants = sessionGrants.get(toolName);
        if (grants != null && !grants.isEmpty()) {
            return true;
        }

        // 检查持久化规则
        return matchesPermissionRules(toolName, input);
    }

    /**
     * 检查工具调用是否匹配任何持久化规则
     */
    private boolean matchesPermissionRules(String toolName, JsonNode input) {
        // 提取命令字符串（对于 bash 工具）
        String command = extractCommandString(toolName, input);
        if (command == null) {
            return false;
        }

        for (PermissionRule rule : permissionRules) {
            if (rule.matches(command)) {
                log.debug("命令 '{}' 匹配规则 '{}'", command, rule.rule());
                return true;
            }
        }
        return false;
    }

    /**
     * 从工具输入中提取命令字符串
     */
    private String extractCommandString(String toolName, JsonNode input) {
        // Bash 工具：提取 command 字段
        if ("bash".equals(toolName) || "Bash".equals(toolName)) {
            JsonNode cmdNode = input.get("command");
            if (cmdNode != null && cmdNode.isTextual()) {
                return cmdNode.asText();
            }
        }

        // 文件工具：提取 file_path 字段
        if (toolName.contains("file") || toolName.contains("File")) {
            JsonNode pathNode = input.get("file_path");
            if (pathNode != null && pathNode.isTextual()) {
                return "file:" + pathNode.asText();
            }
        }

        return null;
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

    // ===== 持久化规则管理 =====

    private void loadPersistentRules() {
        if (!Files.exists(persistentRulesFile)) {
            log.debug("持久化规则文件不存在：{}", persistentRulesFile);
            return;
        }

        try {
            String content = Files.readString(persistentRulesFile);
            if (content.isBlank()) {
                return;
            }

            // JSON 数组解析：["Bash(ls -la)", "Bash(npm:*)", "Bash(cd *)"]
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);

            if (node.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : node) {
                    String ruleStr = item.asText();
                    // 解析规则：ToolName(pattern) 格式
                    PermissionRule rule = parseToolRule(ruleStr);
                    if (rule != null) {
                        permissionRules.add(rule);
                    }
                }
                log.info("已加载 {} 个持久化权限规则", permissionRules.size());
            }
        } catch (IOException e) {
            log.warn("加载持久化规则失败：{}", persistentRulesFile, e);
        }
    }

    /**
     * 解析工具规则字符串，如 "Bash(ls -la)", "Bash(npm:*)", "Bash(cd *)"
     */
    private PermissionRule parseToolRule(String ruleStr) {
        if (ruleStr == null || ruleStr.isBlank()) {
            return null;
        }

        // 格式：ToolName(pattern)
        int openParen = ruleStr.indexOf('(');
        int closeParen = ruleStr.lastIndexOf(')');

        if (openParen == -1 || closeParen == -1 || openParen >= closeParen) {
            log.warn("无效的规则格式：{}", ruleStr);
            return null;
        }

        String toolName = ruleStr.substring(0, openParen).trim();
        String pattern = ruleStr.substring(openParen + 1, closeParen).trim();

        // 使用 ShellRuleMatcher 解析规则
        return ShellRuleMatcher.parseRule(pattern);
    }

    private void savePersistentRules() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // 将规则转换为字符串列表
            List<String> ruleStrings = permissionRules.stream()
                    .map(r -> r.rule())
                    .toList();
            String json = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(ruleStrings);
            Files.writeString(persistentRulesFile, json);
            log.debug("已保存持久化规则到 {}", persistentRulesFile);
        } catch (IOException e) {
            log.warn("保存持久化规则失败：{}", persistentRulesFile, e);
        }
    }

    /**
     * 添加持久化规则
     *
     * @param ruleStr 规则字符串，如 "Bash(ls -la)"
     * @return 是否添加成功
     */
    public boolean addPermissionRule(String ruleStr) {
        PermissionRule rule = parseToolRule(ruleStr);
        if (rule == null) {
            return false;
        }

        // 检查是否已存在
        for (PermissionRule existing : permissionRules) {
            if (existing.rule().equals(ruleStr)) {
                return false;
            }
        }

        permissionRules.add(rule);
        savePersistentRules();
        log.info("已添加权限规则：{}", ruleStr);
        return true;
    }

    /**
     * 移除持久化规则
     */
    public boolean removePermissionRule(String ruleStr) {
        boolean removed = permissionRules.removeIf(r -> r.rule().equals(ruleStr));
        if (removed) {
            savePersistentRules();
            log.info("已移除权限规则：{}", ruleStr);
        }
        return removed;
    }

    /**
     * 获取所有持久化规则
     */
    public List<PermissionRule> getPermissionRules() {
        return List.copyOf(permissionRules);
    }

    /**
     * 为工具调用生成建议的规则
     */
    public List<String> suggestRulesForCommand(String toolName, JsonNode input) {
        String command = extractCommandString(toolName, input);
        if (command == null) {
            return List.of();
        }

        // 使用 ShellRuleMatcher 生成建议
        List<PermissionRule> suggestions = ShellRuleMatcher.generateSuggestions(command, 3);
        return suggestions.stream()
                .map(r -> toolName + "(" + r.rule() + ")")
                .toList();
    }
}
