package com.giso.gateway.debug;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;

/** 进程内联调缓冲（单副本 / 本地开发）。 */
public final class MemoryDebugBuffer implements DebugBuffer {
    private final int max;
    private final Deque<ObjectNode> recent = new ArrayDeque<>();
    private final BiConsumer<ObjectNode, String> onAppend;

    public MemoryDebugBuffer(int max, BiConsumer<ObjectNode, String> onAppend) {
        this.max = Math.max(1, max);
        this.onAppend = onAppend == null ? (w, s) -> { } : onAppend;
    }

    @Override
    public synchronized void append(ObjectNode wrapped, String spaceKey) {
        recent.addFirst(wrapped);
        while (recent.size() > max) recent.removeLast();
        onAppend.accept(wrapped, spaceKey);
    }

    @Override
    public synchronized List<ObjectNode> recent(int limit, String spaceKey, String did, String event, String status) {
        return DebugBufferFilter.filter(snapshot(), limit, spaceKey, did, event, status);
    }

    @Override
    public synchronized List<ObjectNode> recentByDid(String did) {
        List<ObjectNode> out = DebugBufferFilter.byDid(snapshot(), did);
        java.util.Collections.reverse(out);
        return out;
    }

    @Override
    public synchronized void clearRecent(String spaceKey) {
        if (spaceKey == null || spaceKey.isBlank()) {
            recent.clear();
            return;
        }
        String space = new DebugBufferKeys("").space(spaceKey);
        recent.removeIf(n -> space.equals(n.path("space").asText("")));
    }

    @Override
    public String backendName() {
        return "memory";
    }

    private List<ObjectNode> snapshot() {
        return new ArrayList<>(recent);
    }
}
