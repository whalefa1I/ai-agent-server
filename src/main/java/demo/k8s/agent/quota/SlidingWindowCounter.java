package demo.k8s.agent.quota;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 滑动窗口计数器（用于限流）
 */
public class SlidingWindowCounter {

    private final Duration windowSize;
    private final ConcurrentLinkedQueue<Long> timestamps = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);

    public SlidingWindowCounter(Duration windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * 尝试获取一个配额
     *
     * @return true = 成功，false = 已达限流
     */
    public synchronized boolean tryAcquire(Instant now, int limit) {
        long windowStart = now.minus(windowSize).toEpochMilli();
        long nowMs = now.toEpochMilli();

        // 清理过期时间戳
        cleanupExpired(windowStart);

        // 检查是否超过限制
        if (count.get() >= limit) {
            return false;
        }

        // 记录新的时间戳
        timestamps.offer(nowMs);
        count.incrementAndGet();
        return true;
    }

    /**
     * 获取当前计数
     */
    public int getCount() {
        return count.get();
    }

    /**
     * 清理过期的时间戳
     */
    private void cleanupExpired(long windowStart) {
        Long oldestTimestamp;
        while ((oldestTimestamp = timestamps.peek()) != null && oldestTimestamp < windowStart) {
            timestamps.poll();
            count.decrementAndGet();
        }
    }

    /**
     * 重置计数器
     */
    public void reset() {
        timestamps.clear();
        count.set(0);
    }
}
