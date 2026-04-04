package demo.k8s.agent.observability.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路追踪拦截器
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // 从请求头获取或生成 TraceID
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.generateTraceId();
        }

        // 生成 SpanID
        String spanId = TraceContext.generateSpanId();

        // 初始化上下文
        TraceContext.init(traceId, spanId);

        try {
            // 设置响应头
            response.setHeader("X-Trace-Id", traceId);
            response.setHeader("X-Span-Id", spanId);

            filterChain.doFilter(request, response);
        } finally {
            // 清理上下文
            TraceContext.clear();
        }
    }
}
