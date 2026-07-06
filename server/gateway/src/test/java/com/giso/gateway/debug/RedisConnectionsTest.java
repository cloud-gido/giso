package com.giso.gateway.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConnectionsTest {

    @Test
    void parsesRedissUrlWithSpecialCharsAndMissingPort() {
        var info = RedisConnections.parseUrl(
                "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/2",
                null, "wrong-override", -1);
        assertEquals("rediss", info.scheme());
        assertEquals("master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com", info.host());
        assertEquals(6379, info.port());
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", info.password());
        assertEquals(2, info.db());
        assertTrue(info.ssl());
    }

    @Test
    void usesEnvPasswordWhenUrlHasNoAuth() {
        var info = RedisConnections.parseUrl(
                "rediss://master.example.com:6379/0",
                "default", "secret", 2);
        assertEquals("default", info.username());
        assertEquals("secret", info.password());
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

    @Test
    void overrideDbFromConfig() {
        var info = RedisConnections.parseUrl(
                "rediss://:pw@host.example.com:6379/0",
                null, null, 2);
        assertEquals(2, info.db());
        assertEquals(6379, info.port());
    }
}
