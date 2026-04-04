package demo.k8s.agent.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * 导出请求
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExportRequest(
        /**
         * 导出格式
         */
        ExportFormat format,

        /**
         * 用户 ID（可选，按用户导出）
         */
        String userId,

        /**
         * 会话 ID（可选，按会话导出）
         */
        String sessionId,

        /**
         * 开始时间（可选）
         */
        Instant startTime,

        /**
         * 结束时间（可选）
         */
        Instant endTime,

        /**
         * 输出目录（可选，默认为 exports/）
         */
        String outputDir,

        /**
         * 是否脱敏数据（默认 true）
         */
        boolean anonymize
) {
    public static ExportRequestBuilder builder() {
        return new ExportRequestBuilder();
    }

    public static class ExportRequestBuilder {
        private ExportFormat format = ExportFormat.ALPACA;
        private String userId;
        private String sessionId;
        private Instant startTime;
        private Instant endTime;
        private String outputDir;
        private boolean anonymize = true;

        public ExportRequestBuilder format(ExportFormat format) {
            this.format = format;
            return this;
        }

        public ExportRequestBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public ExportRequestBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public ExportRequestBuilder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public ExportRequestBuilder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public ExportRequestBuilder outputDir(String outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public ExportRequestBuilder anonymize(boolean anonymize) {
            this.anonymize = anonymize;
            return this;
        }

        public ExportRequest build() {
            return new ExportRequest(format, userId, sessionId, startTime, endTime, outputDir, anonymize);
        }
    }
}
