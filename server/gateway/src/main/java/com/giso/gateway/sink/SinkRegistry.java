package com.giso.gateway.sink;

import com.giso.gateway.GatewayConfig;

import java.io.IOException;
import java.util.List;

/**
 * 可热更新的出口管道注册表（系统设置保存后立即 reload，无需重启 Pod）。
 */
public final class SinkRegistry implements AutoCloseable {
    private volatile List<EventSink> sinks = List.of();

    public synchronized void reload(GatewayConfig config) throws IOException {
        List<EventSink> next = SinkFactory.create(config);
        List<EventSink> prev = sinks;
        sinks = next;
        for (EventSink s : prev) {
            try {
                s.close();
            } catch (Exception ignored) { }
        }
    }

    public List<EventSink> current() {
        return sinks;
    }

    public List<String> activeNames() {
        return current().stream().map(s -> {
            String n = s.name();
            int p = n.indexOf('(');
            return p > 0 ? n.substring(0, p) : n;
        }).toList();
    }

    @Override
    public synchronized void close() {
        for (EventSink s : sinks) {
            try {
                s.close();
            } catch (Exception ignored) { }
        }
        sinks = List.of();
    }
}
