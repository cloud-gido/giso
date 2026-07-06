package com.giso.gateway;

import com.giso.gateway.debug.RedisConnections;
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
    void explicitUrlIgnoresSplitHostAndPassword() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisUrl = "rediss://:url-token@master.cache.amazonaws.com/0";
        c.debugRedisHost = "ignored";
        c.debugRedisPassword = "ignored-password";
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("url-token", c.debugRedisInfo.password());
        assertEquals(RedisConnections.PASSWORD_SOURCE_URL, c.debugRedisInfo.passwordSource());
    }

    @Test
    void redisUriInHostFieldIsRejected() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisHost = "rediss://:token@master.cache.amazonaws.com/0";
        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> GatewayConfig.resolveDebugRedisUrl(c));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("GISO_DEBUG_REDIS_URL"));
    }

    @Test
    void elasticacheUrlWithoutEmbeddedPasswordUsesEnvPassword() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisUrl = "rediss://master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/0";
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("rediss", c.debugRedisInfo.scheme());
        assertEquals("", c.debugRedisInfo.password());
        assertEquals("", c.debugRedisInfo.passwordSource());
    }

    @Test
    void fullRedissUrlWithSpecialPasswordAndNoPort() {
        GatewayConfig c = new GatewayConfig();
        c.debugRedisUrl = "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.cache.amazonaws.com/0";
        GatewayConfig.resolveDebugRedisUrl(c);
        assertEquals("rediss", c.debugRedisInfo.scheme());
        assertEquals("master.cache.amazonaws.com", c.debugRedisInfo.host());
        assertEquals(6379, c.debugRedisInfo.port());
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", c.debugRedisInfo.password());
        assertEquals(RedisConnections.PASSWORD_SOURCE_URL, c.debugRedisInfo.passwordSource());
        assertEquals("", c.debugRedisInfo.username());
        assertEquals(0, c.debugRedisInfo.db());
        assertTrue(c.debugRedisInfo.ssl());
    }
}
