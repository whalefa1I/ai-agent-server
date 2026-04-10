package demo.k8s.agent.config;

import demo.k8s.agent.apikey.ApiKeyAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final QuotaCheckInterceptor quotaCheckInterceptor;
    private final ApiKeyAuthFilter apiKeyAuthFilter;

    public WebConfig(QuotaCheckInterceptor quotaCheckInterceptor, ApiKeyAuthFilter apiKeyAuthFilter) {
        this.quotaCheckInterceptor = quotaCheckInterceptor;
        this.apiKeyAuthFilter = apiKeyAuthFilter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(quotaCheckInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/logs/**",      // 日志查询 API 不需要配额检查
                        "/api/permissions/**", // 权限请求不需要配额检查
                        "/api/channels/**",   // 频道 webhook 不需要配额检查
                        "/api/ops/**"         // 运维探针（自带 X-Ops-Secret）
                );
    }

    /**
     * 注册 API Key 认证过滤器
     */
    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(apiKeyAuthFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // 在 Spring Security 之前执行
        return registration;
    }
}
