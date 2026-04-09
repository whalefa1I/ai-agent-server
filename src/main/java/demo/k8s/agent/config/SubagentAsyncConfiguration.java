package demo.k8s.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 子 Agent 本地运行时使用的轻量线程池（虚拟线程，JDK 21）。
 * <p>
 * 与主对话链路隔离，避免阻塞 HTTP/WebSocket 线程。
 */
@Configuration
public class SubagentAsyncConfiguration {

    public static final String SUBAGENT_TASK_EXECUTOR = "subagentTaskExecutor";

    @Bean(name = SUBAGENT_TASK_EXECUTOR)
    Executor subagentTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
