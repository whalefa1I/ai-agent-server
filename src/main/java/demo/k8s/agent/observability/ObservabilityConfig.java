package demo.k8s.agent.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer 指标导出配置。
 * TODO: Prometheus 依赖暂未正确安装，暂时注释
 */
@Configuration
public class ObservabilityConfig {

    /**
     * 会话统计指标绑定
     */
    @Bean
    public MeterBinder sessionStatsBinder(SessionStats sessionStats) {
        return registry -> {
            // 注册 SessionStats 到 MeterRegistry
            sessionStats.setMeterRegistry(registry);
        };
    }
}
