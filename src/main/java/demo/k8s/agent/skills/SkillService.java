package demo.k8s.agent.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import demo.k8s.agent.config.SkillsProperties;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 技能服务 - 管理技能的加载、安装、卸载
 *
 * 基于 ClawHub 技能生态理念：
 * - 技能自带实现（.py, .js 脚本）
 * - 框架提供通用执行环境
 * - 从 SKILL.md 解析输入模式
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // 技能搜索目录（按优先级排序）
    private static final List<String> SKILL_SEARCH_PATHS = List.of(
            System.getProperty("user.dir") + "/skills",           // 项目 skills
            System.getProperty("user.home") + "/.agents/skills",  // 个人 skills
            System.getProperty("user.home") + "/.openclaw/skills" // 全局 managed skills
    );

    private final SkillRegistry skillRegistry;
    private final GenericSkillExecutor genericSkillExecutor;
    private final SkillsSnapshotService snapshotService;
    private final SkillsProperties skillsProperties;

    // 已加载的技能清单
    private final Map<String, SkillManifest> loadedSkills = new ConcurrentHashMap<>();

    // 最后加载的技能快照版本
    private volatile long lastLoadedSnapshotVersion = -1;

    /**
     * 技能快照缓存 - 避免每轮请求重建 prompt
     */
    private volatile SkillsSnapshot cachedSnapshot;

    /**
     * 技能快照 - 包含版本、合格技能列表和预构建的 prompt
     */
    private static class SkillsSnapshot {
        final long version;
        final List<SkillManifest> eligibleSkills;
        final String fullCatalogPrompt;
        final String compactCatalogPrompt;

        SkillsSnapshot(long version, List<SkillManifest> eligibleSkills,
                       String fullCatalogPrompt, String compactCatalogPrompt) {
            this.version = version;
            this.eligibleSkills = eligibleSkills;
            this.fullCatalogPrompt = fullCatalogPrompt;
            this.compactCatalogPrompt = compactCatalogPrompt;
        }
    }

    public SkillService(
            SkillRegistry skillRegistry,
            SkillExecutorRegistry skillExecutorRegistry,
            GenericSkillExecutor genericSkillExecutor,
            SkillsSnapshotService snapshotService,
            @Autowired(required = false) SkillsProperties skillsProperties) {
        this.skillRegistry = skillRegistry;
        this.genericSkillExecutor = genericSkillExecutor;
        this.snapshotService = snapshotService;
        this.skillsProperties = skillsProperties != null ? skillsProperties : new SkillsProperties();
        // 在构造函数中直接加载技能，确保在 Bean 使用前已就绪
        loadAllSkills();
    }

    /**
     * 技能清单
     */
    public static class SkillManifest {
        public String name;
        public String description;
        public String directory;
        public OpenClawMetadata metadata = new OpenClawMetadata();

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getDirectory() { return directory; }
        public OpenClawMetadata getMetadata() { return metadata; }

        public static class OpenClawMetadata {
            public String emoji;
            public List<String> os;
            public Requires requires = new Requires();
            public List<Installer> install;
            public Boolean always;
            public String homepage;

            public static class Requires {
                public List<String> bins;
                public List<String> anyBins;
                public List<String> env;
                public List<String> config;
            }

            public static class Installer {
                public String id;
                public String kind;
                public String formula;
                public String package_;
                public String url;
                public List<String> bins;
                public String label;
                public String archive;
                public Boolean extract;
                public Integer stripComponents;
                public String targetDir;
                public List<String> os;
            }
        }
    }

    /**
     * 加载所有技能
     */
    public void loadAllSkills() {
        loadedSkills.clear();

        for (String basePath : SKILL_SEARCH_PATHS) {
            Path skillsDir = Paths.get(basePath);
            if (!Files.exists(skillsDir)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(skillsDir, 2)) {
                paths.filter(Files::isDirectory)
                     .filter(dir -> dir.getFileName().toString().equals(basePath) ||
                                    Files.exists(dir.resolve("SKILL.md")))
                     .filter(dir -> !dir.equals(skillsDir))
                     .forEach(this::loadSkillFromDirectory);
            } catch (IOException e) {
                log.warn("扫描技能目录失败：{} - {}", basePath, e.getMessage());
            }
        }

        // 更新快照版本标记
        lastLoadedSnapshotVersion = snapshotService.getSnapshotVersion();

        // 构建并缓存 snapshot
        List<SkillManifest> eligibleSkills = loadedSkills.values().stream()
                .filter(this::isEligibleForCatalog)
                .sorted(Comparator.comparing(s -> s.name.toLowerCase(Locale.ROOT)))
                .toList();

        String fullPrompt = buildCatalogXml(eligibleSkills, false);
        String compactPrompt = buildCatalogXml(eligibleSkills, true);

        cachedSnapshot = new SkillsSnapshot(
                lastLoadedSnapshotVersion,
                eligibleSkills,
                applyBudgetLimits(fullPrompt, compactPrompt, 30_000),
                applyBudgetLimits(compactPrompt, compactPrompt, 30_000)
        );

        log.info("加载了 {} 个技能 (快照版本：{}, eligible: {})",
                loadedSkills.size(), lastLoadedSnapshotVersion, eligibleSkills.size());
    }

    /**
     * 应用预算限制，返回合适的 prompt 版本
     */
    private String applyBudgetLimits(String fullPrompt, String compactPrompt, int maxChars) {
        if (fullPrompt.length() <= maxChars) {
            return fullPrompt;
        }
        if (compactPrompt.length() <= maxChars) {
            return "⚠️ Skills catalog using compact format (descriptions omitted).\n" + compactPrompt;
        }
        // 极端情况下截断
        return truncatePrompt(compactPrompt, maxChars);
    }

    /**
     * 截断 prompt 以适应预算
     */
    private String truncatePrompt(String prompt, int maxChars) {
        if (prompt.length() <= maxChars) {
            return prompt;
        }
        // 找到最后一个完整的 skill 标签
        int cutoff = prompt.lastIndexOf("</skill>");
        if (cutoff != -1 && cutoff + 9 <= maxChars) {
            return prompt.substring(0, cutoff + 9) + "\n</available_skills>\n";
        }
        return prompt.substring(0, Math.min(maxChars, prompt.length()));
    }

    /**
     * 构建 Skills catalog 提示词（用于注入到 system message）。
     * <p>
     * 采用混合模式：
     * - 默认注入可用技能目录（name/description/location）
     * - 模型可选择：
     *   1. 直接调用 skill_<name> 工具（快捷方式）
     *   2. 使用文件读取工具读取 SKILL.md 获取详细指令
     * <p>
     * 使用 snapshot 缓存避免每轮重建 prompt。
     */
    public String buildSkillsPrompt() {
        // 检查是否需要重新加载（快照版本变化时）
        if (snapshotService.getSnapshotVersion() != lastLoadedSnapshotVersion) {
            log.info("检测到技能快照版本变化 ({} -> {})，重新加载技能",
                    lastLoadedSnapshotVersion, snapshotService.getSnapshotVersion());
            loadAllSkills();
        }

        // 使用 cached snapshot（如果已加载）
        if (cachedSnapshot != null) {
            log.debug("使用技能 snapshot 缓存：version={}", cachedSnapshot.version);
            return cachedSnapshot.fullCatalogPrompt;
        }

        // 如果没有 cached snapshot 且没有技能，返回空字符串
        return "";
    }

    /**
     * 构建 catalog XML prompt（OpenClaw 风格）
     * <p>
     * 输出格式与 OpenClaw 官方实现对齐：
     * - XML 格式：&lt;available_skills&gt;&lt;skill&gt;...&lt;/skill&gt;&lt;/available_skills&gt;
     * - 每个技能包含：name, description, location
     * - location 为 SKILL.md 的绝对路径（home 路径压缩为 ~/）
     * <p>
     * 模型使用方式：
     * - 使用 file_read 工具读取 skill 的 SKILL.md 文件（当任务与 description 匹配时）
     * - 技能文件中的相对路径应相对于 skill 目录解析
     */
    private String buildCatalogXml(List<SkillManifest> skills, boolean compact) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\nThe following skills provide specialized instructions for specific tasks.");
        sb.append("\nUse the file_read tool to load a skill's file when the task matches its description.");
        sb.append("\nWhen a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.");
        sb.append("\n\n<available_skills>\n");

        for (SkillManifest s : skills) {
            String name = safeTrim(s.name);
            if (name.isEmpty()) {
                continue;
            }
            String description = safeTrim(s.description);
            String location = compactHomePath(joinPath(s.directory, "SKILL.md"));

            sb.append("  <skill>\n");
            sb.append("    <name>").append(escapeXml(name)).append("</name>\n");
            sb.append("    <description>").append(escapeXml(description)).append("</description>\n");
            sb.append("    <location>").append(escapeXml(location)).append("</location>\n");
            sb.append("  </skill>\n");
        }

        sb.append("</available_skills>\n");
        return sb.toString();
    }

    /**
     * OpenClaw 风格 gating（完整实现）：
     * - metadata.always: true → 直接通过
     * - metadata.os: 限定平台（win32/linux/darwin）
     * - metadata.requires.bins: 所有二进制必须存在
     * - metadata.requires.anyBins: 任一二进制存在即可
     * - metadata.requires.env：所有环境变量必须存在
     * - metadata.requires.config：从 SkillsProperties 配置判断 truthy
     */
    private boolean isEligibleForCatalog(SkillManifest manifest) {
        if (manifest == null) return false;
        SkillManifest.OpenClawMetadata md = manifest.metadata;
        if (md != null && Boolean.TRUE.equals(md.always)) {
            return true;
        }

        // os gate
        if (md != null && md.os != null && !md.os.isEmpty()) {
            String platform = resolveRuntimePlatform();
            boolean ok = md.os.stream()
                    .filter(Objects::nonNull)
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(s -> s.equals(platform));
            if (!ok) return false;
        }

        // env gate
        if (md != null && md.requires != null && md.requires.env != null && !md.requires.env.isEmpty()) {
            for (String k : md.requires.env) {
                if (k == null || k.isBlank()) continue;
                String v = System.getenv(k.trim());
                if (v == null || v.isBlank()) {
                    return false;
                }
            }
        }

        // bins gate (all required)
        if (md != null && md.requires != null && md.requires.bins != null && !md.requires.bins.isEmpty()) {
            for (String bin : md.requires.bins) {
                if (bin == null || bin.isBlank()) continue;
                if (!hasBinaryOnPath(bin.trim())) {
                    return false;
                }
            }
        }

        // anyBins gate (any one is enough)
        if (md != null && md.requires != null && md.requires.anyBins != null && !md.requires.anyBins.isEmpty()) {
            boolean found = false;
            for (String bin : md.requires.anyBins) {
                if (bin == null || bin.isBlank()) continue;
                if (hasBinaryOnPath(bin.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        // config gate
        if (md != null && md.requires != null && md.requires.config != null && !md.requires.config.isEmpty()) {
            for (String configKey : md.requires.config) {
                if (configKey == null || configKey.isBlank()) continue;
                if (!skillsProperties.isConfigTruthy(configKey.trim())) {
                    return false;
                }
            }
        }

        return true;
    }

    private static String resolveRuntimePlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) return "win32";
        if (osName.contains("mac") || osName.contains("darwin")) return "darwin";
        return "linux";
    }

    private static boolean hasBinaryOnPath(String bin) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return false;
        String[] parts = path.split(File.pathSeparator);

        boolean isWindows = "win32".equals(resolveRuntimePlatform());
        List<String> candidates = new ArrayList<>();
        candidates.add(bin);
        if (isWindows && !bin.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            candidates.add(bin + ".exe");
            candidates.add(bin + ".cmd");
            candidates.add(bin + ".bat");
        }

        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            for (String c : candidates) {
                Path candidate = Paths.get(p).resolve(c);
                try {
                    if (Files.exists(candidate) && !Files.isDirectory(candidate)) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // ignore and continue
                }
            }
        }
        return false;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String joinPath(String baseDir, String fileName) {
        if (baseDir == null || baseDir.isBlank()) {
            return fileName;
        }
        try {
            return Paths.get(baseDir).resolve(fileName).toString();
        } catch (Exception e) {
            // fallback：避免路径异常导致 prompt 构建失败
            return baseDir + "/" + fileName;
        }
    }

    private static String compactHomePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path;
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            String normalizedHome = home.replace('\\', '/');
            String normalizedPath = p.replace('\\', '/');
            if (normalizedPath.startsWith(normalizedHome.endsWith("/") ? normalizedHome : (normalizedHome + "/"))) {
                String suffix = normalizedPath.substring((normalizedHome.endsWith("/") ? normalizedHome : (normalizedHome + "/")).length());
                return "~/" + suffix;
            }
        }
        return p.replace('\\', '/');
    }

    private static String escapeXml(String str) {
        if (str == null) {
            return "";
        }
        return str
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * 获取已加载技能数量
     */
    public int getLoadedSkillsCount() {
        return loadedSkills.size();
    }

    /**
     * 获取当前快照版本
     */
    public long getCurrentSnapshotVersion() {
        return snapshotService.getSnapshotVersion();
    }

    /**
     * 从目录加载技能
     */
    private void loadSkillFromDirectory(Path skillDir) {
        Path skillMdPath = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMdPath)) {
            return;
        }

        try {
            String content = Files.readString(skillMdPath);
            SkillManifest manifest = parseSkillManifest(content, skillDir.toString());

            if (manifest != null && manifest.name != null && !manifest.name.isBlank()) {
                log.info("加载技能：{} ({})", manifest.name, manifest.description);
                loadedSkills.put(manifest.name, manifest);

                // 注册到技能注册表
                registerSkillTools(manifest);
            }
        } catch (IOException e) {
            log.error("加载技能失败：{} - {}", skillDir, e.getMessage());
        }
    }

    /**
     * 解析技能清单
     */
    private SkillManifest parseSkillManifest(String content, String directory) throws IOException {
        // 解析 YAML frontmatter
        int frontmatterEnd = content.indexOf("---", 3);
        if (frontmatterEnd == -1) {
            return null;
        }

        String frontmatter = content.substring(3, frontmatterEnd).trim();
        SkillManifest manifest = YAML_MAPPER.readValue(frontmatter, SkillManifest.class);
        manifest.directory = directory;

        // 解析 metadata.openclaw 字段（可能是 YAML 对象或单行 JSON）
        parseOpenClawMetadata(frontmatter, manifest);

        return manifest;
    }

    /**
     * 解析 openclaw 元数据
     */
    private void parseOpenClawMetadata(String content, SkillManifest manifest) throws IOException {
        if (manifest == null) return;
        if (content == null || content.isBlank()) return;

        JsonNode root = YAML_MAPPER.readTree(content);
        if (root == null || root.isNull()) return;

        JsonNode meta = root.get("metadata");
        if (meta == null || meta.isNull()) {
            return;
        }

        // 支持两种形态：
        // 1) metadata: { openclaw: { ... } }
        // 2) metadata: { ... }  (部分现有技能用平铺字段)
        JsonNode openclaw = meta.get("openclaw");
        JsonNode source = (openclaw != null && !openclaw.isNull()) ? openclaw : meta;

        // metadata.openclaw 也可能是单行 JSON 字符串
        if (source.isTextual()) {
            String raw = source.asText().trim();
            if (raw.startsWith("{") && raw.endsWith("}")) {
                try {
                    source = JSON_MAPPER.readTree(raw);
                } catch (Exception ignored) {
                    // ignore invalid json
                }
            }
        }

        if (source == null || source.isNull() || !source.isObject()) {
            return;
        }

        SkillManifest.OpenClawMetadata out = manifest.metadata != null
                ? manifest.metadata
                : new SkillManifest.OpenClawMetadata();

        // always/os/emoji/homepage
        if (source.hasNonNull("always")) {
            out.always = source.get("always").asBoolean(false);
        }
        if (source.hasNonNull("emoji")) {
            out.emoji = source.get("emoji").asText(null);
        }
        if (source.hasNonNull("homepage")) {
            out.homepage = source.get("homepage").asText(null);
        }
        if (source.hasNonNull("os") && source.get("os").isArray()) {
            List<String> os = new ArrayList<>();
            for (JsonNode n : source.get("os")) {
                if (n != null && !n.isNull()) os.add(n.asText());
            }
            out.os = os;
        }

        // requires.*
        JsonNode requires = source.get("requires");
        if (requires != null && requires.isObject()) {
            if (out.requires == null) out.requires = new SkillManifest.OpenClawMetadata.Requires();
            out.requires.bins = readStringArray(requires.get("bins"));
            out.requires.env = readStringArray(requires.get("env"));
            out.requires.config = readStringArray(requires.get("config"));
        }

        // install（保持最小映射，当前仅用于 UI 展示/未来扩展）
        JsonNode install = source.get("install");
        if (install != null && install.isArray()) {
            try {
                out.install = JSON_MAPPER.convertValue(
                        install,
                        new TypeReference<List<SkillManifest.OpenClawMetadata.Installer>>() {});
            } catch (Exception ignored) {
                // ignore
            }
        }

        manifest.metadata = out;
    }

    private static List<String> readStringArray(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isArray()) return null;
        List<String> out = new ArrayList<>();
        for (JsonNode n : node) {
            if (n == null || n.isNull()) continue;
            String s = n.asText();
            if (s != null && !s.isBlank()) out.add(s);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * 注册技能工具
     *
     * 从 SKILL.md 解析输入模式，注册通用执行器
     */
    private void registerSkillTools(SkillManifest manifest) {
        String toolName = "skill_" + manifest.name;

        // 为每个技能创建特定的输入模式
        String inputSchema = buildInputSchema(manifest);

        // 创建执行函数，使用通用执行器
        java.util.function.Function<String, String> executor = createExecutorFunction(manifest);

        ClaudeLikeTool tool = ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        toolName,
                        ToolCategory.EXTERNAL,
                        manifest.description,
                        inputSchema,
                        null,
                        false));

        // 注册工具到技能注册表
        skillRegistry.registerSkill(new SkillRegistry.Skill() {
            @Override
            public String getName() { return manifest.name; }
            @Override
            public String getDescription() { return manifest.description; }
            @Override
            public List<ClaudeLikeTool> getTools() { return List.of(tool); }
            @Override
            public boolean isEnabled() { return true; }
            @Override
            public void setEnabled(boolean enabled) {}
        });

        // 注册工具执行器到全局注册表
        toolRegistry.put(toolName, executor);
        log.info("注册工具执行器：{} -> {}", toolName, genericSkillExecutor.detectSkillType(manifest.directory));
    }

    /**
     * 构建输入模式
     *
     * 根据技能类型和 SKILL.md 中的用法说明构建合适的 JSON Schema
     */
    private String buildInputSchema(SkillManifest manifest) {
        String name = manifest.name.toLowerCase();

        // 根据技能名称推断输入模式
        return switch (name) {
            // 计算器技能 - 需要 expression 参数
            case "calculator" -> "{" +
                    "  \"type\": \"object\"," +
                    "  \"properties\": {" +
                    "    \"expression\": {\"type\": \"string\", \"description\": \"数学表达式，如 2 + 3 * 4\"}" +
                    "  }," +
                    "  \"required\": [\"expression\"]" +
                    "}";

            // 文件整理技能 - 需要 directory 参数
            case "file-organizer-zh" -> "{" +
                    "  \"type\": \"object\"," +
                    "  \"properties\": {" +
                    "    \"action\": {\"type\": \"string\", \"description\": \"操作类型：organize, scan, classify\", \"enum\": [\"organize\", \"scan\", \"classify\"]}," +
                    "    \"directory\": {\"type\": \"string\", \"description\": \"要整理的目录路径\"}," +
                    "    \"dryRun\": {\"type\": \"boolean\", \"description\": \"是否仅为预览，不实际移动文件\"}" +
                    "  }," +
                    "  \"required\": [\"action\", \"directory\"]" +
                    "}";

            // 本地文件读取 - 需要 path 参数
            case "local-file" -> "{" +
                    "  \"type\": \"object\"," +
                    "  \"properties\": {" +
                    "    \"action\": {\"type\": \"string\", \"description\": \"操作类型：read, summarize, search\", \"enum\": [\"read\", \"summarize\", \"search\"]}," +
                    "    \"path\": {\"type\": \"string\", \"description\": \"文件路径\"}," +
                    "    \"keyword\": {\"type\": \"string\", \"description\": \"搜索关键词（search 操作需要）\"}" +
                    "  }," +
                    "  \"required\": [\"action\", \"path\"]" +
                    "}";

            // JSON 和 Markdown 是指令型技能，不需要执行脚本
            case "json" -> "{" +
                    "  \"type\": \"object\"," +
                    "  \"properties\": {" +
                    "    \"action\": {\"type\": \"string\", \"description\": \"操作类型：validate, format, parse\", \"enum\": [\"validate\", \"format\", \"parse\"]}," +
                    "    \"input\": {\"type\": \"string\", \"description\": \"JSON 字符串\"}," +
                    "    \"schema\": {\"type\": \"string\", \"description\": \"JSON Schema（可选）\"}" +
                    "  }," +
                    "  \"required\": [\"action\", \"input\"]" +
                    "}";

            case "markdown" -> "{" +
                    "  \"type\": \"object\"," +
                    "  \"properties\": {" +
                    "    \"action\": {\"type\": \"string\", \"description\": \"操作类型：validate, check, fix\", \"enum\": [\"validate\", \"check\", \"fix\"]}," +
                    "    \"content\": {\"type\": \"string\", \"description\": \"Markdown 内容\"}" +
                    "  }," +
                    "  \"required\": [\"action\", \"content\"]" +
                    "}";

            // 默认通用模式
            default -> "{" +
                    "  \"type\": \"object\"," +
                    "  \"properties\": {" +
                    "    \"command\": {\"type\": \"string\", \"description\": \"Command to execute\"}," +
                    "    \"args\": {\"type\": \"object\", \"description\": \"Command arguments\"}" +
                    "  }," +
                    "  \"required\": [\"command\"]" +
                    "}";
        };
    }

    /**
     * 创建执行函数
     */
    private java.util.function.Function<String, String> createExecutorFunction(SkillManifest manifest) {
        String skillName = manifest.name.toLowerCase();
        String skillDirectory = manifest.directory;

        return (String inputJson) -> {
            log.info("=== 技能工具被调用 ===");
            log.info("技能名称：{}", skillName);
            log.info("技能目录：{}", skillDirectory);
            log.info("输入 JSON: {}", inputJson);

            try {
                // 解析 JSON 输入
                Map<String, Object> args = JSON_MAPPER.readValue(
                    inputJson,
                    new TypeReference<Map<String, Object>>() {}
                );
                log.info("解析后的参数：{}", args);

                // 对于 JSON 和 Markdown 这种指令型技能，使用 Java 实现
                if ("json".equals(skillName) || "markdown".equals(skillName)) {
                    log.info("使用内置 Java 执行器执行技能：{}", skillName);
                    String result = executeBuiltInSkill(skillName, args);
                    log.info("技能执行结果：{}", result);
                    return result;
                }

                // 其他技能使用通用执行器调用脚本
                log.info("使用 GenericSkillExecutor 执行技能：{}", skillName);
                String result = genericSkillExecutor.execute(skillDirectory, args);

                // 返回结果（如果是纯文本，包装成 JSON）
                if (!result.trim().startsWith("{")) {
                    result = "{\"result\": " + JSON_MAPPER.writeValueAsString(result) + "}";
                }
                log.info("技能执行结果：{}", result);
                return result;

            } catch (Exception e) {
                log.error("执行技能失败：{}", skillName, e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * 执行内置技能（用于指令型技能，如 JSON、Markdown）
     */
    private String executeBuiltInSkill(String skillName, Map<String, Object> args) {
        return switch (skillName) {
            case "json" -> {
                JsonSkillExecutor executor = new JsonSkillExecutor();
                yield executor.execute(args);
            }
            case "markdown" -> {
                MarkdownSkillExecutor executor = new MarkdownSkillExecutor();
                yield executor.execute(args);
            }
            default -> "错误：未知的内置技能：" + skillName;
        };
    }

    // 存储工具执行器的注册表
    private static final Map<String, java.util.function.Function<String, String>> toolRegistry = new ConcurrentHashMap<>();

    /**
     * 获取工具执行器（供 AgentConfiguration 使用）
     */
    public static java.util.function.Function<String, String> getToolExecutor(String toolName) {
        return toolRegistry.get(toolName);
    }

    /**
     * 获取所有技能
     */
    public List<SkillManifest> getAllSkills() {
        return new ArrayList<>(loadedSkills.values());
    }

    /**
     * 获取技能详情
     */
    public SkillManifest getSkill(String name) {
        return loadedSkills.get(name);
    }

    /**
     * 搜索技能
     */
    public List<SkillManifest> searchSkills(String query) {
        if (query == null || query.isBlank()) {
            return getAllSkills();
        }

        String lowerQuery = query.toLowerCase();
        return loadedSkills.values().stream()
                .filter(skill -> skill.name.toLowerCase().contains(lowerQuery) ||
                                 skill.description.toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    /**
     * 安装技能（从 ClawHub）
     */
    public boolean installSkill(String skillId, String version, String workDir) {
        log.info("安装技能：{} (version: {})", skillId, version);
        // TODO: 实现从 ClawHub 下载和安装
        return true;
    }

    /**
     * 卸载技能
     */
    public boolean uninstallSkill(String skillId) {
        SkillManifest removed = loadedSkills.remove(skillId);
        if (removed != null) {
            log.info("卸载技能：{}", skillId);
            // TODO: 删除技能目录
            return true;
        }
        return false;
    }
}
