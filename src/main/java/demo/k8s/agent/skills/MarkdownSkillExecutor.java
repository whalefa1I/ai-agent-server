package demo.k8s.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 技能执行器
 *
 * 提供 Markdown 验证、格式化等功能
 * 基于 SKILL.md 中的最佳实践规则
 */
public class MarkdownSkillExecutor implements SkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(MarkdownSkillExecutor.class);

    @Override
    public String getSkillName() {
        return "markdown";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String action = (String) args.get("action");
        String content = (String) args.get("content");

        if (action == null || content == null) {
            return "错误：缺少必需参数 action 和 content";
        }

        try {
            return switch (action) {
                case "validate" -> validateMarkdown(content);
                case "check" -> checkMarkdown(content);
                case "fix" -> fixMarkdownIssues(content);
                default -> "错误：未知的 action: " + action;
            };
        } catch (Exception e) {
            log.error("Markdown 操作失败", e);
            return "执行失败：" + e.getMessage();
        }
    }

    /**
     * 验证 Markdown 常见问题
     */
    private String validateMarkdown(String content) {
        StringBuilder issues = new StringBuilder();

        // 检查空白行问题
        checkWhitespaceIssues(content, issues);

        // 检查链接和图片
        checkLinksAndImages(content, issues);

        // 检查代码块
        checkCodeBlocks(content, issues);

        // 检查表格
        checkTables(content, issues);

        if (issues.length() == 0) {
            return "Markdown 格式良好，未发现常见问题";
        }

        return "发现以下问题:\n" + issues.toString();
    }

    /**
     * 检查 Markdown 并返回报告
     */
    private String checkMarkdown(String content) {
        StringBuilder report = new StringBuilder();
        report.append("Markdown 检查报告:\n\n");

        // 统计
        int lines = content.split("\n").length;
        int codeBlocks = countMatches(content, "```");
        int links = countMatches(content, "\\[.*?\\]\\(.*?\\)");
        int images = countMatches(content, "!\\[.*?\\]\\(.*?\\)");
        int headers = countMatches(content, "^#+\\s");

        report.append("行数：").append(lines).append("\n");
        report.append("代码块：").append(codeBlocks / 2).append(" 个\n");
        report.append("链接：").append(links).append(" 个\n");
        report.append("图片：").append(images).append(" 个\n");
        report.append("标题：").append(headers).append(" 个\n");

        return report.toString();
    }

    /**
     * 修复常见 Markdown 问题
     */
    private String fixMarkdownIssues(String content) {
        String fixed = content;

        // 修复连续空格
        fixed = fixed.replaceAll("[ ]{2,}", " ");

        // 修复空行前的空格（使用多行模式）
        fixed = fixed.replaceAll("(?m)^[ ]+$", "");

        return "修复后的内容:\n" + fixed;
    }

    private void checkWhitespaceIssues(String content, StringBuilder issues) {
        // 检查空白行后是否紧跟列表
        if (content.matches("(?s).*[^\n\\s]\n-.*")) {
            issues.append("- 列表前可能需要空行\n");
        }

        // 检查行尾连续空格（可能用于换行）
        Pattern pattern = Pattern.compile("  +$", Pattern.MULTILINE);
        if (pattern.matcher(content).find()) {
            issues.append("- 发现行尾连续空格（用于硬换行）\n");
        }
    }

    private void checkLinksAndImages(String content, StringBuilder issues) {
        // 检查链接中的括号
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]*\\([^)]*\\)[^)]*)\\)");
        if (linkPattern.matcher(content).find()) {
            issues.append("- 链接 URL 中包含未转义的括号\n");
        }

        // 检查空 alt 文本
        if (content.contains("![](")) {
            issues.append("- 发现空 alt 文本的图片\n");
        }
    }

    private void checkCodeBlocks(String content, StringBuilder issues) {
        // 检查代码块是否成对
        int backticks = countMatches(content, "```");
        if (backticks % 2 != 0) {
            issues.append("- 代码块标记未闭合（奇数个 ```）\n");
        }

        // 检查代码块内的反引号
        if (content.contains("```\n```")) {
            issues.append("- 可能存在嵌套代码块问题\n");
        }
    }

    private void checkTables(String content, StringBuilder issues) {
        // 检查表格分隔行
        Pattern tableSeparator = Pattern.compile("^\\|?[ \\t]*:?-+:?[ \\t]*\\|", Pattern.MULTILINE);
        if (tableSeparator.matcher(content).find()) {
            // 检查表格前后是否有空行
            if (!content.matches("(?s).*\\n\\n\\|.*")) {
                issues.append("- 表格前可能需要空行\n");
            }
        }
    }

    private int countMatches(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int countMatches(String text, String regex, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
