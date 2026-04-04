package demo.k8s.agent.plugin.hook;

import java.util.Map;

/**
 * Hook 接口 - 所有插件钩子的基接口
 * <p>
 * Hook 是一种拦截器模式，允许在关键事件点执行自定义逻辑。
 */
public interface Hook {

    /**
     * Hook 的唯一标识符
     *
     * @return Hook ID
     */
    String getId();

    /**
     * Hook 名称（人类可读）
     *
     * @return Hook 名称
     */
    String getName();

    /**
     * Hook 描述
     *
     * @return Hook 描述
     */
    String getDescription();

    /**
     * Hook 类型 - 定义这个 Hook 监听的事件类型
     *
     * @return Hook 类型
     */
    HookType getType();

    /**
     * Hook 执行阶段
     *
     * @return BEFORE, AFTER, 或 AROUND
     */
    HookPhase getPhase();

    /**
     * Hook 优先级（数字越小优先级越高）
     * <p>
     * 同类型的多个 Hook 按优先级顺序执行
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100; // 默认优先级
    }

    /**
     * 执行 Hook
     * <p>
     * BEFORE 阶段：返回 true 继续执行，返回 false 阻止目标操作
     * AFTER 阶段：返回值被忽略
     * AROUND 阶段：必须调用 proceed() 来执行目标操作
     *
     * @param context Hook 上下文（包含事件数据）
     * @return 对于 BEFORE Hook，true=继续，false=阻止；其他阶段返回值被忽略
     */
    boolean execute(HookContext context);

    /**
     * Hook 被注册时调用
     */
    default void onRegister() {}

    /**
     * Hook 被注销时调用
     */
    default void onUnregister() {}

    /**
     * Hook 错误处理
     *
     * @param context Hook 上下文
     * @param error   发生的错误
     */
    default void onError(HookContext context, Throwable error) {}

    /**
     * Hook 上下文 - 传递给 Hook 执行的数据
     */
    class HookContext {
        private final HookType hookType;
        private final String sessionId;
        private final String userId;
        private final Map<String, Object> data;
        private Object result;
        private boolean proceed = true; // 仅 BEFORE 阶段使用

        public HookContext(HookType hookType, String sessionId, String userId, Map<String, Object> data) {
            this.hookType = hookType;
            this.sessionId = sessionId;
            this.userId = userId;
            this.data = data;
        }

        public HookType getHookType() {
            return hookType;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public Map<String, Object> getData() {
            return data;
        }

        @SuppressWarnings("unchecked")
        public <T> T getData(String key) {
            return (T) data.get(key);
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public boolean shouldProceed() {
            return proceed;
        }

        public void setProceed(boolean proceed) {
            this.proceed = proceed;
        }

        /**
         * 便捷方法：获取特定类型的数据
         */
        @SuppressWarnings("unchecked")
        public <T> T getEventData(Class<T> type) {
            Object eventData = data.get("eventData");
            if (eventData != null && type.isInstance(eventData)) {
                return type.cast(eventData);
            }
            return null;
        }
    }
}
