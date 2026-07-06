package com.giso.gateway.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConnectionsTest {

    @Test
    void parsesRedissUrlWithSpecialCharsAndMissingPort() {
        var info = RedisConnections.parseUrl(
                "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/2",
                null, "sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", -1);
        assertEquals("rediss", info.scheme());
        assertEquals("master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com", info.host());
        assertEquals(6379, info.port());
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", info.password());
        assertEquals("", info.username());
        assertEquals(0, info.db());
        assertTrue(info.ssl());
    }

    @Test
    void percentDecodesAuthPartOnly() {
        var info = RedisConnections.parseUrl(
                "rediss://:sTtN%3FYo5q-qaHGpP6%3DkEWJRT%21WOTFPI@master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/0",
                null, null, -1);
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", info.password());
        assertEquals("master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com", info.host());
    }

    @Test
    void embeddedPasswordWinsOverOverride() {
        var info = RedisConnections.parseUrl(
                "rediss://:wrong@master.cache.amazonaws.com:6379/0",
                null, "correct-secret", 2);
        assertEquals("wrong", info.password());
        assertEquals("", info.username());
        assertEquals(0, info.db());
    }

    @Test
    void separatePasswordUsedWhenUrlHasNoPassword() {
        var info = RedisConnections.parseUrl(
                "rediss://master.cache.amazonaws.com:6379/0",
                null, "correct-secret", 2);
        assertEquals("correct-secret", info.password());
        assertEquals("", info.username());
        assertEquals(0, info.db());
    }

    @Test
    void internalRedisKeepsDb2() {
        var info = RedisConnections.fromParts(
                "redis", "internal-redis.business-platform.svc.cluster.local", "", "secret", 6379, 2);
        assertEquals("", info.username());
        assertEquals(2, info.db());
    }

    @Test
    void parsesUsernameFromUrl() {
        var info = RedisConnections.parseUrl(
                "rediss://default:secret@host.example.com:6379/0",
                null, null, 2);
        assertEquals("default", info.username());
        assertEquals("secret", info.password());
        assertEquals(2, info.db());
    }
}
