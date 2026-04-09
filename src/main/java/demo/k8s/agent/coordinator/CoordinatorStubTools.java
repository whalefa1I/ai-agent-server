package demo.k8s.agent.coordinator;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * Coordinator 专用占位工具：与 Claude Code 中 SendMessage / TaskStop 名称对齐，便于后续接入真实任务总线。
 */
public final class CoordinatorStubTools {

    private CoordinatorStubTools() {}

    public static ToolCallback sendMessageTool(InMemoryWorkerMailbox mailbox) {
        return FunctionToolCallback.builder(
                        "SendMessage",
                        (SendMessageInput in) -> {
                            String tid = in.task_id() == null ? "" : in.task_id();
                            String msg = in.message() == null ? "" : in.message();
                            mailbox.enqueueMessage(tid, msg);
                            String base =
                                    "已记录 SendMessage（演示）：task_id="
                                            + tid
                                            + "。当前为内存占位，未推送到真实 worker 进程；后续可对接 TaskRepository / 消息总线。";
                            if (!tid.isBlank() && !mailbox.isKnownTaskId(tid)) {
                                base +=
                                        " 提示：该 task_id 尚未由本会话 Task 输出登记，可能拼写错误或尚未创建对应后台任务。";
                            }
                            return base;
                        })
                .description(
                        "向指定 task_id 的 worker 发送跟进指令（协调者星型拓扑）。演示实现仅写入内存队列。")
                .inputType(SendMessageInput.class)
                .build();
    }

    public static ToolCallback taskStopTool(InMemoryWorkerMailbox mailbox) {
        return FunctionToolCallback.builder(
                        "TaskStop",
                        (TaskStopInput in) -> {
                            String tid = in.task_id() == null ? "" : in.task_id();
                            mailbox.requestCancel(tid);
                            String base =
                                    "已记录 TaskStop 请求（演示）：task_id="
                                            + tid
                                            + "。当前为内存占位，未中断库内子 Agent 执行；后续可对接取消令牌。";
                            if (!tid.isBlank() && !mailbox.isKnownTaskId(tid)) {
                                base +=
                                        " 提示：该 task_id 尚未由本会话 Task 输出登记，可能拼写错误或尚未创建对应后台任务。";
                            }
                            return base;
                        })
                .description("请求终止指定 task_id 的 worker（演示占位）。")
                .inputType(TaskStopInput.class)
                .build();
    }
}
