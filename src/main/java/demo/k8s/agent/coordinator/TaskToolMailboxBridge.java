package demo.k8s.agent.coordinator;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 包装库内 {@link org.springaicommunity.agent.tools.task.TaskTool} 返回，将解析出的 task_id 登记到 {@link InMemoryWorkerMailbox}。
 */
public final class TaskToolMailboxBridge implements ToolCallback {

    private final ToolCallback delegate;
    private final InMemoryWorkerMailbox mailbox;

    public TaskToolMailboxBridge(ToolCallback delegate, InMemoryWorkerMailbox mailbox) {
        this.delegate = delegate;
        this.mailbox = mailbox;
    }

    @Override
    public String call(String toolInput) {
        String out = delegate.call(toolInput);
        register(out);
        return out;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String out = delegate.call(toolInput, toolContext);
        register(out);
        return out;
    }

    private void register(String taskOutput) {
        for (String id : TaskToolOutputParser.extractTaskIds(taskOutput)) {
            if (!id.isBlank()) {
                mailbox.registerKnownTaskId(id);
            }
        }
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }
}
