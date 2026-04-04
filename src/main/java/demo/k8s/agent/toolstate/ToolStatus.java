package demo.k8s.agent.toolstate;

import java.util.Map;

/**
 * 工具状态枚举
 */
public enum ToolStatus {
    TODO("todo"),
    PLAN("plan"),
    PENDING_CONFIRMATION("pending_confirmation"),
    EXECUTING("executing"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    ToolStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
