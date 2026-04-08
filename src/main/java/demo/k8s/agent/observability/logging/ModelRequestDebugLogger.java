package demo.k8s.agent.observability.logging;

import demo.k8s.agent.config.DemoDebugProperties;
import org.slf4j.Logger;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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

        String systemText = firstSystemText(messages);
        log.info("[model-request-debug] turn={} systemChars={} systemPreview={}",
                turnIndex,
                systemText != null ? systemText.length() : 0,
                preview(systemText, prev));

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

        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                String text = textOf(m);
                log.info("[model-request-debug] msg[{}] {} chars={} preview={}",
                        i, m.getClass().getSimpleName(), text.length(), preview(text, prev));
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
