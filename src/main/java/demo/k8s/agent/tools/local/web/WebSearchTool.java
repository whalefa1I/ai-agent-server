package demo.k8s.agent.tools.local.web;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Web 搜索工具 - 调用搜索引擎 API 获取搜索结果。
 * <p>
 * 支持：
 * - 自定义搜索引擎 API
 * - 结果数量控制
 * - 安全搜索过滤
 * <p>
 * 注意：需要配置搜索引擎 API Key（如 Google Custom Search、Bing Search 等）
 */
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    private static final int DEFAULT_RESULTS_COUNT = 10;
    private static final int MAX_RESULTS_COUNT = 20;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 搜索引擎配置（可通过环境变量覆盖）
    private static final String SEARCH_ENGINE = System.getenv("WEB_SEARCH_ENGINE"); // "google", "bing", "duckduckgo"
    private static final String SEARCH_API_KEY = System.getenv("WEB_SEARCH_API_KEY");
    private static final String SEARCH_ENGINE_ID = System.getenv("WEB_SEARCH_ENGINE_ID"); // Google Custom Search Engine ID
    private static final String SEARCH_ENDPOINT = System.getenv("WEB_SEARCH_ENDPOINT");

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"query\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Search query\"" +
            "    }," +
            "    \"numResults\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Number of results to return (default: 10, max: 20)\"" +
            "    }," +
            "    \"safeSearch\": {" +
            "      \"type\": \"boolean\"," +
            "      \"description\": \"Enable safe search filtering (default: true)\"" +
            "    }," +
            "    \"site\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Restrict search to specific site/domain\"" +
            "    }," +
            "    \"fileType\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Restrict search to specific file type (e.g., 'pdf', 'doc')\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"query\"]" +
            "}";

    /**
     * 搜索结果项
     */
    public static class SearchResult {
        public String title;
        public String url;
        public String snippet;
        public String displayLink;
        public String formattedUrl;

        public SearchResult(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "web_search",
                        ToolCategory.EXTERNAL,
                        "Search the web using configured search engine API. Requires WEB_SEARCH_API_KEY environment variable. Supports Google Custom Search, Bing Search, etc.",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String query = (String) input.get("query");
        int numResults = getInt(input, "numResults", DEFAULT_RESULTS_COUNT);
        boolean safeSearch = getBoolean(input, "safeSearch", true);
        String site = (String) input.get("site");
        String fileType = (String) input.get("fileType");

        if (query == null || query.isEmpty()) {
            return LocalToolResult.error("query is required");
        }

        // 限制结果数量
        numResults = Math.min(numResults, MAX_RESULTS_COUNT);

        // 构建搜索查询
        String fullQuery = buildQuery(query, site, fileType);

        try {
            List<SearchResult> results;

            // 根据配置的搜索引擎调用相应 API
            if ("google".equalsIgnoreCase(SEARCH_ENGINE) || SEARCH_ENDPOINT != null) {
                results = searchWithGoogle(fullQuery, numResults, safeSearch);
            } else if ("bing".equalsIgnoreCase(SEARCH_ENGINE)) {
                results = searchWithBing(fullQuery, numResults, safeSearch);
            } else {
                // 默认：如果没有配置 API，返回模拟结果（用于演示）
                results = searchWithMock(fullQuery, numResults);
            }

            // 构建输出
            StringBuilder output = new StringBuilder();
            output.append("Web Search Results\n");
            output.append("==================\n\n");
            output.append("Query: ").append(query).append("\n");
            if (site != null) {
                output.append("Site: ").append(site).append("\n");
            }
            if (fileType != null) {
                output.append("File Type: ").append(fileType).append("\n");
            }
            output.append("Results: ").append(results.size()).append("\n\n");

            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                output.append(i + 1).append(". ").append(result.title).append("\n");
                output.append("   URL: ").append(result.url).append("\n");
                if (result.displayLink != null) {
                    output.append("   Site: ").append(result.displayLink).append("\n");
                }
                output.append("   ").append(result.snippet).append("\n\n");
            }

            if (results.isEmpty()) {
                output.append("\nNo results found.\n");
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(output.toString())
                    .executionLocation("local")
                    .metadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                            Map.of(
                                    "query", query,
                                    "resultCount", results.size(),
                                    "results", results.stream()
                                            .map(r -> Map.of(
                                                    "title", r.title,
                                                    "url", r.url,
                                                    "snippet", r.snippet))
                                            .toList())))
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Web search interrupted", e);
            return LocalToolResult.error("Request interrupted");
        } catch (IOException e) {
            log.error("Web search IO error", e);
            return LocalToolResult.error("Network error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Web search error", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 构建搜索查询
     */
    private static String buildQuery(String query, String site, String fileType) {
        StringBuilder fullQuery = new StringBuilder(query);

        if (site != null && !site.isEmpty()) {
            fullQuery.append(" site:").append(site);
        }

        if (fileType != null && !fileType.isEmpty()) {
            fullQuery.append(" filetype:").append(fileType);
        }

        return fullQuery.toString();
    }

    /**
     * Google Custom Search API
     */
    private static List<SearchResult> searchWithGoogle(String query, int numResults, boolean safeSearch)
            throws IOException, InterruptedException {

        if (SEARCH_API_KEY == null || SEARCH_ENGINE_ID == null) {
            log.warn("Google Search API not configured (missing API_KEY or ENGINE_ID)");
            return new ArrayList<>();
        }

        String url = String.format(
                "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s&num=%d&safe=%s",
                SEARCH_API_KEY,
                SEARCH_ENGINE_ID,
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8),
                Math.min(numResults, 10),
                safeSearch ? "active" : "off");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Google Search API error: {}", response.statusCode());
            return new ArrayList<>();
        }

        // 解析 JSON 响应
        com.fasterxml.jackson.databind.JsonNode json =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());

        List<SearchResult> results = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode items = json.path("items");
        if (items.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : items) {
                results.add(new SearchResult(
                        item.path("title").asText(),
                        item.path("link").asText(),
                        item.path("snippet").asText()
                ));
            }
        }

        return results;
    }

    /**
     * Bing Search API
     */
    private static List<SearchResult> searchWithBing(String query, int numResults, boolean safeSearch)
            throws IOException, InterruptedException {

        if (SEARCH_API_KEY == null) {
            log.warn("Bing Search API not configured (missing API_KEY)");
            return new ArrayList<>();
        }

        String url = String.format(
                "https://api.bing.microsoft.com/v7.0/search?q=%s&count=%d&safeSearch=%s",
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8),
                numResults,
                safeSearch ? "Strict" : "Off");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .header("Ocp-Apim-Subscription-Key", SEARCH_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Bing Search API error: {}", response.statusCode());
            return new ArrayList<>();
        }

        // 解析 JSON 响应
        com.fasterxml.jackson.databind.JsonNode json =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());

        List<SearchResult> results = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode webPages = json.path("webPages");
        com.fasterxml.jackson.databind.JsonNode items = webPages.path("value");
        if (items.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : items) {
                SearchResult result = new SearchResult(
                        item.path("name").asText(),
                        item.path("url").asText(),
                        item.path("snippet").asText()
                );
                result.displayLink = item.path("displayUrl").asText();
                result.formattedUrl = item.path("formattedUrl").asText();
                results.add(result);
            }
        }

        return results;
    }

    /**
     * 模拟搜索（用于演示/测试）
     */
    private static List<SearchResult> searchWithMock(String query, int numResults) {
        List<SearchResult> results = new ArrayList<>();

        // 返回演示结果
        results.add(new SearchResult(
                "Search Result 1 for: " + query,
                "https://example.com/result1",
                "This is a mock search result. Configure WEB_SEARCH_API_KEY to use real search."
        ));

        if (numResults > 1) {
            results.add(new SearchResult(
                    "Search Result 2 for: " + query,
                    "https://example.com/result2",
                    "Set environment variables to enable real web search capabilities."
            ));
        }

        if (numResults > 2) {
            results.add(new SearchResult(
                    "Search Result 3 for: " + query,
                    "https://example.com/result3",
                    "Supported engines: Google Custom Search, Bing Search API."
            ));
        }

        return results;
    }

    /**
     * 获取整数参数
     */
    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 获取布尔参数
     */
    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return defaultValue;
    }
}
