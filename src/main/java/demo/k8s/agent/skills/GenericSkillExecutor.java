package demo.k8s.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用技能执行器
 *
 * 根据技能目录中的实现文件自动调用相应的脚本
 * 支持 Python (.py) 和 Node.js (.js) 脚本
 */
@Component
public class GenericSkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(GenericSkillExecutor.class);

    // 技能目录 -> 执行器类型映射
    private final Map<String, SkillType> skillTypes = new ConcurrentHashMap<>();

    public enum SkillType {
        PYTHON,     // Python 脚本 (.py)
        NODEJS,     // Node.js 脚本 (.js)
        UNSUPPORTED // 不支持的类型
    }

    /**
     * 检测技能类型
     */
    public SkillType detectSkillType(String skillDirectory) {
        Path dir = Paths.get(skillDirectory);

        // 检查是否有 Python 脚本
        if (Files.exists(dir.resolve("scripts")) ||
            Files.exists(dir.resolve("__main__.py")) ||
            hasPythonFiles(dir)) {
            return SkillType.PYTHON;
        }

        // 检查是否有 Node.js 脚本
        if (Files.exists(dir.resolve("index.js")) ||
            Files.exists(dir.resolve("main.js")) ||
            hasNodeJsFiles(dir)) {
            return SkillType.NODEJS;
        }

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
        SkillType skillType = skillTypes.computeIfAbsent(
            skillDirectory,
            k -> detectSkillType(k)
        );

        return switch (skillType) {
            case PYTHON -> executePythonScript(skillDirectory, args);
            case NODEJS -> executeNodeJsScript(skillDirectory, args);
            case UNSUPPORTED -> "错误：不支持的技能类型，需要 .py 或 .js 脚本";
        };
    }

    /**
     * 执行 Python 脚本
     */
    private String executePythonScript(String skillDirectory, Map<String, Object> args) {
        try {
            Path dir = Paths.get(skillDirectory);

            // 查找 Python 脚本
            Path scriptPath = findPythonScript(dir);
            if (scriptPath == null) {
                return "错误：未找到 Python 脚本";
            }

            // 构建命令
            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(scriptPath.toString());

            // 添加参数
            // 对于 calculator 这样的技能，直接传递 expression 参数
            if (args.containsKey("expression")) {
                command.add((String) args.get("expression"));
            } else if (args.containsKey("path")) {
                command.add((String) args.get("path"));
            } else if (args.containsKey("directory")) {
                command.add((String) args.get("directory"));
            } else if (!args.isEmpty()) {
                // 传递第一个字符串参数
                for (Object value : args.values()) {
                    if (value instanceof String) {
                        command.add((String) value);
                        break;
                    }
                }
            }

            log.info("执行 Python 脚本：{}", command);

            // 执行命令
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(dir.toFile());

            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待进程结束
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return output.toString().trim();
            } else {
                return "脚本执行失败 (退出码：" + exitCode + "): " + output.toString().trim();
            }

        } catch (IOException e) {
            log.error("执行 Python 脚本失败", e);
            return "执行失败：" + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Python 脚本执行被中断", e);
            return "执行被中断";
        }
    }

    /**
     * 执行 Node.js 脚本
     */
    private String executeNodeJsScript(String skillDirectory, Map<String, Object> args) {
        try {
            Path dir = Paths.get(skillDirectory);

            // 查找 Node.js 脚本
            Path scriptPath = findNodeJsScript(dir);
            if (scriptPath == null) {
                return "错误：未找到 Node.js 脚本";
            }

            // 对于 Node.js 脚本，我们需要通过 CLI 或直接调用来执行
            // 有些技能可能是设计为 CLI 工具，有些可能是库

            // 检查是否有 package.json 和 bin 配置
            Path packageJsonPath = dir.resolve("package.json");
            if (Files.exists(packageJsonPath)) {
                String packageJson = Files.readString(packageJsonPath);

                // 如果 package.json 中有 bin 配置，使用 npx 调用
                if (packageJson.contains("\"bin\"")) {
                    List<String> command = new ArrayList<>();
                    command.add("npx");
                    command.add(dir.toAbsolutePath().toString());

                    // 添加参数
                    if (args.containsKey("path")) {
                        command.add((String) args.get("path"));
                    } else if (args.containsKey("directory")) {
                        command.add((String) args.get("directory"));
                    }

                    log.info("执行 Node.js CLI: {}", command);

                    return executeCommand(command, dir.toFile());
                }
            }

            // 直接执行 index.js，通过命令行参数传递参数
            List<String> command = new ArrayList<>();
            command.add("node");
            command.add(scriptPath.toString());

            // 将参数转换为 JSON 传递给脚本
            if (!args.isEmpty()) {
                String jsonArgs = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args);
                command.add(jsonArgs);
            }

            log.info("执行 Node.js 脚本：{}", command);

            return executeCommand(command, dir.toFile());

        } catch (IOException e) {
            log.error("执行 Node.js 脚本失败", e);
            return "执行失败：" + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Node.js 脚本执行被中断", e);
            return "执行被中断";
        }
    }

    /**
     * 执行通用命令
     */
    private String executeCommand(List<String> command, java.io.File workingDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(workingDir);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            return output.toString().trim();
        } else {
            return "命令执行失败 (退出码：" + exitCode + "): " + output.toString().trim();
        }
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
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                log.warn("列出 scripts 目录失败", e);
            }
        }

        // 查找 __main__.py
        Path mainPy = dir.resolve("__main__.py");
        if (Files.exists(mainPy)) {
            return mainPy;
        }

        // 查找根目录的 .py 文件
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".py"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("列出目录失败", e);
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
            return indexJs;
        }

        // 查找 main.js
        Path mainJs = dir.resolve("main.js");
        if (Files.exists(mainJs)) {
            return mainJs;
        }

        // 查找根目录的 .js 文件
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".js"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("列出目录失败", e);
        }

        return null;
    }

    /**
     * 检查目录是否有 Python 文件
     */
    private boolean hasPythonFiles(Path dir) {
        try (var stream = Files.walk(dir, 2)) {
            return stream.anyMatch(p -> p.toString().endsWith(".py"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查目录是否有 Node.js 文件
     */
    private boolean hasNodeJsFiles(Path dir) {
        try (var stream = Files.walk(dir, 2)) {
            return stream.anyMatch(p -> p.toString().endsWith(".js"));
        } catch (IOException e) {
            return false;
        }
    }
}
