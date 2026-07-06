package com.giso.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.debug.DebugBuffer;
import com.giso.gateway.sink.EventSink;
import com.giso.gateway.sink.SinkRegistry;
import com.giso.gateway.space.SpaceService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件处理中枢（按空间隔离统计与覆盖率累计）。
 * 联调近期缓冲委托 {@link DebugBuffer}（memory 或 redis）。
 */
public final class EventStore {
    private static final ObjectMapper M = new ObjectMapper();

    private final SinkRegistry sinkRegistry;
    private final DebugBuffer debugBuffer;

    public static final class Counter {
        public long total, missing, error;
    }

    private final Map<String, Counter> eventStats = new LinkedHashMap<>();
    private final Map<String, Counter> paramStats = new LinkedHashMap<>();
    private final Map<String, Counter> versionStats = new LinkedHashMap<>();
    /** space|epochHour → count */
    private final Map<String, Long> hourlyCounts = new LinkedHashMap<>();

    private final Set<String> seenPgids = ConcurrentHashMap.newKeySet();
    private final Set<String> seenEids = ConcurrentHashMap.newKeySet();
    private final Set<String> seenBizCodes = ConcurrentHashMap.newKeySet();

    public EventStore(SinkRegistry sinkRegistry, DebugBuffer debugBuffer) {
        this.sinkRegistry = sinkRegistry;
        this.debugBuffer = debugBuffer;
    }

    public DebugBuffer debugBuffer() {
        return debugBuffer;
    }

    private static String sk(String spaceKey) {
        return spaceKey == null || spaceKey.isBlank() ? SpaceService.DEFAULT_SPACE : spaceKey;
    }

    public synchronized ObjectNode accept(JsonNode ev, Registry.Result result, String spaceKey) {
        String space = sk(spaceKey);
        ObjectNode wrapped = M.createObjectNode();
        wrapped.put("stime", System.currentTimeMillis());
        wrapped.put("status", result.status());
        wrapped.put("space", space);
        var issues = wrapped.putArray("issues");
        for (Registry.Issue i : result.issues()) {
            ObjectNode io = issues.addObject();
            io.put("level", i.level());
            io.put("field", i.field());
            io.put("msg", i.msg());
        }
        wrapped.set("data", ev);

        boolean quarantine = result.status().equals("error");
        ObjectNode out = ev.deepCopy();
        out.put("stime", wrapped.get("stime").asLong());
        if (result.status().equals("missing")) out.put("_quality", "missing");
        if (quarantine) out.set("_issues", issues.deepCopy());
        for (EventSink sink : sinkRegistry.current()) {
            sink.accept(out, quarantine);
        }

        debugBuffer.append(wrapped, spaceKey);
        recordSeen(ev, space);
        updateStats(ev, result, space);
        Metrics.inc("giso_events_total{status=\"" + result.status() + "\"}");
        String vrsn = ev.path("common").path("app_vrsn").asText("");
        if (!vrsn.isEmpty()) {
            Metrics.inc("giso_events_by_version_total{version=\"" + vrsn
                    + "\",status=\"" + result.status() + "\"}");
        }
        return wrapped;
    }

    private void recordSeen(JsonNode ev, String space) {
        String pgid = ev.path("page").path("pgid").asText("");
        if (!pgid.isEmpty()) seenPgids.add(space + "|" + pgid);
        String eid = ev.path("element").path("eid").asText("");
        if (!eid.isEmpty()) seenEids.add(space + "|" + eid);
        if ("biz_event".equals(ev.path("event").asText(""))) {
            String code = ev.path("biz").path("code").asText("");
            if (!code.isEmpty()) seenBizCodes.add(space + "|" + code);
        }
    }

    public Set<String> seenPgids(String spaceKey) { return seenFor(spaceKey, seenPgids); }
    public Set<String> seenEids(String spaceKey) { return seenFor(spaceKey, seenEids); }
    public Set<String> seenBizCodes(String spaceKey) { return seenFor(spaceKey, seenBizCodes); }

    /** @deprecated 使用 {@link #seenPgids(String)} */
    @Deprecated
    public Set<String> seenPgids() { return seenPgids(null); }

    @Deprecated
    public Set<String> seenEids() { return seenEids(null); }

    @Deprecated
    public Set<String> seenBizCodes() { return seenBizCodes(null); }

    private static Set<String> seenFor(String spaceKey, Set<String> raw) {
        String prefix = sk(spaceKey) + "|";
        return raw.stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void updateStats(JsonNode ev, Registry.Result result, String space) {
        String evName = ev.path("event").asText("unknown");
        String detail = switch (evName) {
            case "element_exposure", "element_click" ->
                    space + "|" + evName + " · " + ev.path("element").path("eid").asText("?");
            case "biz_event" -> space + "|biz_event · " + ev.path("biz").path("code").asText("?");
            case "page_enter", "page_exit" ->
                    space + "|" + evName + " · " + ev.path("page").path("pgid").asText("?");
            default -> space + "|" + evName;
        };
        Counter c = eventStats.computeIfAbsent(detail, k -> new Counter());
        c.total++;
        if (result.status().equals("missing")) c.missing++;
        if (result.status().equals("error")) c.error++;

        String version = ev.path("common").path("app_vrsn").asText("");
        String platform = ev.path("common").path("platform").asText("?");
        Counter vc = versionStats.computeIfAbsent(
                space + "|" + platform + " · " + (version.isEmpty() ? "unknown" : version),
                k -> new Counter());
        vc.total++;
        if (result.status().equals("missing")) vc.missing++;
        if (result.status().equals("error")) vc.error++;

        long hour = System.currentTimeMillis() / 3_600_000L;
        String hourKey = space + "|" + hour;
        hourlyCounts.merge(hourKey, 1L, Long::sum);
        long cutoff = hour - 48;
        hourlyCounts.keySet().removeIf(k -> {
            int i = k.lastIndexOf('|');
            if (i < 0) return false;
            try {
                return Long.parseLong(k.substring(i + 1)) < cutoff;
            } catch (NumberFormatException e) {
                return false;
            }
        });

        for (Registry.Issue i : result.issues()) {
            String param = i.field().substring(i.field().lastIndexOf('.') + 1);
            Counter pc = paramStats.computeIfAbsent(space + "|" + param, k -> new Counter());
            if (i.level().equals("missing")) pc.missing++;
            else pc.error++;
        }
    }

    public List<ObjectNode> recent(int limit, String spaceKey, String did, String event, String status) {
        return debugBuffer.recent(limit, spaceKey, did, event, status);
    }

    public synchronized ObjectNode stats(String spaceKey) {
        String space = spaceKey == null || spaceKey.isBlank() ? null : sk(spaceKey);
        ObjectNode o = M.createObjectNode();
        var evArr = o.putArray("events");
        eventStats.forEach((k, c) -> {
            if (space != null && !k.startsWith(space + "|")) return;
            ObjectNode e = evArr.addObject();
            e.put("key", space != null ? k.substring(space.length() + 1) : k);
            e.put("total", c.total);
            e.put("missing", c.missing);
            e.put("error", c.error);
        });
        var pArr = o.putArray("params");
        paramStats.forEach((k, c) -> {
            if (space != null && !k.startsWith(space + "|")) return;
            ObjectNode e = pArr.addObject();
            e.put("key", space != null ? k.substring(space.length() + 1) : k);
            e.put("missing", c.missing);
            e.put("error", c.error);
        });
        var vArr = o.putArray("versions");
        versionStats.forEach((k, c) -> {
            if (space != null && !k.startsWith(space + "|")) return;
            ObjectNode e = vArr.addObject();
            e.put("key", space != null ? k.substring(space.length() + 1) : k);
            e.put("total", c.total);
            e.put("missing", c.missing);
            e.put("error", c.error);
        });
        return o;
    }

    public List<ObjectNode> recentByDid(String did) {
        return debugBuffer.recentByDid(did);
    }

    public synchronized ObjectNode hourly(String spaceKey) {
        String space = sk(spaceKey);
        ObjectNode o = M.createObjectNode();
        o.put("space", space);
        var arr = o.putArray("hours");
        String prefix = space + "|";
        hourlyCounts.forEach((k, c) -> {
            if (!k.startsWith(prefix)) return;
            long h = Long.parseLong(k.substring(prefix.length()));
            ObjectNode e = arr.addObject();
            e.put("epoch_hour", h);
            e.put("utc", java.time.Instant.ofEpochMilli(h * 3_600_000L).toString());
            e.put("received", c);
        });
        return o;
    }

    public synchronized void clearRecent(String spaceKey) {
        debugBuffer.clearRecent(spaceKey);
        if (spaceKey == null || spaceKey.isBlank()) {
            eventStats.clear();
            paramStats.clear();
            versionStats.clear();
            hourlyCounts.clear();
            seenPgids.clear();
            seenEids.clear();
            seenBizCodes.clear();
            return;
        }
        String space = sk(spaceKey);
        String prefix = space + "|";
        eventStats.keySet().removeIf(k -> k.startsWith(prefix));
        paramStats.keySet().removeIf(k -> k.startsWith(prefix));
        versionStats.keySet().removeIf(k -> k.startsWith(prefix));
        hourlyCounts.keySet().removeIf(k -> k.startsWith(prefix));
        seenPgids.removeIf(k -> k.startsWith(prefix));
        seenEids.removeIf(k -> k.startsWith(prefix));
        seenBizCodes.removeIf(k -> k.startsWith(prefix));
    }

    public List<EventSink> sinks() { return sinkRegistry.current(); }
}
