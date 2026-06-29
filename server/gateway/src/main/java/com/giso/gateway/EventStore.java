package com.giso.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.sink.EventSink;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件处理中枢：
 *   持久化 → 委托给插拔式 EventSink 管道（file / kafka / ...，可多路双写）
 *   内存环形缓冲（含校验结果）→ 供管理页面实时联调
 *   质量统计 → 事件维度 + 参数维度（上报量/缺失量/错误量，对应大同校验明细）
 */
public final class EventStore {
    private static final ObjectMapper M = new ObjectMapper();

    private final List<EventSink> sinks;
    private final int recentMax;
    private final Deque<ObjectNode> recent = new ArrayDeque<>();

    public static final class Counter {
        public long total, missing, error;
    }

    private final Map<String, Counter> eventStats = new LinkedHashMap<>();
    private final Map<String, Counter> paramStats = new LinkedHashMap<>();
    /** 按 App 版本的质量分布（新版本灰度埋点回归监控的依据） */
    private final Map<String, Counter> versionStats = new LinkedHashMap<>();
    /** 按 UTC 小时的接收量（epoch hour → count），供下游对账，保留最近 48 小时 */
    private final Map<Long, Long> hourlyCounts = new LinkedHashMap<>();

    private final Set<String> seenPgids = ConcurrentHashMap.newKeySet();
    private final Set<String> seenEids = ConcurrentHashMap.newKeySet();
    private final Set<String> seenBizCodes = ConcurrentHashMap.newKeySet();

    public EventStore(List<EventSink> sinks, int recentMax) {
        this.sinks = sinks;
        this.recentMax = recentMax;
    }

    /** 返回包装后的记录（原事件 + 校验结果 + 接收时间），供 SSE 推送 */
    public synchronized ObjectNode accept(JsonNode ev, Registry.Result result) {
        ObjectNode wrapped = M.createObjectNode();
        wrapped.put("stime", System.currentTimeMillis());
        wrapped.put("status", result.status());
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
        for (EventSink sink : sinks) {
            sink.accept(out, quarantine);
        }

        recent.addFirst(wrapped);
        if (recent.size() > recentMax) recent.removeLast();
        recordSeen(ev);
        updateStats(ev, result);
        Metrics.inc("giso_events_total{status=\"" + result.status() + "\"}");
        String vrsn = ev.path("common").path("app_vrsn").asText("");
        if (!vrsn.isEmpty()) {
            Metrics.inc("giso_events_by_version_total{version=\"" + vrsn
                    + "\",status=\"" + result.status() + "\"}");
        }
        return wrapped;
    }

    private void recordSeen(JsonNode ev) {
        String pgid = ev.path("page").path("pgid").asText("");
        if (!pgid.isEmpty()) seenPgids.add(pgid);
        String eid = ev.path("element").path("eid").asText("");
        if (!eid.isEmpty()) seenEids.add(eid);
        if ("biz_event".equals(ev.path("event").asText(""))) {
            String code = ev.path("biz").path("code").asText("");
            if (!code.isEmpty()) seenBizCodes.add(code);
        }
    }

    public Set<String> seenPgids() { return Set.copyOf(seenPgids); }
    public Set<String> seenEids() { return Set.copyOf(seenEids); }
    public Set<String> seenBizCodes() { return Set.copyOf(seenBizCodes); }

    private void updateStats(JsonNode ev, Registry.Result result) {
        String key = ev.path("event").asText("unknown");
        String detail = switch (key) {
            case "element_exposure", "element_click" -> key + " · " + ev.path("element").path("eid").asText("?");
            case "biz_event" -> "biz_event · " + ev.path("biz").path("code").asText("?");
            case "page_enter", "page_exit" -> key + " · " + ev.path("page").path("pgid").asText("?");
            default -> key;
        };
        Counter c = eventStats.computeIfAbsent(detail, k -> new Counter());
        c.total++;
        if (result.status().equals("missing")) c.missing++;
        if (result.status().equals("error")) c.error++;

        String version = ev.path("common").path("app_vrsn").asText("");
        String platform = ev.path("common").path("platform").asText("?");
        Counter vc = versionStats.computeIfAbsent(
                platform + " · " + (version.isEmpty() ? "unknown" : version), k -> new Counter());
        vc.total++;
        if (result.status().equals("missing")) vc.missing++;
        if (result.status().equals("error")) vc.error++;

        long hour = System.currentTimeMillis() / 3_600_000L;
        hourlyCounts.merge(hour, 1L, Long::sum);
        hourlyCounts.keySet().removeIf(h -> h < hour - 48);

        for (Registry.Issue i : result.issues()) {
            String param = i.field().substring(i.field().lastIndexOf('.') + 1);
            Counter pc = paramStats.computeIfAbsent(param, k -> new Counter());
            if (i.level().equals("missing")) pc.missing++;
            else pc.error++;
        }
    }

    public synchronized List<ObjectNode> recent(int limit, String did, String event, String status) {
        List<ObjectNode> out = new ArrayList<>();
        for (ObjectNode n : recent) {
            if (out.size() >= limit) break;
            JsonNode data = n.get("data");
            if (!did.isEmpty() && !data.path("common").path("did").asText().contains(did)) continue;
            if (!event.isEmpty() && !data.path("event").asText().equals(event)) continue;
            if (!status.isEmpty() && !n.path("status").asText().equals(status)) continue;
            out.add(n);
        }
        return out;
    }

    public synchronized ObjectNode stats() {
        ObjectNode o = M.createObjectNode();
        var evArr = o.putArray("events");
        eventStats.forEach((k, c) -> {
            ObjectNode e = evArr.addObject();
            e.put("key", k);
            e.put("total", c.total);
            e.put("missing", c.missing);
            e.put("error", c.error);
        });
        var pArr = o.putArray("params");
        paramStats.forEach((k, c) -> {
            ObjectNode e = pArr.addObject();
            e.put("key", k);
            e.put("missing", c.missing);
            e.put("error", c.error);
        });
        var vArr = o.putArray("versions");
        versionStats.forEach((k, c) -> {
            ObjectNode e = vArr.addObject();
            e.put("key", k);
            e.put("total", c.total);
            e.put("missing", c.missing);
            e.put("error", c.error);
        });
        return o;
    }

    /** 指定设备的近期事件（旧→新），供联调用例断言按序比对 */
    public synchronized List<ObjectNode> recentByDid(String did) {
        List<ObjectNode> out = new ArrayList<>();
        for (ObjectNode n : recent) {
            if (n.path("data").path("common").path("did").asText().equals(did)) out.add(n);
        }
        java.util.Collections.reverse(out); // recent 是新→旧，断言按时间正序比
        return out;
    }

    /** UTC 小时级接收量（最近 48h），供对账脚本比对 Doris 落地行数 */
    public synchronized ObjectNode hourly() {
        ObjectNode o = M.createObjectNode();
        var arr = o.putArray("hours");
        hourlyCounts.forEach((h, c) -> {
            ObjectNode e = arr.addObject();
            e.put("epoch_hour", h);
            e.put("utc", java.time.Instant.ofEpochMilli(h * 3_600_000L).toString());
            e.put("received", c);
        });
        return o;
    }

    public synchronized void clearRecent() {
        recent.clear();
        eventStats.clear();
        paramStats.clear();
        versionStats.clear();
        seenPgids.clear();
        seenEids.clear();
        seenBizCodes.clear();
    }

    public List<EventSink> sinks() { return sinks; }
}
