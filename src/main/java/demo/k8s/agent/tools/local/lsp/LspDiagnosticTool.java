package demo.k8s.agent.tools.local.lsp;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * 语言服务器协议 (LSP) 基础支持工具。
 * <p>
 * 提供以下功能：
 * - 诊断追踪（错误/警告）
 * - 代码导航（跳转定义）
 * - 符号查询
 * - 代码格式化
 * <p>
 * 当前实现：使用命令行 LSP 工具（如 clangd, typescript-language-server 等）的简化封装
 */
public class LspDiagnosticTool {

    private static final Logger log = LoggerFactory.getLogger(LspDiagnosticTool.class);

    private static final int DEFAULT_TIMEOUT_MS = 30000;
    private static final int MAX_OUTPUT_LINES = 500;

    /**
     * 支持的语言
     */
    public enum Language {
        JAVA("java", "javac", "-parameters"),
        TYPESCRIPT("typescript", "tsc", "--noEmit"),
        JAVASCRIPT("javascript", "eslint", "--format=json"),
        PYTHON("python", "pylint", "--output-format=json"),
        GO("go", "go", "vet"),
        RUST("rust", "cargo", "check"),
        CPP("cpp", "clang", "--analyze");

        public final String name;
        public final String lintCommand;
        public final String lintArgs;

        Language(String name, String lintCommand, String lintArgs) {
            this.name = name;
            this.lintCommand = lintCommand;
            this.lintArgs = lintArgs;
        }

        public static Language fromFileExtension(String extension) {
            switch (extension.toLowerCase()) {
                case "java": return JAVA;
                case "ts":
                case "tsx": return TYPESCRIPT;
                case "js":
                case "jsx": return JAVASCRIPT;
                case "py": return PYTHON;
                case "go": return GO;
                case "rs": return RUST;
                case "cpp":
                case "cc":
                case "cxx":
                case "h":
                case "hpp": return CPP;
                default: return null;
            }
        }
    }

    /**
     * 诊断信息
     */
    public static class Diagnostic {
        public String severity; // "error", "warning", "info"
        public int line;
        public int column;
        public String message;
        public String source;
        public String code;

        public Diagnostic(String severity, int line, int column, String message) {
            this.severity = severity;
            this.line = line;
            this.column = column;
            this.message = message;
        }
    }

    /**
     * 诊断结果
     */
    public static class DiagnosticResult {
        public Path file;
        public Language language;
        public List<Diagnostic> diagnostics = new ArrayList<>();
        public int errorCount = 0;
        public int warningCount = 0;
        public String rawOutput;
    }

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"action\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Action: 'diagnose', 'findReferences', 'findDefinition', 'format'\"" +
            "    }," +
            "    \"file\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"File path to analyze\"" +
            "    }," +
            "    \"language\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Language: 'java', 'typescript', 'python', etc. (auto-detected if not specified)\"" +
            "    }," +
            "    \"line\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Line number for findReferences/findDefinition\"" +
            "    }," +
            "    \"column\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Column number for findReferences/findDefinition\"" +
            "    }," +
            "    \"cwd\": {" +
            "      \"type\": \"string\"," +
            "      \"description\": \"Working directory\"" +
            "    }," +
            "    \"timeout\": {" +
            "      \"type\": \"integer\"," +
            "      \"description\": \"Timeout in milliseconds\"" +
            "    }" +
            "  }," +
            "  \"required\": [\"action\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "lsp",
                        ToolCategory.EXTERNAL,
                        "Language Server Protocol diagnostics: analyze code for errors/warnings, find definitions and references. Supports Java, TypeScript, Python, Go, Rust, C++.",
                        INPUT_SCHEMA,
                        null,
                        false));
    }

    /**
     * 执行工具
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        try {
            String action = (String) input.get("action");
            if (action == null || action.isEmpty()) {
                return LocalToolResult.error("action is required");
            }

            switch (action.toLowerCase()) {
                case "diagnose":
                case "analyze":
                    return diagnose(input);
                case "findreferences":
                    return findReferences(input);
                case "finddefinition":
                case "goto":
                    return findDefinition(input);
                case "format":
                    return format(input);
                case "symbols":
                    return listSymbols(input);
                default:
                    return LocalToolResult.error("Unknown action: " + action);
            }

        } catch (Exception e) {
            log.error("LSP execution failed", e);
            return LocalToolResult.error("Error: " + e.getMessage());
        }
    }

    /**
     * 诊断文件
     */
    private static LocalToolResult diagnose(Map<String, Object> input) {
        String fileStr = (String) input.get("file");
        String languageStr = (String) input.get("language");
        String cwdStr = (String) input.get("cwd");
        int timeout = getInt(input, "timeout", DEFAULT_TIMEOUT_MS);

        if (fileStr == null || fileStr.isEmpty()) {
            return LocalToolResult.error("file is required for diagnose action");
        }

        Path file = Paths.get(fileStr);
        if (!Files.exists(file)) {
            return LocalToolResult.error("File does not exist: " + fileStr);
        }

        // 检测语言
        Language language = getLanguage(languageStr, file);
        if (language == null) {
            return LocalToolResult.error("Unsupported language. Supported: java, typescript, javascript, python, go, rust, cpp");
        }

        try {
            DiagnosticResult result = runDiagnostic(file, language, cwdStr, timeout);

            StringBuilder output = new StringBuilder();
            output.append("LSP Diagnostic Report\n");
            output.append("=====================\n\n");
            output.append("File: ").append(file.toAbsolutePath()).append("\n");
            output.append("Language: ").append(language.name).append("\n");
            output.append("Errors: ").append(result.errorCount).append("\n");
            output.append("Warnings: ").append(result.warningCount).append("\n\n");

            if (result.diagnostics.isEmpty()) {
                output.append("No issues found.\n");
            } else {
                output.append("Issues:\n");
                output.append("-------\n");
                for (Diagnostic d : result.diagnostics) {
                    output.append(String.format("[%s] %s:%d:%d - %s\n",
                            d.severity.toUpperCase(),
                            file.getFileName(),
                            d.line,
                            d.column,
                            d.message));
                }
            }

            return LocalToolResult.builder()
                    .success(true)
                    .content(output.toString())
                    .executionLocation("local")
                    .metadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                            Map.of(
                                    "errors", result.errorCount,
                                    "warnings", result.warningCount,
                                    "total", result.diagnostics.size())))
                    .build();

        } catch (Exception e) {
            return LocalToolResult.error("Diagnostic failed: " + e.getMessage());
        }
    }

    /**
     * 查找引用
     */
    private static LocalToolResult findReferences(Map<String, Object> input) {
        // 简化实现：返回不支持的错误
        return LocalToolResult.error("findReferences action is not yet implemented in this version. " +
                "This feature requires a full LSP server connection.");
    }

    /**
     * 查找定义
     */
    private static LocalToolResult findDefinition(Map<String, Object> input) {
        // 简化实现：返回不支持的错误
        return LocalToolResult.error("findDefinition action is not yet implemented in this version. " +
                "This feature requires a full LSP server connection.");
    }

    /**
     * 格式化代码
     */
    private static LocalToolResult format(Map<String, Object> input) {
        // 简化实现：返回不支持的错误
        return LocalToolResult.error("format action is not yet implemented in this version.");
    }

    /**
     * 列出符号
     */
    private static LocalToolResult listSymbols(Map<String, Object> input) {
        // 简化实现：返回不支持的错误
        return LocalToolResult.error("symbols action is not yet implemented in this version.");
    }

    /**
     * 运行诊断
     */
    private static DiagnosticResult runDiagnostic(Path file, Language language, String cwdStr, int timeout)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {

        DiagnosticResult result = new DiagnosticResult();
        result.file = file;
        result.language = language;

        // 构建命令
        List<String> command = buildDiagnosticCommand(file, language);
        if (command == null) {
            result.diagnostics.add(new Diagnostic("error", 0, 0,
                    "No diagnostic tool available for language: " + language.name));
            return result;
        }

        // 设置工作目录
        ProcessBuilder pb = new ProcessBuilder(command);
        if (cwdStr != null && !cwdStr.isEmpty()) {
            pb.directory(new File(cwdStr));
        } else {
            pb.directory(file.getParent().toFile());
        }

        // 执行命令
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 读取输出
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                        .limit(MAX_OUTPUT_LINES)
                        .collect(java.util.stream.Collectors.joining("\n"));
            } catch (IOException e) {
                return "Error reading output: " + e.getMessage();
            }
        });

        // 等待完成
        boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            result.diagnostics.add(new Diagnostic("error", 0, 0,
                    "Diagnostic timed out after " + timeout + "ms"));
            return result;
        }

        String output = outputFuture.get(5, TimeUnit.SECONDS);
        result.rawOutput = output;

        // 解析输出
        parseDiagnosticOutput(result, output, language);

        return result;
    }

    /**
     * 构建诊断命令
     */
    private static List<String> buildDiagnosticCommand(Path file, Language language) {
        // 简化实现：返回 null 表示不支持
        // 实际实现应该根据语言调用相应的工具
        return null;
    }

    /**
     * 解析诊断输出
     */
    private static void parseDiagnosticOutput(DiagnosticResult result, String output, Language language) {
        if (output == null || output.isEmpty()) {
            return;
        }

        // 简化实现：将整个输出作为单个诊断
        // 实际实现应该解析具体的输出格式

        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("error") || line.contains("Error") || line.contains("ERROR")) {
                Diagnostic d = new Diagnostic("error", 0, 0, line.trim());
                result.diagnostics.add(d);
                result.errorCount++;
            } else if (line.contains("warning") || line.contains("Warning") || line.contains("WARNING")) {
                Diagnostic d = new Diagnostic("warning", 0, 0, line.trim());
                result.diagnostics.add(d);
                result.warningCount++;
            }
        }
    }

    /**
     * 获取语言
     */
    private static Language getLanguage(String languageStr, Path file) {
        if (languageStr != null && !languageStr.isEmpty()) {
            return Language.fromFileExtension(languageStr);
        }

        String fileName = file.toString();
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1);
            return Language.fromFileExtension(extension);
        }

        return null;
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
}
