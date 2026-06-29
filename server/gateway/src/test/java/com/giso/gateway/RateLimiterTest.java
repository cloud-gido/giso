package com.giso.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void disabledWhenRpsZero() {
        RateLimiter rl = new RateLimiter(0, 0);
        for (int i = 0; i < 1000; i++) {
            assertTrue(rl.allow("1.2.3.4"));
        }
    }

    @Test
    void burstThenReject() {
        RateLimiter rl = new RateLimiter(1, 5);
        for (int i = 0; i < 5; i++) {
            assertTrue(rl.allow("1.2.3.4"), "burst 内第 " + (i + 1) + " 个应放行");
        }
        assertFalse(rl.allow("1.2.3.4"), "超出 burst 应拒绝");
    }

    @Test
    void perIpIsolation() {
        RateLimiter rl = new RateLimiter(1, 2);
        assertTrue(rl.allow("10.0.0.1"));
        assertTrue(rl.allow("10.0.0.1"));
        assertFalse(rl.allow("10.0.0.1"));
        // 另一个 IP 的桶独立
        assertTrue(rl.allow("10.0.0.2"));
    }

    @Test
    void refillOverTime() throws InterruptedException {
        RateLimiter rl = new RateLimiter(1000, 2);
        assertTrue(rl.allow("1.1.1.1"));
        assertTrue(rl.allow("1.1.1.1"));
        assertFalse(rl.allow("1.1.1.1"));
        Thread.sleep(50); // 1000 rps × 50ms ≈ 50 个令牌（封顶 burst=2）
        assertTrue(rl.allow("1.1.1.1"), "等待后令牌应回填");
    }
}
