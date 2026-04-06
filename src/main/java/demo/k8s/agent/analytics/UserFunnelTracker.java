package demo.k8s.agent.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.observability.logging.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户漏斗追踪器
 *
 * 追踪用户转化漏斗：
 * 1. 访问 (Visit) → 2. 注册 (Register) → 3. 激活 (Activate) → 4. 首次请求 (First Request) → 5. 留存 (Retained)
 */
@Component
public class UserFunnelTracker {

    private static final Logger log = LoggerFactory.getLogger(UserFunnelTracker.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 用户漏斗状态缓存（内存，后续可持久化）
    private final ConcurrentHashMap<String, FunnelState> userFunnelStates = new ConcurrentHashMap<>();

    /**
     * 用户漏斗阶段
     */
    public enum FunnelStage {
        VISIT,          // 访问
        REGISTER,       // 注册
        ACTIVATE,       // 激活
        FIRST_REQUEST,  // 首次请求
        RETAINED        // 留存
    }

    /**
     * 漏斗状态
     */
    public record FunnelState(
            String userId,
            FunnelStage currentStage,
            Instant visitAt,
            Instant registerAt,
            Instant activateAt,
            Instant firstRequestAt,
            Instant retainedAt,
            Map<String, Object> metadata
    ) {}

    /**
     * 记录用户访问
     */
    public void trackVisit(String userId, Map<String, Object> metadata) {
        FunnelState state = userFunnelStates.computeIfAbsent(userId, k -> {
            FunnelState newState = new FunnelState(userId, FunnelStage.VISIT, Instant.now(), null, null, null, null, new HashMap<>());
            logFunnelEvent(userId, FunnelStage.VISIT, metadata);
            return newState;
        });
        log.info("用户 {} 访问漏斗状态：{}", userId, state.currentStage());
    }

    /**
     * 记录用户注册
     */
    public void trackRegister(String userId, Map<String, Object> metadata) {
        FunnelState existingState = userFunnelStates.get(userId);
        if (existingState == null || existingState.currentStage() == FunnelStage.VISIT) {
            FunnelState newState = new FunnelState(
                    userId, FunnelStage.REGISTER,
                    existingState != null ? existingState.visitAt() : Instant.now(),
                    Instant.now(), null, null, null, new HashMap<>(metadata != null ? metadata : Map.of())
            );
            userFunnelStates.put(userId, newState);
            logFunnelEvent(userId, FunnelStage.REGISTER, metadata);
            log.info("用户 {} 注册，漏斗阶段：{}", userId, FunnelStage.REGISTER);
        }
    }

    /**
     * 记录用户激活
     */
    public void trackActivate(String userId, Map<String, Object> metadata) {
        FunnelState existingState = userFunnelStates.get(userId);
        if (existingState != null && existingState.currentStage() == FunnelStage.REGISTER) {
            FunnelState newState = new FunnelState(
                    userId, FunnelStage.ACTIVATE,
                    existingState.visitAt(), existingState.registerAt(),
                    Instant.now(), null, null, new HashMap<>(metadata != null ? metadata : Map.of())
            );
            userFunnelStates.put(userId, newState);
            logFunnelEvent(userId, FunnelStage.ACTIVATE, metadata);
            log.info("用户 {} 激活，漏斗阶段：{}", userId, FunnelStage.ACTIVATE);
        }
    }

    /**
     * 记录首次请求
     */
    public void trackFirstRequest(String userId, Map<String, Object> metadata) {
        FunnelState existingState = userFunnelStates.get(userId);
        if (existingState != null && (existingState.currentStage() == FunnelStage.ACTIVATE || existingState.currentStage() == FunnelStage.REGISTER)) {
            FunnelState newState = new FunnelState(
                    userId, FunnelStage.FIRST_REQUEST,
                    existingState.visitAt(), existingState.registerAt(), existingState.activateAt(),
                    Instant.now(), null, new HashMap<>(metadata != null ? metadata : Map.of())
            );
            userFunnelStates.put(userId, newState);
            logFunnelEvent(userId, FunnelStage.FIRST_REQUEST, metadata);
            log.info("用户 {} 首次请求，漏斗阶段：{}", userId, FunnelStage.FIRST_REQUEST);
        }
    }

    /**
     * 记录用户留存
     */
    public void trackRetained(String userId, Map<String, Object> metadata) {
        FunnelState existingState = userFunnelStates.get(userId);
        if (existingState != null && existingState.currentStage() == FunnelStage.FIRST_REQUEST) {
            FunnelState newState = new FunnelState(
                    userId, FunnelStage.RETAINED,
                    existingState.visitAt(), existingState.registerAt(), existingState.activateAt(),
                    existingState.firstRequestAt(), Instant.now(), new HashMap<>(metadata != null ? metadata : Map.of())
            );
            userFunnelStates.put(userId, newState);
            logFunnelEvent(userId, FunnelStage.RETAINED, metadata);
            log.info("用户 {} 留存，漏斗阶段：{}", userId, FunnelStage.RETAINED);
        }
    }

    /**
     * 获取用户漏斗状态
     */
    public FunnelState getFunnelState(String userId) {
        return userFunnelStates.get(userId);
    }

    /**
     * 获取漏斗统计
     */
    public Map<String, Object> getFunnelStats() {
        int visitCount = 0;
        int registerCount = 0;
        int activateCount = 0;
        int firstRequestCount = 0;
        int retainedCount = 0;

        for (FunnelState state : userFunnelStates.values()) {
            switch (state.currentStage()) {
                case VISIT -> visitCount++;
                case REGISTER -> registerCount++;
                case ACTIVATE -> activateCount++;
                case FIRST_REQUEST -> firstRequestCount++;
                case RETAINED -> retainedCount++;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", userFunnelStates.size());
        stats.put("stages", Map.of(
                "visit", visitCount,
                "register", registerCount,
                "activate", activateCount,
                "first_request", firstRequestCount,
                "retained", retainedCount
        ));

        // 计算转化率
        if (visitCount > 0) {
            stats.put("conversionRates", Map.of(
                    "visit_to_register", String.format("%.2f%%", (double) registerCount / visitCount * 100),
                    "register_to_activate", activateCount > 0 ? String.format("%.2f%%", (double) activateCount / registerCount * 100) : "N/A",
                    "activate_to_first_request", firstRequestCount > 0 ? String.format("%.2f%%", (double) firstRequestCount / activateCount * 100) : "N/A",
                    "first_request_to_retained", retainedCount > 0 ? String.format("%.2f%%", (double) retainedCount / firstRequestCount * 100) : "N/A"
            ));
        }

        return stats;
    }

    /**
     * 记录漏斗事件到结构化日志
     */
    private void logFunnelEvent(String userId, FunnelStage stage, Map<String, Object> metadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("funnelStage", stage.name());
        if (metadata != null) {
            data.putAll(metadata);
        }
        StructuredLogger.logEvent("funnel_" + stage.name().toLowerCase(), null, userId, data, "user_funnel", null, null);
    }
}
