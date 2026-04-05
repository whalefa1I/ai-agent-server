package demo.k8s.agent.commandsystem;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 斜杠命令执行结果
 * 与 claude-code 的 SlashCommandResult 对齐
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SlashCommandResult.Text.class, name = "text"),
    @JsonSubTypes.Type(value = SlashCommandResult.Error.class, name = "error"),
    @JsonSubTypes.Type(value = SlashCommandResult.Confirmation.class, name = "confirmation"),
    @JsonSubTypes.Type(value = SlashCommandResult.PlanMode.class, name = "plan_mode")
})
public sealed interface SlashCommandResult
    permits SlashCommandResult.Text, SlashCommandResult.Error,
            SlashCommandResult.Confirmation, SlashCommandResult.PlanMode {

    /**
     * 文本结果 - 返回消息给用户
     */
    record Text(String content) implements SlashCommandResult {
        public static Text of(String content) {
            return new Text(content);
        }
    }

    /**
     * 错误结果
     */
    record Error(String message, String code) implements SlashCommandResult {
        public static Error of(String message) {
            return new Error(message, "COMMAND_ERROR");
        }

        public static Error of(String message, String code) {
            return new Error(message, code);
        }
    }

    /**
     * 需要确认的结果
     */
    record Confirmation(
        String title,
        String message,
        String confirmLabel,
        String cancelLabel,
        Object payload
    ) implements SlashCommandResult {
        public static Confirmation of(String title, String message, Object payload) {
            return new Confirmation(title, message, "确认", "取消", payload);
        }
    }

    /**
     * 计划模式结果
     */
    record PlanMode(
        boolean enabled,
        String planContent,
        String planFilePath
    ) implements SlashCommandResult {
        public static PlanMode enabled(String planContent, String planFilePath) {
            return new PlanMode(true, planContent, planFilePath);
        }

        public static PlanMode disabled() {
            return new PlanMode(false, null, null);
        }
    }
}
