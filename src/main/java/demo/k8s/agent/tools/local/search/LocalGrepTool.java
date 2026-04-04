package demo.k8s.agent.tools.local.search;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * 本地正则表达式搜索工具 - 在文件内容中搜索匹配项。
 */
public class LocalGrepTool {

    private static final Logger log = LoggerFactory.getLogger(LocalGrepTool.class);

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_RESULTS = 1000;

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"pattern\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Regular expression pattern to search for\"" +
            "    }," +
            "    \"path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Directory to search in (defaults to current directory)\"" +
            "    }," +
            "    \"include\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Glob pattern to include files (e.g., **/*.java)\"" +
            "    }," +
            "    \"exclude\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Glob pattern to exclude files\"" +
            "    }," +
            "    \"contextLines\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Number of context lines to show (default: 2)\"" +
            "    }," +
            "    \"caseSensitive\": {" +
            "      \"type\": \"boolean\"," +
            "      \"description\": \"Case sensitive search (default: false)\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"pattern\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "grep",
                        ToolCategory.FILE_SYSTEM,
                        "Search file contents using regular expressions",
                        INPUT_SCHEMA,
                        null,
                        true));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();

        try {
            String patternStr = (String) input.get("pattern");
            String basePath = (String) input.getOrDefault("path", System.getProperty("user.dir"));
            String includePattern = (String) input.get("include");
            String excludePattern = (String) input.get("exclude");
            int contextLines = getInt(input, "contextLines", 2);
            boolean caseSensitive = getBoolean(input, "caseSensitive", false);

            if (patternStr == null || patternStr.isEmpty()) {
                return LocalToolResult.error("pattern is required");
            }

            // 编译正则
            Pattern pattern;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = Pattern.compile(patternStr, flags);
            } catch (Exception e) {
                return LocalToolResult.error("Invalid regex pattern: " + e.getMessage());
            }

            Path baseDir = Paths.get(basePath);
            if (!Files.exists(baseDir)) {
                return LocalToolResult.error("Directory does not exist: " + basePath);
            }

            // 执行搜索
            List<MatchResult> results = new ArrayList<>();

            try (Stream<Path> paths = Files.walk(baseDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            // 跳过隐藏文件和目录
                            if (fileName.startsWith(".")) return false;
                            // 应用 include/exclude 过滤
                            if (includePattern != null && !matchesGlob(path.toString(), includePattern, baseDir)) {
                                return false;
                            }
                            if (excludePattern != null && matchesGlob(path.toString(), excludePattern, baseDir)) {
                                return false;
                            }
                            return true;
                        })
                        .forEach(path -> {
                            if (results.size() >= MAX_RESULTS) return;

                            searchInFile(path, pattern, contextLines, results, baseDir);
                        });
            }

            // 生成结果
            StringBuilder result = new StringBuilder();
            result.append("Search pattern: ").append(patternStr).append("\n");
            result.append("Directory: ").append(basePath).append("\n");
            result.append("Found ").append(results.size()).append(" match(s)\n\n");

            for (MatchResult match : results) {
                result.append("File: ").append(match.filePath).append("\n");
                result.append("  Line ").append(match.lineNumber).append(": ").append(match.lineContent).append("\n");

                // 显示上下文
                if (contextLines > 0) {
                    if (!match.beforeLines.isEmpty()) {
                        for (String line : match.beforeLines) {
                            result.append("    ").append(line).append("\n");
                        }
                    }
                    if (!match.afterLines.isEmpty()) {
                        for (String line : match.afterLines) {
                            result.append("    ").append(line).append("\n");
                        }
                    }
                }
                result.append("\n");
            }

            if (results.size() >= MAX_RESULTS) {
                result.append("\n(Note: Results limited to ").append(MAX_RESULTS).append(" matches)\n");
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(result.toString())
                    .executionLocation("local")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Grep execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 在文件中搜索
     */
    private static void searchInFile(Path path, Pattern pattern, int contextLines,
                                      List<MatchResult> results, Path baseDir) {
        try {
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return; // 跳过太大的文件
            }

            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // 找到匹配
                    MatchResult result = new MatchResult();
                    result.filePath = baseDir.relativize(path).toString();
                    result.lineNumber = i + 1;
                    result.lineContent = line.trim();

                    // 获取上下文
                    int beforeStart = Math.max(0, i - contextLines);
                    int afterEnd = Math.min(lines.size(), i + contextLines + 1);

                    result.beforeLines = lines.subList(beforeStart, i);
                    result.afterLines = lines.subList(i + 1, afterEnd);

                    results.add(result);

                    if (results.size() >= MAX_RESULTS) {
                        return;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file: {}", path, e);
        }
    }

    /**
     * 匹配 Glob 模式
     */
    private static boolean matchesGlob(String path, String glob, Path baseDir) {
        String relativePath = baseDir.relativize(Paths.get(path)).toString().replace('\\', '/');

        // 简化实现：支持 ** 和 *
        String regex = glob.replace(".", "\\.").replace("**", ".*").replace("*", "[^/]*");
        return relativePath.matches(regex);
    }

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

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return defaultValue;
    }

    /**
     * 匹配结果
     */
    static class MatchResult {
        String filePath;
        int lineNumber;
        String lineContent;
        List<String> beforeLines;
        List<String> afterLines;
    }
}
