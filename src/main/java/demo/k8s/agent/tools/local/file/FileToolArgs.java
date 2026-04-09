package demo.k8s.agent.tools.local.file;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 文件类工具路径参数解析。对外 Schema 为 {@code file_path}（Claude Code）；
 * 执行层在此统一接受 file_path、filePath、path，避免模型混用键名导致误报。
 */
public final class FileToolArgs {

    private FileToolArgs() {}

    public static String readFilePath(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        return firstNonBlank(
                stringOrNull(input.get("file_path")),
                stringOrNull(input.get("filePath")),
                stringOrNull(input.get("path")));
    }

    public static String readFilePath(JsonNode input) {
        if (input == null || !input.isObject()) {
            return null;
        }
        String s = textOrBlank(input, "file_path");
        if (s != null) {
            return s;
        }
        s = textOrBlank(input, "filePath");
        if (s != null) {
            return s;
        }
        return textOrBlank(input, "path");
    }

    /** 用于 multi_edit 中单条 edit 对象 */
    public static String readFilePathFromEdit(JsonNode edit) {
        return readFilePath(edit);
    }

    private static String textOrBlank(JsonNode parent, String field) {
        if (!parent.has(field)) {
            return null;
        }
        JsonNode n = parent.get(field);
        if (n == null || !n.isTextual()) {
            return null;
        }
        String t = n.asText("").trim();
        return t.isEmpty() ? null : t;
    }

    private static String stringOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return c;
    }
}
