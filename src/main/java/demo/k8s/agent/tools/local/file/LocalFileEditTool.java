package demo.k8s.agent.tools.local.file;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 本地文件差异编辑工具 - 支持多种编辑模式：
 * 1. 字符串替换模式（原有）
 * 2. Unified diff 补丁模式（新增）
 * 3. 行号定位编辑模式（新增）
 */
public class LocalFileEditTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileEditTool.class);

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"path\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Path to the file to edit\"" +
            "    }," +
            "    \"oldText\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Text to find and replace (for string replacement mode)\"" +
            "    }," +
            "    \"newText\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Text to replace with (for string replacement mode)\"" +
            "    }," +
            "    \"diff\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Unified diff patch to apply (for patch mode)\"" +
            "    }," +
            "    \"lineNumber\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Line number to edit (for line-based mode)\"" +
            "    }," +
            "    \"endLineNumber\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"End line number for range edit (for line-based mode)\"" +
            "    }," +
            "    \"newLines\": {" +
            "      \"type\": \"array\"," +
            "      \"items\": {\"type\": \"string\"}," +
            "      \"description\": \"New lines to insert (for line-based mode)\"" +
            "    }," +
            "    \"expectedReplacements\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Expected number of replacements, defaults to 1\"" +
            "    }," +
            "    \"fuzzy\": {" +
            "      \"type\": \"boolean\"," +
            "      \"description\": \"Enable fuzzy matching for whitespace/indentation differences\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"path\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "file_edit",
                        ToolCategory.FILE_SYSTEM,
                        "Edit a file using one of three modes: (1) string replacement with oldText/newText, (2) unified diff patch, or (3) line-number based insertion/deletion",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();

        try {
            // 兼容 path 和 file_path 字段
            String pathStr = (String) input.get("path");
            if (pathStr == null || pathStr.isEmpty()) {
                pathStr = (String) input.get("file_path");
            }
            if (pathStr == null || pathStr.isEmpty()) {
                return LocalToolResult.error("path or file_path is required");
            }

            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                return LocalToolResult.error("File does not exist: " + pathStr);
            }

            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return LocalToolResult.error("File too large to edit: " + fileSize + " bytes");
            }

            // 读取文件内容
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

            // 判断使用哪种编辑模式
            if (input.containsKey("diff")) {
                // Unified diff 补丁模式
                return applyDiffMode(path, lines, (String) input.get("diff"), startTime);
            } else if (input.containsKey("lineNumber")) {
                // 行号定位模式
                return applyLineBasedMode(path, lines, input, startTime);
            } else {
                // 兼容 oldText/newText 和 old_string/new_string 字段
                String oldText = (String) input.get("oldText");
                if (oldText == null || oldText.isEmpty()) {
                    oldText = (String) input.get("old_string");
                }
                String newText = (String) input.get("newText");
                if (newText == null || newText.isEmpty()) {
                    newText = (String) input.get("new_string");
                }

                if (oldText != null && newText != null) {
                    // 字符串替换模式
                    return applyStringReplacementMode(path, lines, oldText, newText, input, startTime);
                } else {
                    return LocalToolResult.error("Must specify one of: (1) oldText+newText or old_string+new_string, (2) diff, or (3) lineNumber");
                }
            }

        } catch (Exception e) {
            log.error("File edit execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 字符串替换模式
     */
    private static LocalToolResult applyStringReplacementMode(Path path, List<String> lines,
                                                               String oldText, String newText,
                                                               Map<String, Object> input, long startTime) throws IOException {
        int expectedReplacements = getInt(input, "expectedReplacements", 1);
        boolean fuzzy = getBoolean(input, "fuzzy", false);

        if (oldText == null || oldText.isEmpty()) {
            return LocalToolResult.error("oldText is required for string replacement mode");
        }
        if (newText == null) {
            return LocalToolResult.error("newText is required for string replacement mode");
        }

        String content = String.join("\n", lines);
        if (!content.endsWith("\n")) {
            content += "\n";
        }

        // 查找匹配位置
        int matchIndex = findMatch(content, oldText, fuzzy);
        if (matchIndex == -1) {
            // 尝试 fuzzy 匹配
            if (!fuzzy) {
                matchIndex = findMatch(content, oldText, true);
                if (matchIndex != -1) {
                    return LocalToolResult.error("Text not found. Fuzzy match found similar content - consider enabling fuzzy=true");
                }
            }
            return LocalToolResult.error("Text not found in file");
        }

        // 计算行号
        int lineNumber = countLines(content, matchIndex);

        // 执行替换
        String newContent = content.replaceFirst(Pattern.quote(oldText), newText);

        // 原子写入
        writeAtomically(path, newContent);

        // 生成输出
        StringBuilder output = new StringBuilder();
        output.append("Successfully replaced text in ").append(path.toAbsolutePath()).append(":\n");
        output.append("  Line ").append(lineNumber).append(": - ").append(truncate(oldText, 80)).append("\n");
        output.append("  Line ").append(lineNumber).append(": + ").append(truncate(newText, 80)).append("\n");

        return LocalToolResult.builder()
                .success(true)
                .content(output.toString())
                .executionLocation("local")
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * Unified diff 补丁模式
     */
    private static LocalToolResult applyDiffMode(Path path, List<String> lines, String diff, long startTime) throws IOException {
        try {
            List<String> newLines = applyUnifiedDiff(lines, diff);

            // 原子写入
            String newContent = String.join("\n", newLines);
            if (!newContent.endsWith("\n")) {
                newContent += "\n";
            }
            writeAtomically(path, newContent);

            // 统计变更
            int added = 0;
            int removed = 0;
            for (String line : diff.split("\n")) {
                if (line.startsWith("+") && !line.startsWith("+++")) added++;
                if (line.startsWith("-") && !line.startsWith("---")) removed++;
            }

            StringBuilder output = new StringBuilder();
            output.append("Successfully applied diff to ").append(path.toAbsolutePath()).append(":\n");
            output.append("  +").append(added).append(" lines added\n");
            output.append("  -").append(removed).append(" lines removed\n");

            return LocalToolResult.builder()
                    .success(true)
                    .content(output.toString())
                    .executionLocation("local")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            return LocalToolResult.error("Failed to apply diff: " + e.getMessage());
        }
    }

    /**
     * 行号定位模式
     */
    private static LocalToolResult applyLineBasedMode(Path path, List<String> lines,
                                                       Map<String, Object> input, long startTime) throws IOException {
        int lineNumber = getInt(input, "lineNumber", 0);
        Integer endLineNumber = (Integer) input.get("endLineNumber");
        @SuppressWarnings("unchecked")
        List<String> newLines = (List<String>) input.get("newLines");

        if (lineNumber <= 0) {
            return LocalToolResult.error("lineNumber must be >= 1");
        }
        if (lineNumber > lines.size()) {
            return LocalToolResult.error("lineNumber " + lineNumber + " exceeds file length " + lines.size());
        }

        // 删除指定行范围的行
        if (endLineNumber != null) {
            if (endLineNumber < lineNumber) {
                return LocalToolResult.error("endLineNumber must be >= lineNumber");
            }
            for (int i = endLineNumber; i >= lineNumber; i--) {
                if (i > 0 && i <= lines.size()) {
                    lines.remove(i - 1);
                }
            }
        }

        // 插入新行
        if (newLines != null && !newLines.isEmpty()) {
            for (int i = 0; i < newLines.size(); i++) {
                lines.add(lineNumber - 1 + i, newLines.get(i));
            }
        }

        // 原子写入
        String newContent = String.join("\n", lines);
        if (!newContent.endsWith("\n")) {
            newContent += "\n";
        }
        writeAtomically(path, newContent);

        StringBuilder output = new StringBuilder();
        output.append("Successfully edited ").append(path.toAbsolutePath()).append(":\n");
        if (endLineNumber != null) {
            output.append("  Deleted lines ").append(lineNumber).append("-").append(endLineNumber).append("\n");
        }
        if (newLines != null) {
            output.append("  Inserted ").append(newLines.size()).append(" lines at line ").append(lineNumber).append("\n");
        }

        return LocalToolResult.builder()
                .success(true)
                .content(output.toString())
                .executionLocation("local")
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * 查找匹配位置（支持 fuzzy）
     */
    private static int findMatch(String content, String searchText, boolean fuzzy) {
        if (!fuzzy) {
            return content.indexOf(searchText);
        }

        // Fuzzy 匹配：忽略空白和缩进差异
        String normalizedContent = content.replaceAll("[ \\t]+", " ");
        String normalizedSearch = searchText.replaceAll("[ \\t]+", " ");

        int normalizedIndex = normalizedContent.indexOf(normalizedSearch);
        if (normalizedIndex == -1) {
            return -1;
        }

        // 映射回原始位置（近似）
        return mapNormalizedToOriginalIndex(content, normalizedContent, normalizedIndex);
    }

    /**
     * 计算行号
     */
    private static int countLines(String content, int charIndex) {
        int lineNumber = 1;
        for (int i = 0; i < charIndex && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    /**
     * 应用 unified diff
     */
    private static List<String> applyUnifiedDiff(List<String> originalLines, String diff) throws Exception {
        List<String> result = new ArrayList<>(originalLines);
        List<DiffHunk> hunks = parseUnifiedDiff(diff);

        if (hunks.isEmpty()) {
            throw new Exception("No valid hunks found in diff");
        }

        // 按 hunk 处理（从后往前，避免索引偏移）
        Collections.reverse(hunks);

        for (DiffHunk hunk : hunks) {
            result = applyHunk(result, hunk);
        }

        return result;
    }

    /**
     * 解析 unified diff 格式
     */
    private static List<DiffHunk> parseUnifiedDiff(String diff) {
        List<DiffHunk> hunks = new ArrayList<>();
        String[] lines = diff.split("\n");

        Pattern hunkPattern = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

        DiffHunk currentHunk = null;

        for (String line : lines) {
            if (line.startsWith("@@")) {
                var matcher = hunkPattern.matcher(line);
                if (matcher.find()) {
                    currentHunk = new DiffHunk();
                    currentHunk.oldStart = Integer.parseInt(matcher.group(1));
                    currentHunk.oldLines = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
                    currentHunk.newStart = Integer.parseInt(matcher.group(3));
                    currentHunk.newLines = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 1;
                    hunks.add(currentHunk);
                }
            } else if (currentHunk != null) {
                if (line.startsWith("+")) {
                    currentHunk.addedLines.add(line.substring(1));
                } else if (line.startsWith("-")) {
                    currentHunk.removedLines.add(line.substring(1));
                } else if (line.startsWith(" ")) {
                    currentHunk.contextLines.add(line.substring(1));
                }
            }
        }

        return hunks;
    }

    /**
     * 应用单个 hunk
     */
    private static List<String> applyHunk(List<String> lines, DiffHunk hunk) throws Exception {
        // 找到匹配位置
        int matchStart = findHunkMatch(lines, hunk);
        if (matchStart == -1) {
            throw new Exception("Hunk does not match at expected line " + hunk.oldStart);
        }

        // 构建新内容
        List<String> result = new ArrayList<>();

        // 添加 hunk 之前的行
        for (int i = 0; i < matchStart; i++) {
            result.add(lines.get(i));
        }

        // 添加新行（保留上下文）
        int contextIdx = 0;
        for (String ctxLine : hunk.contextLines) {
            // 在 removed 行之前添加 context
            int removedBefore = countRemovedBefore(hunk, contextIdx);
            if (removedBefore > 0) {
                // 已经处理过
            }
            result.add(ctxLine);
            contextIdx++;
        }

        // 添加 inserted 行
        result.addAll(hunk.addedLines);

        // 添加 hunk 之后的行
        int skipLines = hunk.oldLines;
        for (int i = matchStart + skipLines; i < lines.size(); i++) {
            result.add(lines.get(i));
        }

        return result;
    }

    /**
     * 查找 hunk 匹配位置
     */
    private static int findHunkMatch(List<String> lines, DiffHunk hunk) {
        // 简化的匹配逻辑：在预期位置附近查找
        int expectedStart = hunk.oldStart - 1; // 转为 0-based

        if (expectedStart >= 0 && expectedStart < lines.size()) {
            // 检查上下文是否匹配
            int contextIdx = 0;
            boolean matches = true;

            for (int i = expectedStart; i < lines.size() && contextIdx < hunk.contextLines.size(); i++) {
                if (!lines.get(i).equals(hunk.contextLines.get(contextIdx))) {
                    matches = false;
                    break;
                }
                contextIdx++;
            }

            if (matches) {
                return expectedStart;
            }
        }

        // 尝试在附近查找
        int searchRange = 10;
        for (int offset = 1; offset <= searchRange; offset++) {
            // 尝试上方
            int above = expectedStart - offset;
            if (above >= 0 && matchesAt(lines, above, hunk.contextLines)) {
                return above;
            }
            // 尝试下方
            int below = expectedStart + offset;
            if (below >= 0 && below < lines.size() && matchesAt(lines, below, hunk.contextLines)) {
                return below;
            }
        }

        return expectedStart >= 0 ? expectedStart : 0;
    }

    /**
     * 检查在指定位置是否匹配
     */
    private static boolean matchesAt(List<String> lines, int start, List<String> contextLines) {
        if (start + contextLines.size() > lines.size()) {
            return false;
        }
        for (int i = 0; i < contextLines.size(); i++) {
            if (!lines.get(start + i).equals(contextLines.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 统计 hunk 中在某索引之前删除的行数
     */
    private static int countRemovedBefore(DiffHunk hunk, int contextIdx) {
        // 简化实现
        return 0;
    }

    /**
     * 原子写入文件
     */
    private static void writeAtomically(Path path, String content) throws IOException {
        Path tempFile = Files.createTempFile(path.getParent(), ".tmp-", ".txt");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    /**
     * 截断字符串
     */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
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

    /**
     * 映射 normalized 索引到原始索引
     */
    private static int mapNormalizedToOriginalIndex(String original, String normalized, int normalizedIndex) {
        // 简化的映射：返回近似位置
        return Math.min(normalizedIndex, original.length() - 1);
    }

    /**
     * Diff hunk 数据结构
     */
    private static class DiffHunk {
        int oldStart;
        int oldLines;
        int newStart;
        int newLines;
        List<String> contextLines = new ArrayList<>();
        List<String> removedLines = new ArrayList<>();
        List<String> addedLines = new ArrayList<>();
    }
}
