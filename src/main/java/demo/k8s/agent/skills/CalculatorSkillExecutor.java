package demo.k8s.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 计算器技能执行器
 *
 * 执行 calc.py 脚本进行数学计算
 */
public class CalculatorSkillExecutor implements SkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(CalculatorSkillExecutor.class);

    private final String skillDirectory;

    public CalculatorSkillExecutor(String skillDirectory) {
        this.skillDirectory = skillDirectory;
    }

    @Override
    public String getSkillName() {
        return "calculator";
    }

    @Override
    public String execute(Map<String, Object> args) {
        // 支持两种参数形式：
        // 1. {"expression": "2 + 3 * 4"}
        // 2. {"command": "calculate", "args": {"expression": "2 + 3 * 4"}}

        String expression = null;

        if (args.containsKey("expression")) {
            expression = (String) args.get("expression");
        } else if (args.containsKey("command") && "calculate".equals(args.get("command"))) {
            Object cmdArgs = args.get("args");
            if (cmdArgs instanceof Map) {
                expression = (String) ((Map<?, ?>) cmdArgs).get("expression");
            }
        } else if (args.containsKey("command")) {
            // 兼容旧模式，command 直接作为表达式
            expression = (String) args.get("command");
        }

        if (expression == null || expression.isBlank()) {
            return "错误：缺少 expression 参数";
        }

        // 查找 calc.py 脚本
        Path calcScript = Paths.get(skillDirectory, "scripts", "calc.py");
        if (!Files.exists(calcScript)) {
            return "错误：找不到 calc.py 脚本：" + calcScript;
        }

        try {
            // 构建命令
            List<String> command = List.of("python", calcScript.toString(), expression);

            log.info("执行计算器命令：{}", command);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

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
                return "计算失败 (退出码：" + exitCode + "): " + output.toString().trim();
            }

        } catch (IOException e) {
            log.error("执行计算器失败", e);
            return "执行失败：" + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("计算器执行被中断", e);
            return "计算被中断";
        }
    }
}
