package com.giso.gateway.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryDebugBufferTest {
    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void appendRecentAndClearBySpace() throws Exception {
        var buf = new MemoryDebugBuffer(10, (w, s) -> { });
        buf.append(wrap("default", "d1", "page_enter"), "default");
        buf.append(wrap("longvideo", "d2", "page_enter"), "longvideo");

        assertEquals(1, buf.recent(10, "default", "", "", "").size());
        assertEquals(1, buf.recent(10, "longvideo", "", "", "").size());

        buf.clearRecent("default");
        assertEquals(0, buf.recent(10, "default", "", "", "").size());
        assertEquals(1, buf.recent(10, "longvideo", "", "", "").size());
    }

    @Test
    void recentByDidOrderedOldestFirst() throws Exception {
        var buf = new MemoryDebugBuffer(10, (w, s) -> { });
        buf.append(wrap("default", "did-x", "app_launch"), "default");
        Thread.sleep(2);
        buf.append(wrap("default", "did-x", "page_enter"), "default");

        var list = buf.recentByDid("did-x");
        assertEquals(2, list.size());
        assertEquals("app_launch", list.get(0).path("data").path("event").asText());
        assertEquals("page_enter", list.get(1).path("data").path("event").asText());
    }

    @Test
    void onAppendCallbackFires() throws Exception {
        var seen = new boolean[1];
        var buf = new MemoryDebugBuffer(5, (w, s) -> seen[0] = true);
        buf.append(wrap("default", "d", "page_enter"), "default");
        assertTrue(seen[0]);
    }

    private static ObjectNode wrap(String space, String did, String event) throws Exception {
        String json = """
            {"stime":1,"status":"ok","space":"%s","issues":[],"data":{"event":"%s","common":{"did":"%s"}}}
            """.formatted(space, event, did);
        return (ObjectNode) M.readTree(json);
    }
}
