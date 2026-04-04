package demo.k8s.agent.observability.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 事件总线
 */
@Component
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    /**
     * 事件处理器注册表
     */
    private final List<EventHandler<?>> handlers = new CopyOnWriteArrayList<>();

    /**
     * 事件历史（内存缓存，用于近期事件查询）
     */
    private final List<Event> eventHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY_SIZE = 1000;

    /**
     * 注册事件处理器
     */
    public <T extends Event> void subscribe(Class<T> eventType, Consumer<T> handler) {
        handlers.add(new EventHandler<>(eventType, handler));
        log.debug("注册事件处理器：{}", eventType.getSimpleName());
    }

    /**
     * 发布事件
     */
    public void publish(Event event) {
        // 添加到历史
        addToHistory(event);

        // 通知所有处理器
        for (EventHandler<?> handler : handlers) {
            if (handler.eventType.isInstance(event)) {
                try {
                    ((EventHandler<Event>) handler).invoke(event);
                } catch (Exception e) {
                    log.error("事件处理失败：{}", event, e);
                }
            }
        }

        log.debug("发布事件：{} (sessionId={}, userId={})",
                event.getClass().getSimpleName(),
                event.sessionId(),
                event.userId());
    }

    /**
     * 添加到历史
     */
    private synchronized void addToHistory(Event event) {
        eventHistory.add(event);
        while (eventHistory.size() > MAX_HISTORY_SIZE) {
            eventHistory.remove(0);
        }
    }

    /**
     * 获取最近的事件
     */
    public List<Event> getRecentEvents(int limit) {
        return eventHistory.stream()
                .skip(Math.max(0, eventHistory.size() - limit))
                .toList();
    }

    /**
     * 根据会话 ID 获取事件
     */
    public List<Event> getEventsBySession(String sessionId) {
        return eventHistory.stream()
                .filter(e -> sessionId.equals(e.sessionId()))
                .toList();
    }

    /**
     * 根据用户 ID 获取事件
     */
    public List<Event> getEventsByUser(String userId) {
        return eventHistory.stream()
                .filter(e -> userId.equals(e.userId()))
                .toList();
    }

    /**
     * 根据事件类型获取事件
     */
    public <T extends Event> List<T> getEventsByType(Class<T> eventType) {
        return eventHistory.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .toList();
    }

    /**
     * 清理事件历史
     */
    public void clearHistory() {
        eventHistory.clear();
    }

    /**
     * 事件处理器
     */
    private static class EventHandler<T extends Event> {
        private final Class<T> eventType;
        private final Consumer<T> handler;

        public EventHandler(Class<T> eventType, Consumer<T> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        public void invoke(Event event) {
            if (eventType.isInstance(event)) {
                handler.accept((T) event);
            }
        }
    }
}
