package demo.k8s.agent.tools.local;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具执行结果（本地/远程通用）。
 */
public class LocalToolResult {
    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 执行结果内容
     */
    private String content;

    /**
     * 错误信息（失败时）
     */
    private String error;

    /**
     * 工具输出元数据（可选）
     */
    private JsonNode metadata;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * 执行位置（local / remote）
     */
    private String executionLocation;

    public LocalToolResult() {}

    public LocalToolResult(boolean success, String content, String error, JsonNode metadata, long durationMs, String executionLocation) {
        this.success = success;
        this.content = content;
        this.error = error;
        this.metadata = metadata;
        this.durationMs = durationMs;
        this.executionLocation = executionLocation;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public JsonNode getMetadata() { return metadata; }
    public void setMetadata(JsonNode metadata) { this.metadata = metadata; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getExecutionLocation() { return executionLocation; }
    public void setExecutionLocation(String executionLocation) { this.executionLocation = executionLocation; }

    /**
     * Builder class
     */
    public static class LocalToolResultBuilder {
        private boolean success;
        private String content;
        private String error;
        private JsonNode metadata;
        private long durationMs;
        private String executionLocation;

        public LocalToolResultBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public LocalToolResultBuilder content(String content) {
            this.content = content;
            return this;
        }

        public LocalToolResultBuilder error(String error) {
            this.error = error;
            return this;
        }

        public LocalToolResultBuilder metadata(JsonNode metadata) {
            this.metadata = metadata;
            return this;
        }

        public LocalToolResultBuilder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public LocalToolResultBuilder executionLocation(String executionLocation) {
            this.executionLocation = executionLocation;
            return this;
        }

        public LocalToolResult build() {
            // 如果 metadata 为 null，初始化为 empty object，避免 Spring AI 处理时出现 NullPointerException
            if (this.metadata == null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    this.metadata = mapper.createObjectNode();
                } catch (Exception e) {
                    // 忽略转换错误
                }
            }
            return new LocalToolResult(success, content, error, metadata, durationMs, executionLocation);
        }
    }

    public static LocalToolResultBuilder builder() {
        return new LocalToolResultBuilder();
    }

    /**
     * 创建成功结果
     */
    public static LocalToolResult success(String content) {
        LocalToolResult result = new LocalToolResult();
        result.success = true;
        result.content = content;
        result.executionLocation = "local";
        result.durationMs = 0;
        // 初始化 metadata 为 empty object，避免 Spring AI 处理时出现 NullPointerException
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            result.metadata = mapper.createObjectNode();
        } catch (Exception e) {
            // 忽略转换错误
        }
        return result;
    }

    /**
     * 创建成功结果（带元数据）
     */
    public static LocalToolResult success(String content, Object metadata) {
        LocalToolResult result = new LocalToolResult();
        result.success = true;
        result.content = content;
        result.executionLocation = "local";
        result.durationMs = 0;
        // 将 metadata 转换为 JsonNode
        if (metadata != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                result.metadata = mapper.valueToTree(metadata);
            } catch (Exception e) {
                // 忽略转换错误
            }
        }
        return result;
    }

    /**
     * 创建失败结果
     */
    public static LocalToolResult error(String errorMessage) {
        LocalToolResult result = new LocalToolResult();
        result.success = false;
        result.error = errorMessage;
        result.executionLocation = "local";
        result.durationMs = 0;
        return result;
    }
}
