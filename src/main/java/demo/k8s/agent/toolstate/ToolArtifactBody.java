package demo.k8s.agent.toolstate;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * ToolArtifact Body - 工具状态详情
 *
 * 根据工具状态不同，包含不同的字段：
 * - todo: todo 描述
 * - plan: 计划步骤列表
 * - pending_confirmation: 输入参数和确认请求
 * - executing: 输入参数和执行进度
 * - completed: 输入参数和输出结果
 * - failed: 输入参数和错误信息
 */
public class ToolArtifactBody {
    @JsonProperty("todo")
    private String todo;

    @JsonProperty("plan")
    private List<String> plan;

    @JsonProperty("input")
    private Map<String, Object> input;

    @JsonProperty("output")
    private Object output;

    @JsonProperty("error")
    private String error;

    @JsonProperty("progress")
    private String progress;

    @JsonProperty("confirmation")
    private Confirmation confirmation;

    @JsonProperty("version")
    private int version;

    // Confirmation 内部类
    public static class Confirmation {
        @JsonProperty("requested")
        private boolean requested;

        @JsonProperty("granted")
        private Boolean granted;

        public boolean isRequested() { return requested; }
        public void setRequested(boolean requested) { this.requested = requested; }

        public Boolean isGranted() { return granted; }
        public void setGranted(Boolean granted) { this.granted = granted; }
    }

    // Getters and Setters
    public String getTodo() { return todo; }
    public void setTodo(String todo) { this.todo = todo; }

    public List<String> getPlan() { return plan; }
    public void setPlan(List<String> plan) { this.plan = plan; }

    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }

    public Object getOutput() { return output; }
    public void setOutput(Object output) { this.output = output; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getProgress() { return progress; }
    public void setProgress(String progress) { this.progress = progress; }

    public Confirmation getConfirmation() { return confirmation; }
    public void setConfirmation(Confirmation confirmation) { this.confirmation = confirmation; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
