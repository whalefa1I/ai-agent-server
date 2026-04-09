package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本地文件匹配工具 - 使用 Glob 模式查找文件。
 */
public class LocalGlobTool {

    private static final Logger log = LoggerFactory.getLogger(LocalGlobTool.class);

    /**
     * Glob 工具提示词（与 Claude Code 对齐）
     */
    private static final String GLOB_PROMPT = """
            - Fast file pattern matching tool that works with any codebase size
            - Supports glob patterns like "**/*.js" or "src/**/*.ts"
            - Returns matching file paths sorted by modification time
            - Use this tool when you need to find files by name patterns
            - When you are doing an open ended search that may require multiple rounds of globbing and grepping, use the Agent tool instead
            """;

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"pattern\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Glob pattern to match (e.g., **/*.java, src/**/*.ts)\"" +
            "    }," +
            "    \"path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Directory to search under workspace root, OR glob pattern if pattern is omitted and this contains * or ?\"" +
            "    }" +
            "  }" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "glob",
                        ToolCategory.FILE_SYSTEM,
                        "Fast file pattern matching tool. Use ** for recursive matching.",
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
            String pattern = stringOrNull(input.get("pattern"));
            String pathOrAlias = FilesystemPathArgs.readPathOrAlias(input);

            // 模型常把 glob 写在 path（如 {"path": "**/SKILL.md"}），而 schema 要求 pattern
            if ((pattern == null || pattern.isEmpty()) && pathOrAlias != null && looksLikeGlob(pathOrAlias)) {
                pattern = pathOrAlias;
                pathOrAlias = null;
            }

            String basePath = pathOrAlias;
            if (basePath == null || basePath.isBlank()) {
                basePath = WorkspacePathPolicy.workspaceRoot();
            }

            if (pattern == null || pattern.isEmpty()) {
                return LocalToolResult.error("pattern is required (or pass a glob in path, e.g. **/SKILL.md)");
            }

            Path baseDir = Path.of(basePath);
            if (!Files.exists(baseDir)) {
                return LocalToolResult.error("Directory does not exist: " + basePath);
            }

            if (!Files.isDirectory(baseDir)) {
                return LocalToolResult.error("Path is not a directory: " + basePath);
            }

            List<String> matchedFiles = matchFiles(baseDir, pattern);

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(matchedFiles.size()).append(" file(s):\n\n");
            for (String file : matchedFiles) {
                result.append(file).append("\n");
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(result.toString())
                    .executionLocation("local")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Glob execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    private static String stringOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /** true if the string is likely a glob pattern rather than a directory path */
    private static boolean looksLikeGlob(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    /**
     * 匹配文件
     */
    private static List<String> matchFiles(Path baseDir, String globPattern) throws IOException {
        List<String> matchedFiles = new ArrayList<>();

        // 将 Glob 模式转换为正则表达式
        Pattern pattern = globToRegex(globPattern);

        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relativePath = baseDir.relativize(file);
                String relativePathStr = relativePath.toString().replace('\\', '/');

                if (pattern.matcher(relativePathStr).matches()) {
                    matchedFiles.add(relativePathStr);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 跳过隐藏目录
                if (dir.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return matchedFiles;
    }

    /**
     * 将 Glob 模式转换为正则表达式
     */
    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);

            if (c == '*') {
                // 检查是否是 **
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++; // 跳过第二个 *
                    // 如果后面跟着 /，则也匹配它
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                        i++; // 跳过 /
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (c == '.') {
                regex.append("\\.");
            } else if (c == '+') {
                regex.append("\\+");
            } else if (c == '^') {
                regex.append("\\^");
            } else if (c == '$') {
                regex.append("\\$");
            } else if (c == '|') {
                regex.append("\\|");
            } else if (c == '(') {
                regex.append("\\(");
            } else if (c == ')') {
                regex.append("\\)");
            } else if (c == '{') {
                regex.append("(");
            } else if (c == '}') {
                regex.append(")");
            } else if (c == ',') {
                regex.append("|");
            } else if (c == '[') {
                regex.append("[");
            } else if (c == ']') {
                regex.append("]");
            } else {
                regex.append(c);
            }
        }

        regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
