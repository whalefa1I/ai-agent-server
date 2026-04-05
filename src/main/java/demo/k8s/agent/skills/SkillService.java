package demo.k8s.agent.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
    private final SkillExecutorRegistry skillExecutorRegistry;
    private final GenericSkillExecutor genericSkillExecutor;

    // 已加载的技能清单
    private final Map<String, SkillManifest> loadedSkills = new ConcurrentHashMap<>();

    public SkillService(
            SkillRegistry skillRegistry,
            SkillExecutorRegistry skillExecutorRegistry,
            GenericSkillExecutor genericSkillExecutor) {
        this.skillRegistry = skillRegistry;
        this.skillExecutorRegistry = skillExecutorRegistry;
        this.genericSkillExecutor = genericSkillExecutor;
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

        log.info("加载了 {} 个技能", loadedSkills.size());
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

        // 解析 metadata 中的 openclaw 字段（可能是单行 JSON）
        if (manifest.metadata != null && content.contains("\"openclaw\":")) {
            parseOpenClawMetadata(content, manifest);
        }

        return manifest;
    }

    /**
     * 解析 openclaw 元数据
     */
    private void parseOpenClawMetadata(String content, SkillManifest manifest) throws IOException {
        // 简化实现，从 YAML 中直接读取 metadata
        // 完整实现需要解析单行 JSON
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
            try {
                // 解析 JSON 输入
                Map<String, Object> args = JSON_MAPPER.readValue(
                    inputJson,
                    new TypeReference<Map<String, Object>>() {}
                );

                // 对于 JSON 和 Markdown 这种指令型技能，使用 Java 实现
                if ("json".equals(skillName) || "markdown".equals(skillName)) {
                    return executeBuiltInSkill(skillName, args);
                }

                // 其他技能使用通用执行器调用脚本
                String result = genericSkillExecutor.execute(skillDirectory, args);

                // 返回结果（如果是纯文本，包装成 JSON）
                if (!result.trim().startsWith("{")) {
                    result = "{\"result\": " + JSON_MAPPER.writeValueAsString(result) + "}";
                }
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
