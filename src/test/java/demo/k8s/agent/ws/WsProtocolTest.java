package demo.k8s.agent.ws;

import demo.k8s.agent.ws.protocol.WsProtocol.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 协议单元测试
 */
public class WsProtocolTest {

    @Test
    public void testRiskLevelEnum() {
        // 测试风险等级枚举
        assertEquals("只读", RiskLevel.READ_ONLY.label);
        assertEquals("📖", RiskLevel.READ_ONLY.icon);
        assertEquals("#22c55e", RiskLevel.READ_ONLY.color);

        assertEquals("修改状态", RiskLevel.MODIFY_STATE.label);
        assertEquals("✏️", RiskLevel.MODIFY_STATE.icon);
        assertEquals("#f59e0b", RiskLevel.MODIFY_STATE.color);

        assertEquals("破坏性", RiskLevel.DESTRUCTIVE.label);
        assertEquals("⚠️", RiskLevel.DESTRUCTIVE.icon);
        assertEquals("#ef4444", RiskLevel.DESTRUCTIVE.color);
    }

    @Test
    public void testRiskLevelFrom() {
        assertEquals(RiskLevel.READ_ONLY, RiskLevel.from("READ_ONLY"));
        assertEquals(RiskLevel.MODIFY_STATE, RiskLevel.from("MODIFY_STATE"));
        assertEquals(RiskLevel.DESTRUCTIVE, RiskLevel.from("DESTRUCTIVE"));
        assertEquals(RiskLevel.MODIFY_STATE, RiskLevel.from("INVALID")); // 无效值返回默认
    }

    @Test
    public void testRiskLevelToJSON() {
        Map<String, String> json = RiskLevel.DESTRUCTIVE.toJSON();
        assertEquals("DESTRUCTIVE", json.get("value"));
        assertEquals("破坏性", json.get("label"));
        assertEquals("⚠️", json.get("icon"));
        assertEquals("#ef4444", json.get("color"));
    }

    @Test
    public void testToolCallMessageCreation() {
        ToolCallMessage msg = ToolCallMessage.create("read_file", "started");

        assertEquals("read_file", msg.toolName);
        assertEquals("started", msg.status);
        assertEquals("Read file", msg.toolDisplayName);  // 首字母大写，其余小写
        assertEquals("📄", msg.icon);
        assertNotNull(msg.toolCallId);
    }

    @Test
    public void testToolCallMessageIcons() {
        assertEquals("⌨️", ToolCallMessage.create("bash", "started").icon);
        assertEquals("📄", ToolCallMessage.create("read_file", "started").icon);
        assertEquals("✏️", ToolCallMessage.create("write_file", "started").icon);
        assertEquals("🔍", ToolCallMessage.create("grep", "started").icon);
        assertEquals("🤖", ToolCallMessage.create("agent", "started").icon);
        assertEquals("🔧", ToolCallMessage.create("unknown_tool", "started").icon);
    }

    @Test
    public void testPermissionRequestMessageCreation() {
        // 模拟一个权限请求
        demo.k8s.agent.toolsystem.PermissionRequest request =
                demo.k8s.agent.toolsystem.PermissionRequest.create(
                        "write_file",
                        "写入文件到指定路径",
                        demo.k8s.agent.toolsystem.PermissionLevel.MODIFY_STATE,
                        "{\"path\":\"/tmp/test.txt\",\"content\":\"hello\"}",
                        "此操作将修改文件系统状态"
                );

        PermissionRequestMessage msg = new PermissionRequestMessage(request);

        assertEquals(request.id(), msg.id);
        assertEquals("write_file", msg.toolName);
        assertEquals("Write file", msg.toolDisplayName);  // 首字母大写
        assertEquals("✏️", msg.icon);
        assertEquals("MODIFY_STATE", msg.level);
        assertEquals("修改状态", msg.levelLabel);
        assertNotNull(msg.permissionOptions);
        assertEquals(4, msg.permissionOptions.size());
    }

    @Test
    public void testPermissionOptionValues() {
        demo.k8s.agent.toolsystem.PermissionRequest request =
                demo.k8s.agent.toolsystem.PermissionRequest.create(
                        "bash",
                        "执行 shell 命令",
                        demo.k8s.agent.toolsystem.PermissionLevel.DESTRUCTIVE,
                        "{\"command\":\"echo hello\"}",
                        "此操作将执行系统命令"
                );

        PermissionRequestMessage msg = new PermissionRequestMessage(request);

        // 验证默认权限选项
        var options = msg.permissionOptions;
        assertEquals("ALLOW_ONCE", options.get(0).value);
        assertEquals("本次允许", options.get(0).label);
        assertEquals("default", options.get(0).style);
        assertEquals("1", options.get(0).shortcut);

        assertEquals("DENY", options.get(3).value);
        assertEquals("拒绝", options.get(3).label);
        assertEquals("danger", options.get(3).style);
    }

    @Test
    public void testConnectedMessage() {
        ConnectedMessage msg = new ConnectedMessage("test-session-123");

        assertEquals("CONNECTED", msg.getType().name());
        assertEquals("test-session-123", msg.sessionId);
        assertEquals("0.1.0", msg.serverVersion);  // 服务端版本
    }

    @Test
    public void testUserMessage() {
        UserMessage msg = new UserMessage("请帮我读取文件");

        assertEquals("USER_MESSAGE", msg.getType().name());
        assertEquals("请帮我读取文件", msg.content);
        assertNotNull(msg.requestId);
        assertNotNull(msg.timestamp);
    }

    @Test
    public void testPermissionResponseMessage() {
        PermissionResponseMessage msg = new PermissionResponseMessage("perm_123", "ALLOW_ONCE");

        assertEquals("PERMISSION_RESPONSE", msg.getType().name());
        assertEquals("perm_123", msg.requestId);
        assertEquals("ALLOW_ONCE", msg.choice);
    }
}
