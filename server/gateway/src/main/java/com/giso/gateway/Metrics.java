package com.giso.gateway;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 零依赖 Prometheus 指标（文本 exposition 格式，GET /metrics 抓取）。
 * series 写法即 Prometheus 时序名，如 giso_events_total{status="ok"}。
 */
public final class Metrics {
    private static final long START_MS = System.currentTimeMillis();
    private static final ConcurrentMap<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();

    private Metrics() { }

    public static void inc(String series) {
        COUNTERS.computeIfAbsent(series, k -> new LongAdder()).increment();
    }

    public static String render() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# TYPE giso_gateway_uptime_seconds gauge\n");
        sb.append("giso_gateway_uptime_seconds ")
          .append((System.currentTimeMillis() - START_MS) / 1000).append('\n');

        String lastMetric = "";
        for (Map.Entry<String, LongAdder> e : new TreeMap<>(COUNTERS).entrySet()) {
            String base = e.getKey();
            int brace = base.indexOf('{');
            if (brace > 0) base = base.substring(0, brace);
            if (!base.equals(lastMetric)) {
                sb.append("# TYPE ").append(base).append(" counter\n");
                lastMetric = base;
            }
            sb.append(e.getKey()).append(' ').append(e.getValue().sum()).append('\n');
        }
        return sb.toString();
    }

    /** 仅供单测复位 */
    static void reset() {
        COUNTERS.clear();
    }
}
