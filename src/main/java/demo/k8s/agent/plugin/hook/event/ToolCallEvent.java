package demo.k8s.agent.plugin.hook.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 工具调用事件数据
 */
public class ToolCallEvent {

    private final String toolName;
    private final JsonNode input;
    private final String output;
    private final Instant timestamp;
    private final boolean successful;
    private final long durationMs;

    public ToolCallEvent(String toolName, JsonNode input, String output, boolean successful, long durationMs) {
        this.toolName = toolName;
        this.input = input;
        this.output = output;
        this.timestamp = Instant.now();
        this.successful = successful;
        this.durationMs = durationMs;
    }

    public String getToolName() {
        return toolName;
    }

    public JsonNode getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
