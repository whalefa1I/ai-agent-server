package demo.k8s.agent.plugin.hook.event;

import java.time.Instant;

/**
 * 模型调用事件数据
 */
public class ModelCallEvent {

    private final String modelName;
    private final String prompt;
    private final String response;
    private final int inputTokens;
    private final int outputTokens;
    private final Instant timestamp;
    private final long durationMs;

    public ModelCallEvent(String modelName, String prompt, String response, int inputTokens, int outputTokens, long durationMs) {
        this.modelName = modelName;
        this.prompt = prompt;
        this.response = response;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.timestamp = Instant.now();
        this.durationMs = durationMs;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getResponse() {
        return response;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
