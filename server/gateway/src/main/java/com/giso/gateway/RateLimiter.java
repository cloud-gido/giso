package com.giso.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 按客户端 IP 的令牌桶限流。rps=0 时关闭。 */
final class RateLimiter {
    private final int rps;
    private final int burst;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
        long lastSeenMs;
    }

    RateLimiter(int rps, int burst) {
        this.rps = rps;
        this.burst = burst <= 0 ? rps * 2 : burst;
    }

    boolean allow(String ip) {
        if (rps <= 0) return true;
        cleanupIfNeeded();
        Bucket b = buckets.computeIfAbsent(ip, k -> {
            Bucket nb = new Bucket();
            nb.tokens = burst;
            nb.lastRefillNanos = System.nanoTime();
            return nb;
        });
        synchronized (b) {
            long now = System.nanoTime();
            b.tokens = Math.min(burst, b.tokens + (now - b.lastRefillNanos) / 1e9 * rps);
            b.lastRefillNanos = now;
            b.lastSeenMs = System.currentTimeMillis();
            if (b.tokens >= 1) {
                b.tokens -= 1;
                return true;
            }
            return false;
        }
    }

    /** 每 10 分钟清一次 30 分钟未活跃的桶，防内存泄漏 */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();
        if (now - last > 600_000 && lastCleanup.compareAndSet(last, now)) {
            buckets.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > 1_800_000);
        }
    }
}
