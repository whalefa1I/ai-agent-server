package demo.k8s.agent.tools.remote;

import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 远程工具执行器接口 - 用于通过 HTTP 调用远程工具服务。
 * <p>
 * 未来实现方式：
 * - 通过 REST Client 调用远程服务
 * - 支持异步执行
 * - 支持连接池和重试
 */
public interface RemoteToolExecutor {

    /**
     * 异步执行远程工具
     *
     * @param tool 工具定义
     * @param input 工具输入
     * @param baseUrl 远程服务基础 URL
     * @param authToken 认证 Token（可选）
     * @return 异步结果
     */
    CompletableFuture<LocalToolResult> executeRemote(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            String baseUrl,
            String authToken);

    /**
     * 同步执行远程工具（包装异步）
     */
    default LocalToolResult executeRemoteSync(
            ClaudeLikeTool tool,
            Map<String, Object> input,
            String baseUrl,
            String authToken) {
        return executeRemote(tool, input, baseUrl, authToken).join();
    }
}
