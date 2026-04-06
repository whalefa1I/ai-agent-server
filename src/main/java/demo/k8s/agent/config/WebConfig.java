package demo.k8s.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final QuotaCheckInterceptor quotaCheckInterceptor;

    public WebConfig(QuotaCheckInterceptor quotaCheckInterceptor) {
        this.quotaCheckInterceptor = quotaCheckInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(quotaCheckInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/logs/**",      // 日志查询 API 不需要配额检查
                        "/api/permissions/**", // 权限请求不需要配额检查
                        "/api/channels/**"    // 频道 webhook 不需要配额检查
                );
    }
}
