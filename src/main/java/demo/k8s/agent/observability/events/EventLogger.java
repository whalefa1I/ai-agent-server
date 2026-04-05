package demo.k8s.agent.observability.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.k8s.agent.observability.logging.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 事件日志记录器
 * <p>
 * 监听所有事件并记录到结构化日志中，用于审计和后续分析
 */
@Component
public class EventLogger {

    private static final Logger log = LoggerFactory.getLogger(EventLogger.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EventBus eventBus;

    public EventLogger(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 应用准备就绪后注册事件处理器
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("注册事件日志记录器");

        // 订阅所有事件类型
        eventBus.subscribe(Event.class, this::logEvent);
    }

    /**
     * 记录事件到日志
     */
    private void logEvent(Event event) {
        try {
            Map<String, Object> eventDataMap = new java.util.HashMap<>();
            eventDataMap.put("eventType", event.getEventType());
            eventDataMap.put("eventId", event.id());
            eventDataMap.put("sessionId", event.sessionId());
            eventDataMap.put("userId", event.userId());
            eventDataMap.put("timestamp", event.timestamp().toString());
            eventDataMap.put("metadata", event.metadata());

            String eventData = objectMapper.writeValueAsString(eventDataMap);

            log.info("EVENT: {}", eventData);

            // 同时记录到结构化日志
            StructuredLogger.logEvent(event.getEventType(),
                    event.sessionId(),
                    event.userId(),
                    event.metadata());

        } catch (Exception e) {
            log.error("记录事件日志失败：{}", event, e);
        }
    }
}
