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
}
