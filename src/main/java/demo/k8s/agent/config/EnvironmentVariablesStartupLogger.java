package demo.k8s.agent.config;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动后可选地统一打印 {@link System#getenv()}，便于核对部署平台环境变量是否注入。
 * 由 {@link DemoDebugProperties#isLogEnvironmentAtStartup()} 控制；敏感键名仅打印是否为空与长度。
 */
@Component
@Order(0)
public class EnvironmentVariablesStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentVariablesStartupLogger.class);

    private final DemoDebugProperties debugProperties;

    public EnvironmentVariablesStartupLogger(DemoDebugProperties debugProperties) {
        this.debugProperties = debugProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!debugProperties.isLogEnvironmentAtStartup()) {
            return;
        }
        Map<String, String> env = new TreeMap<>(System.getenv());
        int maxVal = Math.max(64, debugProperties.getLogEnvironmentValueMaxChars());
        log.info("[StartupEnv] totalKeys={}", env.size());
        for (Map.Entry<String, String> e : env.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (sensitiveEnvKey(k)) {
                boolean empty = v == null || v.isEmpty();
                int len = v == null ? 0 : v.length();
                log.info("[StartupEnv] {}=(redacted, empty={}, len={})", k, empty, len);
            } else {
                String raw = v != null ? v : "";
                String shown = raw.length() > maxVal
                        ? raw.substring(0, maxVal) + "...(truncated, len=" + raw.length() + ")"
                        : raw;
                log.info("[StartupEnv] {}={}", k, shown);
            }
        }
        log.info("[StartupEnv] dump complete");
    }

    static boolean sensitiveEnvKey(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        String u = name.toUpperCase(Locale.ROOT);
        if (u.contains("PASSWORD")) {
            return true;
        }
        if (u.contains("SECRET")) {
            return true;
        }
        if (u.contains("TOKEN")) {
            return true;
        }
        if (u.contains("CREDENTIAL")) {
            return true;
        }
        if (u.contains("PRIVATE_KEY") || u.endsWith("_PRIVATE_KEY")) {
            return true;
        }
        if (u.contains("API_KEY") || u.endsWith("_API_KEY")) {
            return true;
        }
        if (u.contains("DATABASE_URL") || u.contains("JDBC") || u.contains("REDIS_URL")) {
            return true;
        }
        if (u.contains("WEBHOOK") && u.contains("SECRET")) {
            return true;
        }
        if (u.contains("SIGNING") && (u.contains("KEY") || u.contains("SECRET"))) {
            return true;
        }
        return false;
    }
}
