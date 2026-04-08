package demo.k8s.agent.tools.local.file;

import java.util.Map;

/**
 * 目录/搜索根/单一路径类工具（glob、grep、ls、delete、stat、mkdir 等）在 Schema 中主键多为 {@code path}，
 * 与 file_read 等的 {@code file_path} 易混。执行层在此统一解析，顺序：path → file_path → filePath。
 */
public final class FilesystemPathArgs {

    private FilesystemPathArgs() {}

    public static String readPathOrAlias(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        String a = stringOrNull(input.get("path"));
        if (a != null) {
            return a;
        }
        a = stringOrNull(input.get("file_path"));
        if (a != null) {
            return a;
        }
        return stringOrNull(input.get("filePath"));
    }

    private static String stringOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
