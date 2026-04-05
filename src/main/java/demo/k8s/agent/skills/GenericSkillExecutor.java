package demo.k8s.agent.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 通用技能执行器
 *
 * 根据技能目录中的实现文件自动调用相应的脚本
 * 支持 Python (.py) 和 Node.js (.js) 脚本
 *
 * 日志和错误处理特性：
 * - 完整的技能加载日志
 * - 详细的脚本执行诊断信息
 * - 环境兼容性检查（Python/Node.js 是否安装）
 * - 结构化的错误输出，便于 agent 诊断
 */
@Component
public class GenericSkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(GenericSkillExecutor.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // 技能目录 -> 执行器类型映射
    private final Map<String, SkillType> skillTypes = new ConcurrentHashMap<>();

    // 运行环境缓存
    private Boolean pythonInstalled = null;
    private Boolean nodejsInstalled = null;
    private String pythonExecutable = null;
    private String nodeExecutable = null;

    public enum SkillType {
        PYTHON,     // Python 脚本 (.py)
        NODEJS,     // Node.js 脚本 (.js)
        UNSUPPORTED // 不支持的类型
    }

    /**
     * 检测技能类型（带详细日志）
     */
    public SkillType detectSkillType(String skillDirectory) {
        log.debug("检测技能类型：{}", skillDirectory);
        Path dir = Paths.get(skillDirectory);

        if (!Files.exists(dir)) {
            log.warn("技能目录不存在：{}", skillDirectory);
            return SkillType.UNSUPPORTED;
        }

        // 检查是否有 Python 脚本
        if (Files.exists(dir.resolve("scripts")) ||
            Files.exists(dir.resolve("__main__.py")) ||
            hasPythonFiles(dir)) {
            log.debug("检测到 Python 技能：{}", skillDirectory);
            return SkillType.PYTHON;
        }

        // 检查是否有 Node.js 脚本
        if (Files.exists(dir.resolve("index.js")) ||
            Files.exists(dir.resolve("main.js")) ||
            hasNodeJsFiles(dir)) {
            log.debug("检测到 Node.js 技能：{}", skillDirectory);
            return SkillType.NODEJS;
        }

        // 检查 SKILL.md 中的使用说明
        Path skillMdPath = dir.resolve("SKILL.md");
        if (Files.exists(skillMdPath)) {
            try {
                String content = Files.readString(skillMdPath);
                if (content.contains("python") || content.contains("Python")) {
                    log.debug("从 SKILL.md 推断为 Python 技能：{}", skillDirectory);
                    return SkillType.PYTHON;
                }
                if (content.contains("node") || content.contains("Node.js") || content.contains("npm")) {
                    log.debug("从 SKILL.md 推断为 Node.js 技能：{}", skillDirectory);
                    return SkillType.NODEJS;
                }
            } catch (IOException e) {
                log.warn("读取 SKILL.md 失败：{}", skillMdPath);
            }
        }

        log.warn("未检测到可执行脚本，技能类型：UNSUPPORTED - {}", skillDirectory);
        return SkillType.UNSUPPORTED;
    }

    /**
     * 执行技能
     *
     * @param skillDirectory 技能目录
     * @param args 参数
     * @return 执行结果
     */
    public String execute(String skillDirectory, Map<String, Object> args) {
        log.info("=== 开始执行技能 ===");
        log.info("技能目录：{}", skillDirectory);
        log.info("执行参数：{}", args);

        // 检查技能目录是否存在
        Path dir = Paths.get(skillDirectory);
        if (!Files.exists(dir)) {
            String errorMsg = String.format(
                "技能目录不存在 [目录=%s, 参数=%s]",
                skillDirectory, args);
            log.error("❌ {}", errorMsg);
            return buildJsonError(errorMsg, "SKILL_DIRECTORY_NOT_FOUND");
        }

        SkillType skillType = skillTypes.computeIfAbsent(
            skillDirectory,
            k -> detectSkillType(k)
        );

        log.info("技能类型：{}", skillType);

        // 检查运行环境
        if (skillType == SkillType.PYTHON && !checkPythonInstalled()) {
            String errorMsg = "Python 未安装，无法执行 Python 技能。请安装 Python 3.8+ 并添加到 PATH";
            log.error("❌ {}", errorMsg);
            return buildJsonError(errorMsg, "PYTHON_NOT_INSTALLED");
        }

        if (skillType == SkillType.NODEJS && !checkNodeJsInstalled()) {
            String errorMsg = "Node.js 未安装，无法执行 Node.js 技能。请安装 Node.js 16+ 并添加到 PATH";
            log.error("❌ {}", errorMsg);
            return buildJsonError(errorMsg, "NODEJS_NOT_INSTALLED");
        }

        String result = switch (skillType) {
            case PYTHON -> executePythonScript(skillDirectory, args);
            case NODEJS -> executeNodeJsScript(skillDirectory, args);
            case UNSUPPORTED -> {
                String errorMsg = String.format(
                    "不支持的技能类型 [目录=%s]。技能必须包含 .py 或 .js 脚本文件",
                    skillDirectory);
                log.error("❌ {}", errorMsg);
                yield buildJsonError(errorMsg, "UNSUPPORTED_SKILL_TYPE");
            }
        };

        log.info("=== 技能执行完成 ===");
        return result;
    }

    /**
     * 执行 Python 脚本
     */
    private String executePythonScript(String skillDirectory, Map<String, Object> args) {
        log.info("--- 执行 Python 脚本 ---");
        try {
            Path dir = Paths.get(skillDirectory);

            // 查找 Python 脚本
            Path scriptPath = findPythonScript(dir);
            if (scriptPath == null) {
                // 列出目录内容帮助诊断
                String dirContents = listDirectoryContents(dir);
                String errorMsg = String.format(
                    "未找到 Python 脚本文件 [目录=%s]\n目录内容:\n%s",
                    skillDirectory, dirContents);
                log.error("❌ {}", errorMsg);
                return buildJsonError(errorMsg, "PYTHON_SCRIPT_NOT_FOUND");
            }

            log.info("找到 Python 脚本：{}", scriptPath);

            // 构建命令
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable != null ? pythonExecutable : "python");
            command.add(scriptPath.toString());

            // 添加参数
            addArgumentsToCommand(command, args);

            log.info("执行命令：{}", String.join(" ", command));
            log.info("工作目录：{}", dir.toAbsolutePath());

            // 执行命令
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(dir.toFile());

            // 记录环境变量（调试用）
            Map<String, String> env = pb.environment();
            log.debug("PYTHONPATH: {}", env.getOrDefault("PYTHONPATH", "(未设置)"));
            log.debug("PATH: {}", env.getOrDefault("PATH", env.getOrDefault("Path", "(未设置)")));

            Process process = pb.start();

            // 读取输出和错误
            ExecutionResult executionResult = readProcessOutput(process);

            // 等待进程结束
            int exitCode = process.waitFor();

            log.info("退出码：{}", exitCode);
            log.info("输出：{}", executionResult.output);

            if (!executionResult.error.isEmpty()) {
                log.warn("错误输出：{}", executionResult.error);
            }

            if (exitCode == 0) {
                return executionResult.output.trim();
            } else {
                String errorMsg = String.format(
                    "Python 脚本执行失败 [退出码=%d, 脚本=%s]\n标准输出:\n%s\n错误输出:\n%s",
                    exitCode, scriptPath, executionResult.output, executionResult.error);
                log.error("❌ {}", errorMsg);
                return buildJsonError(errorMsg, "PYTHON_SCRIPT_EXECUTION_FAILED");
            }

        } catch (IOException e) {
            String errorMsg = String.format(
                "执行 Python 脚本时发生 IO 错误 [目录=%s, 错误=%s]",
                skillDirectory, e.getMessage());
            log.error("❌ {}", errorMsg, e);
            return buildJsonError(errorMsg + "\n堆栈跟踪:\n" + getStackTrace(e), "IO_EXCEPTION");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = String.format(
                "Python 脚本执行被中断 [目录=%s]",
                skillDirectory);
            log.error("❌ {}", errorMsg, e);
            return buildJsonError(errorMsg, "INTERRUPTED_EXCEPTION");
        }
    }

    /**
     * 执行 Node.js 脚本
     */
    private String executeNodeJsScript(String skillDirectory, Map<String, Object> args) {
        log.info("--- 执行 Node.js 脚本 ---");
        try {
            Path dir = Paths.get(skillDirectory);

            // 查找 Node.js 脚本
            Path scriptPath = findNodeJsScript(dir);
            if (scriptPath == null) {
                String dirContents = listDirectoryContents(dir);
                String errorMsg = String.format(
                    "未找到 Node.js 脚本文件 [目录=%s]\n目录内容:\n%s",
                    skillDirectory, dirContents);
                log.error("❌ {}", errorMsg);
                return buildJsonError(errorMsg, "NODEJS_SCRIPT_NOT_FOUND");
            }

            log.info("找到 Node.js 脚本：{}", scriptPath);

            // 检查是否有 package.json 和 bin 配置
            Path packageJsonPath = dir.resolve("package.json");
            boolean useNpx = false;

            if (Files.exists(packageJsonPath)) {
                String packageJson = Files.readString(packageJsonPath);
                if (packageJson.contains("\"bin\"")) {
                    useNpx = true;
                    log.info("检测到 package.json 中的 bin 配置，使用 npx 调用");
                }
            }

            List<String> command = new ArrayList<>();

            if (useNpx) {
                command.add(nodeExecutable != null ? nodeExecutable : "npx");
                command.add(dir.toAbsolutePath().toString());
                addArgumentsToCommand(command, args);
            } else {
                command.add(nodeExecutable != null ? nodeExecutable : "node");
                command.add(scriptPath.toString());
                // 将参数转换为 JSON 传递给脚本
                if (!args.isEmpty()) {
                    String jsonArgs = JSON_MAPPER.writeValueAsString(args);
                    command.add(jsonArgs);
                    log.debug("JSON 参数：{}", jsonArgs);
                }
            }

            log.info("执行命令：{}", String.join(" ", command));
            log.info("工作目录：{}", dir.toAbsolutePath());

            return executeCommand(command, dir.toFile(), skillDirectory);

        } catch (IOException e) {
            String errorMsg = String.format(
                "执行 Node.js 脚本时发生 IO 错误 [目录=%s, 错误=%s]",
                skillDirectory, e.getMessage());
            log.error("❌ {}", errorMsg, e);
            return buildJsonError(errorMsg + "\n堆栈跟踪:\n" + getStackTrace(e), "IO_EXCEPTION");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = String.format(
                "Node.js 脚本执行被中断 [目录=%s]",
                skillDirectory);
            log.error("❌ {}", errorMsg, e);
            return buildJsonError(errorMsg, "INTERRUPTED_EXCEPTION");
        }
    }

    /**
     * 执行通用命令
     */
    private String executeCommand(List<String> command, java.io.File workingDir, String skillDirectory)
            throws IOException, InterruptedException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(workingDir);

            log.debug("启动进程...");
            Process process = pb.start();

            // 读取输出和错误
            ExecutionResult executionResult = readProcessOutput(process);

            int exitCode = process.waitFor();

            log.info("退出码：{}", exitCode);
            log.info("输出：{}", executionResult.output);

            if (!executionResult.error.isEmpty()) {
                log.warn("错误输出：{}", executionResult.error);
            }

            if (exitCode == 0) {
                return executionResult.output.trim();
            } else {
                String errorMsg = String.format(
                    "Node.js 脚本执行失败 [退出码=%d, 目录=%s]\n标准输出:\n%s\n错误输出:\n%s",
                    exitCode, skillDirectory, executionResult.output, executionResult.error);
                log.error("❌ {}", errorMsg);
                return buildJsonError(errorMsg, "NODEJS_SCRIPT_EXECUTION_FAILED");
            }

        } catch (IOException e) {
            String errorMsg = String.format(
                "执行命令时发生 IO 错误 [目录=%s, 错误=%s]",
                skillDirectory, e.getMessage());
            log.error("❌ {}", errorMsg, e);
            return buildJsonError(errorMsg + "\n堆栈跟踪:\n" + getStackTrace(e), "IO_EXCEPTION");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = String.format(
                "命令执行被中断 [目录=%s]",
                skillDirectory);
            log.error("❌ {}", errorMsg, e);
            return buildJsonError(errorMsg, "INTERRUPTED_EXCEPTION");
        }
    }

    /**
     * 读取进程输出
     */
    private ExecutionResult readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // 如果有错误流（虽然我们重定向了，但保留这个逻辑）
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }

        return new ExecutionResult(
            output.toString().trim(),
            error.toString().trim()
        );
    }

    /**
     * 添加参数到命令
     */
    private void addArgumentsToCommand(List<String> command, Map<String, Object> args) {
        if (args.containsKey("expression")) {
            command.add((String) args.get("expression"));
        } else if (args.containsKey("path")) {
            command.add((String) args.get("path"));
        } else if (args.containsKey("directory")) {
            command.add((String) args.get("directory"));
        } else if (args.containsKey("action")) {
            command.add((String) args.get("action"));
        } else if (!args.isEmpty()) {
            // 传递第一个字符串参数
            for (Object value : args.values()) {
                if (value instanceof String) {
                    command.add((String) value);
                    break;
                }
            }
        }
    }

    /**
     * 检查 Python 是否已安装
     */
    private boolean checkPythonInstalled() {
        if (pythonInstalled != null) {
            return pythonInstalled;
        }

        // 尝试常见的 Python 可执行文件名称
        String[] possibleCommands = {"python3", "python", "py"};

        for (String cmd : possibleCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String version = reader.readLine();
                    if (version != null && version.contains("Python")) {
                        pythonInstalled = true;
                        pythonExecutable = cmd;
                        log.info("Python 环境检测成功：{} - {}", cmd, version);
                        return true;
                    }
                }

                process.waitFor();
            } catch (Exception e) {
                // 继续尝试下一个命令
            }
        }

        pythonInstalled = false;
        log.warn("Python 未安装或不在 PATH 中。尝试的命令：" + String.join(", ", possibleCommands));
        return false;
    }

    /**
     * 检查 Node.js 是否已安装
     */
    private boolean checkNodeJsInstalled() {
        if (nodejsInstalled != null) {
            return nodejsInstalled;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String version = reader.readLine();
                if (version != null && version.startsWith("v")) {
                    nodejsInstalled = true;
                    nodeExecutable = "node";
                    log.info("Node.js 环境检测成功：{}", version);
                    return true;
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // 忽略异常
        }

        nodejsInstalled = false;
        log.warn("Node.js 未安装或不在 PATH 中");
        return false;
    }

    /**
     * 查找 Python 脚本
     */
    private Path findPythonScript(Path dir) {
        // 优先查找 scripts/calc.py 这样的脚本
        Path scriptsDir = dir.resolve("scripts");
        if (Files.exists(scriptsDir)) {
            try (var stream = Files.list(scriptsDir)) {
                return stream.filter(p -> p.toString().endsWith(".py"))
                        .peek(p -> log.debug("找到 Python 脚本候选：{}", p))
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                log.warn("列出 scripts 目录失败：{} - {}", scriptsDir, e.getMessage());
            }
        }

        // 查找 __main__.py
        Path mainPy = dir.resolve("__main__.py");
        if (Files.exists(mainPy)) {
            log.debug("找到 __main__.py: {}", mainPy);
            return mainPy;
        }

        // 查找根目录的 .py 文件
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".py"))
                    .peek(p -> log.debug("找到 Python 脚本候选：{}", p))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("列出目录失败：{} - {}", dir, e.getMessage());
        }

        return null;
    }

    /**
     * 查找 Node.js 脚本
     */
    private Path findNodeJsScript(Path dir) {
        // 优先查找 index.js
        Path indexJs = dir.resolve("index.js");
        if (Files.exists(indexJs)) {
            log.debug("找到 index.js: {}", indexJs);
            return indexJs;
        }

        // 查找 main.js
        Path mainJs = dir.resolve("main.js");
        if (Files.exists(mainJs)) {
            log.debug("找到 main.js: {}", mainJs);
            return mainJs;
        }

        // 查找根目录的 .js 文件
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".js"))
                    .peek(p -> log.debug("找到 Node.js 脚本候选：{}", p))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("列出目录失败：{} - {}", dir, e.getMessage());
        }

        return null;
    }

    /**
     * 检查目录是否有 Python 文件
     */
    private boolean hasPythonFiles(Path dir) {
        try (var stream = Files.walk(dir, 2)) {
            boolean hasPython = stream.anyMatch(p -> p.toString().endsWith(".py"));
            log.debug("目录 {} {} Python 文件", dir, hasPython ? "包含" : "不包含");
            return hasPython;
        } catch (IOException e) {
            log.debug("检查 Python 文件失败：{} - {}", dir, e.getMessage());
            return false;
        }
    }

    /**
     * 检查目录是否有 Node.js 文件
     */
    private boolean hasNodeJsFiles(Path dir) {
        try (var stream = Files.walk(dir, 2)) {
            boolean hasNode = stream.anyMatch(p -> p.toString().endsWith(".js"));
            log.debug("目录 {} {} Node.js 文件", dir, hasNode ? "包含" : "不包含");
            return hasNode;
        } catch (IOException e) {
            log.debug("检查 Node.js 文件失败：{} - {}", dir, e.getMessage());
            return false;
        }
    }

    /**
     * 列出目录内容（用于错误诊断）
     */
    private String listDirectoryContents(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream
                .map(p -> "  - " + p.getFileName() + (Files.isDirectory(p) ? "/" : ""))
                .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "  (无法列出目录内容：" + e.getMessage() + ")";
        }
    }

    /**
     * 获取堆栈跟踪
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建 JSON 格式的错误响应
     */
    private String buildJsonError(String message, String errorCode) {
        try {
            return JSON_MAPPER.writeValueAsString(Map.of(
                "error", true,
                "code", errorCode,
                "message", message
            ));
        } catch (Exception e) {
            return "{\"error\": true, \"code\": \"JSON_BUILD_FAILED\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 执行结果
     */
    private static class ExecutionResult {
        final String output;
        final String error;

        ExecutionResult(String output, String error) {
            this.output = output;
            this.error = error;
        }
    }
}
