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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Web 抓取工具 - 获取网页内容并提取纯文本。
 * <p>
 * 功能：
 * - HTTP/HTTPS 网页抓取
 * - HTML 清理和文本提取
 * - 超时保护
 * - 内容长度限制
 */
public class WebFetchTool {

    private static final Logger log = LoggerFactory.getLogger(WebFetchTool.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_CONTENT_SIZE = 500 * 1024; // 500KB
    private static final int MAX_OUTPUT_LENGTH = 50000; // 最大输出字符数

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("(?s)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern MULTIPLE_WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"url\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"URL of the webpage to fetch\"" +
            "    }," +
            "    \"timeout\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Timeout in seconds (default: 30)\"" +
            "    }," +
            "    \"stripHtml\": {" +
            "      \"type\": \"boolean\"," +
            "      \"description\": \"Strip HTML tags and return plain text (default: true)\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"url\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "web_fetch",
                        ToolCategory.EXTERNAL,
                        "Fetch webpage content and extract text. Supports HTTP/HTTPS URLs. Automatically strips HTML tags, scripts, and styles.",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        String urlStr = (String) input.get("url");
        int timeout = getInt(input, "timeout", DEFAULT_TIMEOUT_SECONDS);
        boolean stripHtml = getBoolean(input, "stripHtml", true);

        if (urlStr == null || urlStr.isEmpty()) {
            return LocalToolResult.error("url is required");
        }

        // URL 验证
        URI url;
        try {
            url = URI.create(urlStr);
        } catch (Exception e) {
            return LocalToolResult.error("Invalid URL: " + e.getMessage());
        }

        // 协议检查
        String scheme = url.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return LocalToolResult.error("Only HTTP and HTTPS URLs are supported");
        }

        // 安全检查：防止内网请求
        String host = url.getHost();
        if (isInternalHost(host)) {
            return LocalToolResult.error("Access to internal/private hosts is not allowed: " + host);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url)
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("User-Agent", "Mozilla/5.0 (compatible; AgentBot/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 检查响应大小
            if (response.body().length() > MAX_CONTENT_SIZE) {
                return LocalToolResult.error("Response too large: " + response.body().length() +
                        " bytes (max: " + MAX_CONTENT_SIZE + " bytes)");
            }

            // 检查 HTTP 状态码
            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                return LocalToolResult.error("HTTP " + statusCode + ": " +
                        getStatusCodeMessage(statusCode));
            }

            String content = response.body();

            // 处理内容
            String result;
            if (stripHtml) {
                result = extractPlainText(content);
            } else {
                result = content;
            }

            // 截断过长的内容
            if (result.length() > MAX_OUTPUT_LENGTH) {
                result = result.substring(0, MAX_OUTPUT_LENGTH) +
                        "\n\n[Content truncated at " + MAX_OUTPUT_LENGTH + " characters]";
            }

            StringBuilder output = new StringBuilder();
            output.append("URL: ").append(urlStr).append("\n");
            output.append("Status: ").append(statusCode).append("\n");
            output.append("Content-Type: ").append(
                    response.headers().firstValue("content-type").orElse("unknown")).append("\n");
            if (stripHtml) {
                output.append("Format: Plain text (HTML stripped)\n");
            }
            output.append("Length: ").append(result.length()).append(" characters\n\n");
            output.append("--- Content ---\n");
            output.append(result);

            return LocalToolResult.builder()
                    .success(true)
                    .content(output.toString())
                    .executionLocation("local")
                    .metadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                            Map.of(
                                    "url", urlStr,
                                    "statusCode", statusCode,
                                    "contentLength", result.length(),
                                    "stripped", stripHtml)))
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Web fetch interrupted", e);
            return LocalToolResult.error("Request interrupted");
        } catch (IOException e) {
            log.error("Web fetch IO error", e);
            return LocalToolResult.error("Network error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Web fetch error", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 提取纯文本（移除 HTML 标签）
     */
    private static String extractPlainText(String html) {
        if (html == null) return "";

        // 移除 script 和 style 标签及其内容
        String cleaned = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll("");

        // 移除所有 HTML 标签
        cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll(" ");

        // 解码 HTML 实体
        cleaned = decodeHtmlEntities(cleaned);

        // 压缩空白
        cleaned = MULTIPLE_WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ");

        return cleaned.trim();
    }

    /**
     * 解码常见 HTML 实体
     */
    private static String decodeHtmlEntities(String text) {
        if (text == null) return "";

        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&ndash;", "-")
                .replace("&mdash;", "-")
                .replace("&lsquo;", "'")
                .replace("&rsquo;", "'")
                .replace("&ldquo;", "\"")
                .replace("&rdquo;", "\"")
                .replace("&hellip;", "...")
                .replace("&copy;", "(c)")
                .replace("&reg;", "(R)")
                .replace("&trade;", "(TM)");
    }

    /**
     * 检查是否是内网主机
     */
    private static boolean isInternalHost(String host) {
        if (host == null || host.isEmpty()) return true;

        // 检查 localhost
        if (host.equalsIgnoreCase("localhost") ||
            host.equals("127.0.0.1") ||
            host.equals("::1")) {
            return true;
        }

        // 检查私有 IP 范围
        if (host.matches("^10\\..*")) return true;
        if (host.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*")) return true;
        if (host.matches("^192\\.168\\..*")) return true;
        if (host.startsWith("169.254.")) return true; // Link-local

        // 检查内网域名
        if (host.endsWith(".local") || host.endsWith(".internal")) return true;

        return false;
    }

    /**
     * 获取 HTTP 状态码描述
     */
    private static String getStatusCodeMessage(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 408 -> "Request Timeout";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Error";
        };
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
