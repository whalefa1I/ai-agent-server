package demo.k8s.agent.coordinator;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 {@link org.springaicommunity.agent.tools.task.TaskTool} 返回文本中尽力提取 task_id（同步/后台格式不一致，故多模式匹配）。
 */
public final class TaskToolOutputParser {

    private static final Pattern LINE_TASK_ID =
            Pattern.compile("(?im)^task_id\\s*:\\s*(\\S+)");

    private static final Pattern BACKGROUND_ID =
            Pattern.compile("(?i)Background task started with ID:\\s*(\\S+)");

    private static final Pattern TASK_ID_ATTR = Pattern.compile("(?i)task_id\\s*=\\s*['\"]([^'\"]+)['\"]");

    private TaskToolOutputParser() {}

    /** 提取 0..n 个候选 id（去重保序）。 */
    public static Set<String> extractTaskIds(String taskToolOutput) {
        Set<String> out = new LinkedHashSet<>();
        if (taskToolOutput == null || taskToolOutput.isBlank()) {
            return out;
        }
        Matcher m1 = LINE_TASK_ID.matcher(taskToolOutput);
        while (m1.find()) {
            out.add(trim(m1.group(1)));
        }
        Matcher m2 = BACKGROUND_ID.matcher(taskToolOutput);
        while (m2.find()) {
            out.add(trim(m2.group(1)));
        }
        Matcher m3 = TASK_ID_ATTR.matcher(taskToolOutput);
        while (m3.find()) {
            out.add(trim(m3.group(1)));
        }
        return out;
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.endsWith(".") || t.endsWith(",") || t.endsWith(")")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}
