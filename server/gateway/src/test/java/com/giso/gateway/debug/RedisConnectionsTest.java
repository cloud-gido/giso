package com.giso.gateway.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConnectionsTest {

    @Test
    void parsesRedissUrlWithSpecialCharsAndMissingPort() {
        var info = RedisConnections.parseUrl(
                "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/2",
                null, -1);
        assertEquals("rediss", info.scheme());
        assertEquals("master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com", info.host());
        assertEquals(6379, info.port());
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", info.password());
        assertEquals(2, info.db());
        assertTrue(info.ssl());
    }

    @Test
    void overrideDbFromConfig() {
        var info = RedisConnections.parseUrl(
                "rediss://:pw@host.example.com:6379/0",
                "pw", 2);
        assertEquals(2, info.db());
        assertEquals(6379, info.port());
    }
}
