package com.giso.gateway.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 需本机 Redis：GISO_TEST_REDIS_URL=redis://localhost:6379/15 */
@EnabledIfEnvironmentVariable(named = "GISO_TEST_REDIS_URL", matches = ".+")
class RedisDebugBufferTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static final String URL = System.getenv("GISO_TEST_REDIS_URL");
    private static final String PREFIX = "giso:test:debug";

    private RedisDebugBuffer buffer;

    @BeforeEach
    void setUp() {
        var info = RedisConnections.parseUrl(URL, null, -1);
        buffer = new RedisDebugBuffer(info, PREFIX, 100, 120);
        buffer.clearRecent(null);
    }

    @AfterEach
    void tearDown() {
        buffer.clearRecent(null);
        buffer.close();
    }

    @Test
    void appendVisibleAcrossNewBufferInstance() throws Exception {
        buffer.append(wrap("default", "did-1", "page_enter"), "default");

        try (RedisDebugBuffer reader = new RedisDebugBuffer(
                RedisConnections.parseUrl(URL, null, -1), PREFIX, 100, 120)) {
            var recent = reader.recent(10, "default", "did-1", "", "");
            assertEquals(1, recent.size());
            assertEquals("page_enter", recent.get(0).path("data").path("event").asText());
        }
    }

    @Test
    void clearBySpace() throws Exception {
        buffer.append(wrap("default", "d1", "page_enter"), "default");
        buffer.append(wrap("longvideo", "d2", "page_enter"), "longvideo");
        buffer.clearRecent("default");

        try (RedisDebugBuffer reader = new RedisDebugBuffer(
                RedisConnections.parseUrl(URL, null, -1), PREFIX, 100, 120)) {
            assertEquals(0, reader.recent(10, "default", "", "", "").size());
            assertEquals(1, reader.recent(10, "longvideo", "", "", "").size());
        }
    }

    private static ObjectNode wrap(String space, String did, String event) throws Exception {
        String json = """
            {"stime":%d,"status":"ok","space":"%s","issues":[],"data":{"event":"%s","common":{"did":"%s"}}}
            """.formatted(System.currentTimeMillis(), space, event, did);
        return (ObjectNode) M.readTree(json);
    }
}
