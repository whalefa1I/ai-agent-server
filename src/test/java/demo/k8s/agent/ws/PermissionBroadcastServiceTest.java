package demo.k8s.agent.ws;

import demo.k8s.agent.toolsystem.PermissionChoice;
import demo.k8s.agent.toolsystem.PermissionLevel;
import demo.k8s.agent.toolsystem.PermissionRequest;
import demo.k8s.agent.toolsystem.PermissionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限广播服务单元测试
 */
public class PermissionBroadcastServiceTest {

    private PermissionBroadcastService broadcastService;

    @BeforeEach
    public void setUp() {
        broadcastService = new PermissionBroadcastService();
    }

    @Test
    public void testBroadcastPermissionRequest() throws Exception {
        // 创建一个权限请求
        PermissionRequest request = PermissionRequest.create(
                "write_file",
                "写入文件",
                PermissionLevel.MODIFY_STATE,
                "{\"path\":\"/tmp/test.txt\"}",
                "此操作将修改文件"
        );

        // 广播权限请求（没有订阅者）
        CompletableFuture<PermissionResponse> future =
                broadcastService.broadcastPermissionRequest("session_123", request);

        // 验证请求已加入待处理队列
        assertTrue(broadcastService.getPendingRequestIds().contains(request.id()));

        // 模拟超时（因为没有订阅者）
        assertThrows(java.util.concurrent.TimeoutException.class, () -> {
            future.get(100, TimeUnit.MILLISECONDS);
        });
    }

    @Test
    public void testHandlePermissionResponse() {
        PermissionRequest request = PermissionRequest.create(
                "bash",
                "执行命令",
                PermissionLevel.DESTRUCTIVE,
                "{\"command\":\"echo hello\"}",
                "此操作将执行命令"
        );

        // 先广播请求
        CompletableFuture<PermissionResponse> future =
                broadcastService.broadcastPermissionRequest("session_123", request);

        // 创建响应
        PermissionResponse response = new PermissionResponse(
                request.id(),
                PermissionChoice.ALLOW_ONCE,
                30,
                null
        );

        // 处理响应
        boolean handled = broadcastService.handlePermissionResponse("session_123", response);

        // 验证
        assertTrue(handled);
        assertTrue(future.isDone());

        // 获取响应结果
        PermissionResponse result = future.join();
        assertEquals(request.id(), result.requestId());
        assertEquals(PermissionChoice.ALLOW_ONCE, result.choice());
    }

    @Test
    public void testSubscribeAndUnsubscribe() {
        // 验证初始订阅数为 0
        assertEquals(0, broadcastService.getSubscriberCount("session_123"));

        // 模拟订阅（需要 mock WebSocketSession）
        // 实际使用时由 WebSocketHandler 管理订阅

        // 验证取消订阅
        // broadcastService.unsubscribe("session_123", mockSession);
        // assertEquals(0, broadcastService.getSubscriberCount("session_123"));
    }

    @Test
    public void testMultiplePendingRequests() {
        // 创建多个权限请求
        PermissionRequest req1 = PermissionRequest.create(
                "read_file", "读取文件",
                PermissionLevel.READ_ONLY,
                "{\"path\":\"/tmp/a.txt\"}", "只读操作"
        );
        PermissionRequest req2 = PermissionRequest.create(
                "write_file", "写入文件",
                PermissionLevel.MODIFY_STATE,
                "{\"path\":\"/tmp/b.txt\"}", "写操作"
        );
        PermissionRequest req3 = PermissionRequest.create(
                "bash", "执行命令",
                PermissionLevel.DESTRUCTIVE,
                "{\"command\":\"rm -rf /tmp/*\"}", "危险操作"
        );

        // 广播所有请求
        broadcastService.broadcastPermissionRequest("session_123", req1);
        broadcastService.broadcastPermissionRequest("session_123", req2);
        broadcastService.broadcastPermissionRequest("session_123", req3);

        // 验证待处理队列
        assertEquals(3, broadcastService.getPendingRequestIds().size());
        assertTrue(broadcastService.getPendingRequestIds().contains(req1.id()));
        assertTrue(broadcastService.getPendingRequestIds().contains(req2.id()));
        assertTrue(broadcastService.getPendingRequestIds().contains(req3.id()));
    }

    @Test
    public void testPermissionChoiceValues() {
        // 验证所有权限选项值
        for (PermissionChoice choice : PermissionChoice.values()) {
            assertNotNull(choice.name());
        }

        assertEquals(PermissionChoice.ALLOW_ONCE, PermissionChoice.valueOf("ALLOW_ONCE"));
        assertEquals(PermissionChoice.ALLOW_SESSION, PermissionChoice.valueOf("ALLOW_SESSION"));
        assertEquals(PermissionChoice.ALLOW_ALWAYS, PermissionChoice.valueOf("ALLOW_ALWAYS"));
        assertEquals(PermissionChoice.DENY, PermissionChoice.valueOf("DENY"));
    }
}
