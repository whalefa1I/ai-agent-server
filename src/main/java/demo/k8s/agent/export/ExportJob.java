package demo.k8s.agent.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 导出任务
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportJob {

    private String id;
    private ExportRequest request;
    private ExportStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String outputFile;
    private String error;
    private int recordCount;

    public ExportJob() {
    }

    public static ExportJobBuilder builder() {
        return new ExportJobBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ExportRequest getRequest() {
        return request;
    }

    public void setRequest(ExportRequest request) {
        this.request = request;
    }

    public ExportStatus getStatus() {
        return status;
    }

    public void setStatus(ExportStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public static class ExportJobBuilder {
        private final ExportJob job = new ExportJob();

        public ExportJobBuilder id(String id) {
            job.setId(id);
            return this;
        }

        public ExportJobBuilder request(ExportRequest request) {
            job.setRequest(request);
            return this;
        }

        public ExportJobBuilder status(ExportStatus status) {
            job.setStatus(status);
            return this;
        }

        public ExportJobBuilder createdAt(Instant createdAt) {
            job.setCreatedAt(createdAt);
            return this;
        }

        public ExportJobBuilder startedAt(Instant startedAt) {
            job.setStartedAt(startedAt);
            return this;
        }

        public ExportJobBuilder completedAt(Instant completedAt) {
            job.setCompletedAt(completedAt);
            return this;
        }

        public ExportJobBuilder outputFile(String outputFile) {
            job.setOutputFile(outputFile);
            return this;
        }

        public ExportJobBuilder error(String error) {
            job.setError(error);
            return this;
        }

        public ExportJobBuilder recordCount(int recordCount) {
            job.setRecordCount(recordCount);
            return this;
        }

        public ExportJob build() {
            return job;
        }
    }
}
