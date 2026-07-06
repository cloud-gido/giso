package com.giso.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConfigDebugRedisTest {

    @Test
    void buildsUrlFromHostPasswordAndDb() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisHost = "internal-redis";
        c.debugRedisPassword = "secret";
        c.debugRedisDb = 2;
        c.debugRedisSearchNamespace = "business-platform";
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals(
                "redis://:secret@internal-redis.business-platform.svc.cluster.local:6379/2",
                c.debugRedisUrl);
    }

    @Test
    void explicitUrlTakesPrecedence() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisUrl = "redis://:x@custom:6380/1";
        c.debugRedisHost = "ignored";
        c.debugRedisPassword = "ignored";
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("redis://:x@custom:6380/1", c.debugRedisUrl);
    }

    @Test
    void encodesSpecialCharsInPassword() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisHost = "redis.example.com";
        c.debugRedisPassword = "p@ss/word";
        c.debugRedisDb = 2;
        GatewayConfig.resolveDebugRedisUrl(c);
        assertTrue(c.debugRedisUrl.startsWith("redis://:"));
        assertTrue(c.debugRedisUrl.contains("redis.example.com:6379/2"));
    }

    @Test
    void fullRedissUrlInHostOnlyChangesDb() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisHost = "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.cache.amazonaws.com:6379/0";
        c.debugRedisPassword = "sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI";
        c.debugRedisDb = 2;
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals(
                "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.cache.amazonaws.com:6379/2",
                c.debugRedisUrl);
    }

    @Test
    void fullRedissUrlWithoutDbAppendsDb() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisHost = "rediss://:secret@master.cache.amazonaws.com:6379";
        c.debugRedisPassword = "secret";
        c.debugRedisDb = 2;
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("rediss://:secret@master.cache.amazonaws.com:6379/2", c.debugRedisUrl);
    }
}
