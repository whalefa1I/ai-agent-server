package demo.k8s.agent.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClawHub API 客户端
 * ClawHub 是 OpenClaw 的技能注册表
 */
@Service
@ConditionalOnProperty(prefix = "demo.skills.clawhub", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClawhubClient {

    private static final Logger log = LoggerFactory.getLogger(ClawhubClient.class);

    private static final String DEFAULT_BASE_URL = "https://clawhub.com";

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;

    public ClawhubClient(ClawhubProperties properties) {
        this.baseUrl = properties.getBaseUrl() != null ? properties.getBaseUrl() : DEFAULT_BASE_URL;
        this.apiKey = properties.getApiKey();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(this.baseUrl);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        this.webClient = builder.build();
        log.info("ClawHub 客户端已初始化：{}", this.baseUrl);
    }

    /**
     * ClawHub 技能响应
     */
    public record ClawhubSkill(
            String id,
            String slug,
            String name,
            String description,
            String version,
            String author,
            long downloads,
            long stars,
            String homepage,
            Map<String, Object> metadata
    ) {}

    /**
     * 搜索技能
     */
    public List<ClawhubSkill> search(String query) {
        log.info("搜索 ClawHub 技能：{}", query);

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/skills/search")
                            .queryParam("q", query)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            List<ClawhubSkill> skills = new ArrayList<>();
            JsonNode skillsNode = root.has("skills") ? root.get("skills") : root;

            if (skillsNode.isArray()) {
                for (JsonNode node : skillsNode) {
                    skills.add(parseSkill(node));
                }
            }

            return skills;

        } catch (Exception e) {
            log.error("搜索 ClawHub 失败：{}", e.getMessage());
            // 返回示例数据用于演示
            return getDemoSkills(query);
        }
    }

    /**
     * 获取技能详情
     */
    public ClawhubSkill getSkill(String skillId) {
        log.info("获取 ClawHub 技能详情：{}", skillId);

        try {
            String response = webClient.get()
                    .uri("/api/skills/" + skillId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response);
            return parseSkill(node);

        } catch (Exception e) {
            log.error("获取技能详情失败：{} - {}", skillId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取技能版本列表
     */
    public List<String> getVersions(String skillId) {
        log.info("获取技能版本：{}", skillId);

        try {
            String response = webClient.get()
                    .uri("/api/skills/" + skillId + "/versions")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            List<String> versions = new ArrayList<>();
            JsonNode versionsNode = root.has("versions") ? root.get("versions") : root;

            if (versionsNode.isArray()) {
                for (JsonNode node : versionsNode) {
                    if (node.isTextual()) {
                        versions.add(node.asText());
                    } else if (node.has("version")) {
                        versions.add(node.get("version").asText());
                    }
                }
            }

            return versions;

        } catch (Exception e) {
            log.error("获取技能版本失败：{} - {}", skillId, e.getMessage());
            return List.of("1.0.0", "0.9.0", "0.8.0");
        }
    }

    /**
     * 下载技能包
     */
    public byte[] downloadSkill(String skillId, String version) {
        log.info("下载技能包：{} (v{})", skillId, version);

        try {
            return webClient.get()
                    .uri("/api/skills/" + skillId + "/download?version=" + version)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

        } catch (Exception e) {
            log.error("下载技能包失败：{} - {}", skillId, e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 发布技能
     */
    public boolean publishSkill(String skillPath, String slug, String name, String version, String changelog) {
        log.info("发布技能：{} (v{})", slug, version);

        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("slug", slug);
            payload.put("name", name);
            payload.put("version", version);
            payload.put("changelog", changelog);

            String response = webClient.post()
                    .uri("/api/skills/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response != null && (response.contains("success") || response.contains("published"));

        } catch (Exception e) {
            log.error("发布技能失败：{} - {}", slug, e.getMessage());
            return false;
        }
    }

    private ClawhubSkill parseSkill(JsonNode node) {
        String id = node.has("id") ? node.get("id").asText() : node.has("slug") ? node.get("slug").asText() : "";
        String slug = node.has("slug") ? node.get("slug").asText() : id;
        String name = node.has("name") ? node.get("name").asText() : slug;
        String description = node.has("description") ? node.get("description").asText() : "";
        String version = node.has("version") ? node.get("version").asText() : "1.0.0";
        String author = node.has("author") ? node.get("author").asText() : "unknown";
        long downloads = node.has("downloads") ? node.get("downloads").asLong() : 0;
        long stars = node.has("stars") ? node.get("stars").asLong() : 0;
        String homepage = node.has("homepage") ? node.get("homepage").asText() : null;

        Map<String, Object> metadata = new HashMap<>();
        if (node.has("metadata")) {
            metadata = convertToMap(node.get("metadata"));
        }

        return new ClawhubSkill(id, slug, name, description, version, author, downloads, stars, homepage, metadata);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(JsonNode node) {
        return new ObjectMapper().convertValue(node, Map.class);
    }

    /**
     * 获取示例技能（用于演示）
     */
    private List<ClawhubSkill> getDemoSkills(String query) {
        List<ClawhubSkill> demoSkills = List.of(
                new ClawhubSkill("calc", "calc", "Calculator", "简单的计算器技能", "1.0.0", "openclaw", 1000, 50, null, Map.of()),
                new ClawhubSkill("weather", "weather", "Weather", "天气预报技能", "2.1.0", "openclaw", 2500, 120, "https://weather.example.com", Map.of()),
                new ClawhubSkill("github", "github", "GitHub", "GitHub 集成技能", "1.5.0", "openclaw", 5000, 300, "https://github.com", Map.of()),
                new ClawhubSkill("discord", "discord", "Discord", "Discord 机器人技能", "3.0.0", "community", 1500, 80, "https://discord.com", Map.of()),
                new ClawhubSkill("slack", "slack", "Slack", "Slack 集成技能", "2.0.0", "community", 3000, 150, "https://slack.com", Map.of()),
                new ClawhubSkill("notion", "notion", "Notion", "Notion 笔记技能", "1.2.0", "community", 800, 45, "https://notion.so", Map.of()),
                new ClawhubSkill("obsidian", "obsidian", "Obsidian", "Obsidian 知识库技能", "1.0.5", "community", 600, 35, "https://obsidian.md", Map.of()),
                new ClawhubSkill("translate", "translate", "Translate", "多语言翻译技能", "2.3.0", "openclaw", 4000, 200, null, Map.of())
        );

        if (query == null || query.isBlank()) {
            return demoSkills;
        }

        String lowerQuery = query.toLowerCase();
        return demoSkills.stream()
                .filter(s -> s.name().toLowerCase().contains(lowerQuery) ||
                             s.description().toLowerCase().contains(lowerQuery))
                .toList();
    }

    /**
     * 配置属性类
     */
    public static class ClawhubProperties {
        private String baseUrl;
        private String apiKey;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
