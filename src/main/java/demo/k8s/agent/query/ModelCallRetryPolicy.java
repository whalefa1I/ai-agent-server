package demo.k8s.agent.query;

import java.util.Locale;
import java.util.function.Supplier;

import demo.k8s.agent.config.DemoQueryProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * 对齐 {@code withRetry.ts} 的<strong>子集</strong>：对模型 HTTP/传输层可重试错误做有限次退避。
 * 与 TS 中「query 内层 fallback 模型循环」不同层；此处仅包装单次 {@link org.springframework.ai.chat.model.ChatModel#call}。
 */
@Component
public class ModelCallRetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(ModelCallRetryPolicy.class);

    private final DemoQueryProperties props;

    public ModelCallRetryPolicy(DemoQueryProperties props) {
        this.props = props;
    }

    public ChatResponse call(Supplier<ChatResponse> supplier) {
        int max = props.getRetry().getMaxAttempts();
        long base = props.getRetry().getBaseDelayMs();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                last = e;
                if (attempt == max || !isRetriable(e)) {
                    throw e;
                }
                long sleep = Math.min(32_000L, base * (1L << (attempt - 1)));
                log.warn("Model call failed (attempt {}/{}), retrying after {}ms: {}", attempt, max, sleep, e.toString());
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last != null ? last : new IllegalStateException("unreachable");
    }

    private static boolean isRetriable(RuntimeException e) {
        String msg = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        String cls = e.getClass().getName().toLowerCase(Locale.ROOT);
        if (msg.contains("429") || msg.contains("503") || msg.contains("529") || msg.contains("timeout")) {
            return true;
        }
        if (msg.contains("connection") || msg.contains("reset")) {
            return true;
        }
        return cls.contains("timeout") || cls.contains("retry");
    }
}
