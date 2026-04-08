package demo.k8s.agent.tools.local.file;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 文件类工具入参解析。Schema 与 Claude Code 一致使用 snake_case {@code file_path}；
 * 部分模型/客户端会以 camelCase {@code filePath} 传参，执行层统一解析，避免误报 {@code file_path is required}。
 */
public final class FileToolArgs {

    private FileToolArgs() {}

    public static String readFilePath(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        return firstNonBlank(stringOrNull(input.get("file_path")), stringOrNull(input.get("filePath")));
    }

    public static String readFilePath(JsonNode input) {
        if (input == null || !input.isObject()) {
            return null;
        }
        String a = textOrBlank(input, "file_path");
        if (a != null) {
            return a;
        }
        return textOrBlank(input, "filePath");
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
        String s = n.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static String stringOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null) {
            return a;
        }
        return b;
    }
}
