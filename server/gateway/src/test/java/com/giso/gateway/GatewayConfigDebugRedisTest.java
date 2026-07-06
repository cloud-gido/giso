package com.giso.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConfigDebugRedisTest {

    @Test
    void buildsInfoFromHostPasswordAndDb() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisHost = "internal-redis";
        c.debugRedisPassword = "secret";
        c.debugRedisDb = 2;
        c.debugRedisSearchNamespace = "business-platform";
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("redis", c.debugRedisInfo.scheme());
        assertEquals("internal-redis.business-platform.svc.cluster.local", c.debugRedisInfo.host());
        assertEquals(6379, c.debugRedisInfo.port());
        assertEquals("secret", c.debugRedisInfo.password());
        assertEquals(2, c.debugRedisInfo.db());
    }

    @Test
    void explicitUrlTakesPrecedence() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisUrl = "redis://:x@custom:6380/1";
        c.debugRedisHost = "ignored";
        c.debugRedisPassword = "ignored";
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("custom", c.debugRedisInfo.host());
        assertEquals(6380, c.debugRedisInfo.port());
        assertEquals(1, c.debugRedisInfo.db());
    }

    @Test
    void fullRedissUrlWithSpecialPasswordAndNoPort() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisHost = "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.cache.amazonaws.com/0";
        c.debugRedisPassword = "sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI";
        c.debugRedisDb = 2;
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("rediss", c.debugRedisInfo.scheme());
        assertEquals("master.cache.amazonaws.com", c.debugRedisInfo.host());
        assertEquals(6379, c.debugRedisInfo.port());
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", c.debugRedisInfo.password());
        assertEquals("default", c.debugRedisInfo.username());
        assertEquals(0, c.debugRedisInfo.db());
        assertTrue(c.debugRedisInfo.ssl());
    }
}
