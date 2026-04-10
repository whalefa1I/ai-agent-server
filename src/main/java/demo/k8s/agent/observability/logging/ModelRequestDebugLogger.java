package demo.k8s.agent.observability.logging;

import demo.k8s.agent.config.DemoDebugProperties;
import org.slf4j.Logger;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

/**
 * 在调用 ChatModel 之前记录「发给模型的有效载荷」摘要（服务端日志）。
 * 前端无法拿到完整 tools + system 拼接结果，排查提示词 vs 工具 Schema 请在服务端开启 {@code demo.debug.log-model-request}。
 */
public final class ModelRequestDebugLogger {

    private ModelRequestDebugLogger() {}

    public static void logBeforeModelCall(
            Logger log,
            DemoDebugProperties debug,
            List<ToolCallback> tools,
            List<Message> messages,
            int turnIndex) {

        if (debug == null || !debug.isLogModelRequest()) {
            return;
        }
        int prev = debug.getModelRequestPreviewChars();
        int schemaMax = debug.getModelRequestToolSchemaMaxChars();

        // 只在第0轮（新会话）打印 system prompt，其他轮次跳过
        if (turnIndex == 0) {
            String systemText = firstSystemText(messages);
            log.info("[NEW-SESSION] turn={} systemChars={} systemPreview={}",
                    turnIndex,
                    systemText != null ? systemText.length() : 0,
                    preview(systemText, prev));
        }

        if (tools != null) {
            for (int i = 0; i < tools.size(); i++) {
                ToolCallback tc = tools.get(i);
                try {
                    ToolDefinition def = tc.getToolDefinition();
                    String name = def.name();
                    String schema = def.inputSchema();
                    int len = schema != null ? schema.length() : 0;
                    log.info("[model-request-debug] tool[{}] name={} inputSchemaChars={} inputSchemaPreview={}",
                            i, name, len, preview(schema, schemaMax));
                } catch (Exception e) {
                    log.info("[model-request-debug] tool[{}] (definition unavailable) {}", i, tc);
                }
            }
        }

        // 打印当前轮次的关键消息（只找最后一条 UserMessage 和 AssistantMessage）
        if (messages != null && !messages.isEmpty()) {
            // 倒序查找最近的用户输入和助手响应
            Message lastUser = null;
            Message lastAssistant = null;
            ToolResponseMessage lastToolResponse = null;

            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (lastUser == null && m instanceof UserMessage) {
                    lastUser = m;
                } else if (lastAssistant == null && m instanceof AssistantMessage) {
                    lastAssistant = m;
                } else if (lastToolResponse == null && m instanceof ToolResponseMessage) {
                    lastToolResponse = (ToolResponseMessage) m;
                }
                // 找到关键消息就可以停止了
                if ((lastUser != null || lastAssistant != null) && lastToolResponse != null) {
                    break;
                }
            }

            // 打印当前轮次摘要
            if (lastUser != null) {
                String text = ((UserMessage) lastUser).getText();
                log.info("[Turn-{}] User: {}", turnIndex, preview(text, prev));
            }

            // 打印工具响应（如果有）
            if (lastToolResponse != null) {
                StringBuilder toolSummary = new StringBuilder();
                for (ToolResponseMessage.ToolResponse r : lastToolResponse.getResponses()) {
                    String name = r.name();
                    int len = r.responseData() != null ? r.responseData().length() : 0;
                    // 高亮 spawn_subagent
                    if ("spawn_subagent".equals(name)) {
                        toolSummary.append("[SPAWN:").append(name).append("(").append(len).append("chars)] ");
                    } else {
                        toolSummary.append(name).append("(").append(len).append("chars) ");
                    }
                }
                log.info("[Turn-{}] Tools completed: {}", turnIndex, toolSummary.toString().trim());
            }

            // 打印助手响应（如果有）
            if (lastAssistant instanceof AssistantMessage asm) {
                if (asm.getToolCalls() != null && !asm.getToolCalls().isEmpty()) {
                    StringBuilder toolCalls = new StringBuilder();
                    for (AssistantMessage.ToolCall tc : asm.getToolCalls()) {
                        String name = tc.name();
                        int argsLen = tc.arguments() != null ? tc.arguments().length() : 0;
                        // 高亮 spawn_subagent 调用意图
                        if ("spawn_subagent".equals(name)) {
                            toolCalls.append("[SPAWN:").append(name).append("(").append(argsLen).append("chars)] ");
                        } else {
                            toolCalls.append(name).append("(").append(argsLen).append("chars) ");
                        }
                    }
                    log.info("[Turn-{}] Assistant will call: {}", turnIndex, toolCalls.toString().trim());
                } else {
                    String text = asm.getText();
                    log.info("[Turn-{}] Assistant: {}", turnIndex, preview(text, prev));
                }
            }
        }
    }

    private static String firstSystemText(List<Message> messages) {
        if (messages == null) {
            return null;
        }
        for (Message m : messages) {
            if (m instanceof SystemMessage sm) {
                return sm.getText();
            }
        }
        return null;
    }

    private static String textOf(Message m) {
        if (m instanceof ToolResponseMessage trm) {
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                String d = r.responseData();
                int len = d != null ? d.length() : 0;
                sb.append(r.name()).append(": ").append(len).append(" chars; ");
            }
            return sb.toString();
        }
        if (m instanceof AbstractMessage am) {
            String t = am.getText();
            if (m instanceof AssistantMessage asm && asm.getToolCalls() != null) {
                StringBuilder sb = new StringBuilder(t != null ? t : "");
                for (AssistantMessage.ToolCall tc : asm.getToolCalls()) {
                    String args = tc.arguments();
                    sb.append(" [tool ").append(tc.name()).append(" argsChars=")
                            .append(args != null ? args.length() : 0).append("]");
                }
                return sb.toString();
            }
            return t != null ? t : "";
        }
        return String.valueOf(m);
    }

    private static String preview(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s.replace("\r\n", "\n");
        }
        return s.substring(0, max).replace("\r\n", "\n") + "…";
    }
}
