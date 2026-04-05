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

    // 已加载的技能清单
    private final Map<String, SkillManifest> loadedSkills = new ConcurrentHashMap<>();

    public SkillService(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
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
     */
    private void registerSkillTools(SkillManifest manifest) {
        // 为每个技能创建一个通用的 execute 工具
        String inputSchema = "{" +
                "  \"type\": \"object\"," +
                "  \"properties\": {" +
                "    \"command\": {\"type\": \"string\", \"description\": \"Command to execute\"}," +
                "    \"args\": {\"type\": \"object\", \"description\": \"Command arguments\"}" +
                "  }," +
                "  \"required\": [\"command\"]" +
                "}";

        ClaudeLikeTool tool = ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "skill_" + manifest.name,
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
