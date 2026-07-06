package com.giso.gateway.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

final class DebugBufferFilter {
    private DebugBufferFilter() { }

    static List<ObjectNode> filter(List<ObjectNode> source, int limit,
                                   String spaceKey, String did, String event, String status) {
        List<ObjectNode> out = new ArrayList<>();
        String space = spaceKey == null || spaceKey.isBlank() ? null : spaceKey;
        for (ObjectNode n : source) {
            if (out.size() >= limit) break;
            if (space != null && !space.equals(n.path("space").asText(""))) continue;
            JsonNode data = n.get("data");
            if (data == null) continue;
            if (!did.isEmpty() && !data.path("common").path("did").asText().contains(did)) continue;
            if (!event.isEmpty() && !data.path("event").asText().equals(event)) continue;
            if (!status.isEmpty() && !n.path("status").asText().equals(status)) continue;
            out.add(n);
        }
        return out;
    }

    static List<ObjectNode> byDid(List<ObjectNode> source, String did) {
        List<ObjectNode> out = new ArrayList<>();
        for (ObjectNode n : source) {
            if (n.path("data").path("common").path("did").asText().equals(did)) out.add(n);
        }
        return out;
    }
}
