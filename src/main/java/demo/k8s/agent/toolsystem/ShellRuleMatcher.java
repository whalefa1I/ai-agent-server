package demo.k8s.agent.toolsystem;

import java.util.regex.Pattern;

/**
 * Shell 权限规则匹配工具，与 Claude Code 的 {@code shellRuleMatching.ts} 对齐。
 * <p>
 * 支持三种规则类型：
 * <ul>
 *   <li>精确匹配：{@code Bash(ls -la)} - 完全匹配命令</li>
 *   <li>前缀匹配：{@code Bash(npm:*)} - 匹配以特定前缀开头的命令</li>
 *   <li>通配符匹配：{@code Bash(cd *)} - 使用 * 通配符匹配任意字符序列</li>
 * </ul>
 */
public class ShellRuleMatcher {

    private ShellRuleMatcher() {
        // 工具类，防止实例化
    }

    /**
     * 解析规则字符串，返回规则类型和前缀/模式
     *
     * @param rule 规则字符串，如 {@code "ls -la"}, {@code "npm:*"}, {@code "cd *"}
     * @return 解析后的规则对象
     */
    public static PermissionRule parseRule(String rule) {
        if (rule == null || rule.isBlank()) {
            return new PermissionRule(PermissionRuleType.EXACT, rule, null);
        }

        String trimmed = rule.trim();

        // 检查前缀语法（legacy :* 语法）
        if (trimmed.endsWith(":*")) {
            String prefix = trimmed.substring(0, trimmed.length() - 2);
            return new PermissionRule(PermissionRuleType.PREFIX, rule, prefix);
        }

        // 检查是否包含通配符
        if (hasWildcards(trimmed)) {
            return new PermissionRule(PermissionRuleType.WILDCARD, rule, trimmed);
        }

        // 默认为精确匹配
        return new PermissionRule(PermissionRuleType.EXACT, rule, trimmed);
    }

    /**
     * 检查模式是否包含未转义的通配符
     *
     * @param pattern 模式字符串
     * @return true = 包含通配符，false = 不包含或使用 legacy 前缀语法
     */
    public static boolean hasWildcards(String pattern) {
        // 如果以:* 结尾，是 legacy 前缀语法，不是通配符
        if (pattern.endsWith(":*")) {
            return false;
        }

        // 检查未转义的 *
        for (int i = 0; i < pattern.length(); i++) {
            if (pattern.charAt(i) == '*') {
                // 计算前面的反斜杠数量
                int backslashCount = 0;
                int j = i - 1;
                while (j >= 0 && pattern.charAt(j) == '\\') {
                    backslashCount++;
                    j--;
                }
                // 如果反斜杠数量为偶数（包括 0），则 * 未被转义
                if (backslashCount % 2 == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 匹配命令与通配符模式
     *
     * @param pattern 模式字符串（可包含 * 通配符）
     * @param command 待匹配的命令
     * @return true = 匹配成功，false = 匹配失败
     */
    public static boolean matchWildcard(String pattern, String command) {
        return matchWildcard(pattern, command, false);
    }

    /**
     * 匹配命令与通配符模式（支持大小写敏感选项）
     *
     * @param pattern 模式字符串（可包含 * 通配符，\* 表示字面量 *, \\ 表示字面量 \）
     * @param command 待匹配的命令
     * @param caseInsensitive 是否忽略大小写
     * @return true = 匹配成功，false = 匹配失败
     */
    public static boolean matchWildcard(String pattern, String command, boolean caseInsensitive) {
        // 处理转义序列
        StringBuilder processed = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            if (pattern.charAt(i) == '\\') {
                if (i + 1 < pattern.length()) {
                    char next = pattern.charAt(i + 1);
                    if (next == '*') {
                        // \* 转义为字面量 *
                        processed.append('*');
                        i += 2;
                        continue;
                    } else if (next == '\\') {
                        // \\ 转义为字面量 \
                        processed.append('\\');
                        i += 2;
                        continue;
                    }
                }
                // 单独的 \ 保持不变
                processed.append('\\');
                i++;
            } else {
                processed.append(pattern.charAt(i));
                i++;
            }
        }

        // 将 * 转换为正则表达式的 .*
        String regex = patternToRegex(processed.toString());

        // 编译正则
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        Pattern compiled = Pattern.compile(regex, flags);

        return compiled.matcher(command).matches();
    }

    /**
     * 将通配符模式转换为正则表达式
     */
    private static String patternToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        regex.append('^');

        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '.':
                case '+':
                case '?':
                case '{':
                case '}':
                case '(':
                case ')':
                case '[':
                case ']':
                case '|':
                    regex.append('\\').append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }

        regex.append('$');
        return regex.toString();
    }

    /**
     * 从命令中提取前缀（用于前缀匹配规则）
     *
     * @param command 命令字符串
     * @return 命令前缀（前两个词，如 "git commit"），如果无法提取则返回 null
     */
    public static String extractCommandPrefix(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }

        String[] tokens = command.trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        // 第二个词必须看起来像子命令（小写字母数字）
        String subcmd = tokens[1];
        if (!subcmd.matches("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")) {
            return null;
        }

        return tokens[0] + " " + tokens[1];
    }

    /**
     * 为命令生成建议的权限规则
     *
     * @param command 命令字符串
     * @param maxSuggestions 最大建议数量
     * @return 建议的规则列表
     */
    public static java.util.List<PermissionRule> generateSuggestions(String command, int maxSuggestions) {
        java.util.List<PermissionRule> suggestions = new java.util.ArrayList<>();

        // 1. 精确匹配总是添加
        suggestions.add(new PermissionRule(PermissionRuleType.EXACT, command, command));

        // 2. 尝试提取前缀
        String prefix = extractCommandPrefix(command);
        if (prefix != null && suggestions.size() < maxSuggestions) {
            String prefixRule = prefix + ":*";
            suggestions.add(new PermissionRule(PermissionRuleType.PREFIX, prefixRule, prefix));
        }

        // 3. 尝试生成通配符规则（主命令 + *）
        if (command.contains(" ") && suggestions.size() < maxSuggestions) {
            String mainCommand = command.split("\\s+")[0];
            String wildcardRule = mainCommand + " *";
            if (!hasWildcards(wildcardRule) || matchWildcard(wildcardRule, command)) {
                suggestions.add(new PermissionRule(PermissionRuleType.WILDCARD, wildcardRule, wildcardRule));
            }
        }

        return suggestions;
    }

    /**
     * 检查命令是否匹配任何规则
     *
     * @param command 命令字符串
     * @param rules 规则列表
     * @return true = 匹配任何规则，false = 不匹配任何规则
     */
    public static boolean matchesAny(String command, java.util.List<PermissionRule> rules) {
        for (PermissionRule rule : rules) {
            if (matches(command, rule)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查命令是否匹配单个规则
     *
     * @param command 命令字符串
     * @param rule 规则
     * @return true = 匹配，false = 不匹配
     */
    public static boolean matches(String command, PermissionRule rule) {
        if (command == null || rule == null) {
            return false;
        }

        String trimmedCommand = command.trim();

        switch (rule.type()) {
            case EXACT:
                return trimmedCommand.equals(rule.value());
            case PREFIX:
                return trimmedCommand.startsWith(rule.prefix() + " ") ||
                       trimmedCommand.equals(rule.prefix());
            case WILDCARD:
                return matchWildcard(rule.value(), trimmedCommand);
            default:
                return false;
        }
    }
}
