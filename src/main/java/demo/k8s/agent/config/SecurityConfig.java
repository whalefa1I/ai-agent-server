package demo.k8s.agent.config;

import demo.k8s.agent.apikey.ApiKeyAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 *
 * 配置说明：
 * - 放行公开 API 端点（如 /api/v1/** 用于前端 Happy Protocol）
 * - 保护管理端点（如 /api/scheduler/**, /api/admin/**）需要 API Key 认证
 * - 禁用 CSRF（API 服务使用 Token 认证）
 * - 启用 CORS 支持跨域请求
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;

    public SecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter) {
        this.apiKeyAuthFilter = apiKeyAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（API 服务，使用 Token 认证）
            .csrf(csrf -> csrf.disable())

            // 配置 CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 配置会话管理（无状态）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // 公开端点 - 不需要认证
                .requestMatchers("/api/v1/**").permitAll()          // Happy Protocol API
                .requestMatchers("/api/health").permitAll()         // 健康检查
                .requestMatchers("/api/public/**").permitAll()      // 公开 API
                .requestMatchers("/api/auth/**").permitAll()        // 认证相关
                .requestMatchers("/api/commands/**").permitAll()    // 斜杠命令
                .requestMatchers("/api/skills/**").permitAll()      // 技能管理
                .requestMatchers("/actuator/**").permitAll()        // 监控端点
                .requestMatchers("/error").permitAll()              // 错误页面
                .requestMatchers("/h2-console/**").permitAll()      // H2 控制台（开发环境）

                // 管理端点 - 需要 API Key 认证（由 ApiKeyAuthFilter 处理）
                .requestMatchers("/api/scheduler/**").authenticated()
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/agent/**").authenticated()
                .requestMatchers("/api/tools/**").authenticated()
                .requestMatchers("/api/models/**").authenticated()
                .requestMatchers("/api/premium/**").authenticated()

                // 其他端点默认需要认证
                .anyRequest().authenticated()
            )

            // 添加 API Key 过滤器（在 UsernamePasswordAuthenticationFilter 之前执行）
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // 禁用 HTTP Basic 认证（使用 API Key 认证）
            .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * CORS 配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的源（生产环境应该限制为具体域名）
        // 注意：当 allowCredentials 为 true 时，不能使用 "*"，必须指定具体域名
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:3333",
            "https://ai-agent-server-psi.vercel.app",
            "https://ai-agent-server.vercel.app",
            "https://*.vercel.app"
        ));

        // 允许所有源（不携带凭证时使用）
        // 如果需要 allowCredentials(true)，请注释掉上面具体的 origins，改用 originPatterns
        configuration.addAllowedOriginPattern("*");

        // 允许的方法
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // 允许的头部
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-API-Key",
            "X-User-ID",
            "X-Session-ID",
            "Accept",
            "Origin"
        ));

        // 暴露的头部
        configuration.setExposedHeaders(List.of(
            "X-Total-Count",
            "X-Page-Count"
        ));

        // 允许携带凭证 - 注释掉因为使用了 * 通配符
        // configuration.setAllowCredentials(true);

        // 预检请求缓存时间（秒）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
