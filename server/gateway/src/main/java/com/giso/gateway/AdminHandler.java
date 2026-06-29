package com.giso.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * 管理 REST API：
 *   GET    /admin/api/registry                  四张注册表
 *   POST   /admin/api/registry/{kind}           新增/编辑条目（写回 YAML）
 *   DELETE /admin/api/registry/{kind}?key=xxx   删除条目
 *   GET    /admin/api/events?limit=&did=&event=&status=   近期事件（含校验结果）
 *   GET    /admin/api/stats                     质量统计
 *   GET    /admin/api/coverage                  登记覆盖率（live 条目 vs 已见上报）
 *   GET    /admin/api/stream                    SSE 实时事件流
 *   POST   /admin/api/clear                     清空联调缓冲
 *   POST   /admin/api/assert                    联调用例断言（期望序列 vs 实报）
 */
public final class AdminHandler implements HttpHandler {
    private static final ObjectMapper M = new ObjectMapper();

    private final Registry registry;
    private final EventStore store;
    private final SseHub sse;
    private final GatewayConfig config;
    private final ClickHouseCoverage clickhouse;

    public AdminHandler(Registry registry, EventStore store, SseHub sse, GatewayConfig config) {
        this.registry = registry;
        this.store = store;
        this.sse = sse;
        this.config = config;
        this.clickhouse = new ClickHouseCoverage(config.clickhouseUrl);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Http.handlePreflight(ex)) return;
        String role = Http.adminRole(ex, config);
        if (role == null) {
            Http.unauthorizedBasic(ex);
            return;
        }
        String path = ex.getRequestURI().getPath().substring("/admin/api".length());
        String method = ex.getRequestMethod();
        // 写操作（注册表增删改、清缓冲）仅限 admin；viewer 只读
        boolean isWrite = (path.startsWith("/registry/") && !method.equals("GET"))
                || path.equals("/clear");
        if (isWrite && !role.equals("admin")) {
            Http.json(ex, 403, "{\"error\":\"只读账号无权修改，请用管理员账号\"}");
            return;
        }
        try {
            route(ex, method, path);
        } catch (Exception e) {
            Http.json(ex, 500, M.writeValueAsString(Map.of("error", String.valueOf(e.getMessage()))));
        }
    }

    private void route(HttpExchange ex, String method, String path) throws IOException {
        if (path.equals("/registry") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(registry.all()));

        } else if (path.startsWith("/registry/")) {
            String kind = path.substring("/registry/".length());
            if (!Map.of("params", 1, "pages", 1, "elements", 1, "events", 1).containsKey(kind)) {
                Http.json(ex, 404, "{\"error\":\"unknown kind\"}");
                return;
            }
            if (method.equals("POST")) {
                Map<String, Object> item = M.readValue(Http.readBody(ex), new TypeReference<>() { });
                String err = registry.upsert(kind, item);
                if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
                else Http.json(ex, 200, "{\"ok\":true}");
            } else if (method.equals("DELETE")) {
                String key = Http.query(ex).getOrDefault("key", "");
                String err = registry.delete(kind, key);
                if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
                else Http.json(ex, 200, "{\"ok\":true}");
            } else {
                Http.empty(ex, 405);
            }

        } else if (path.equals("/events") && method.equals("GET")) {
            var q = Http.query(ex);
            int limit = Integer.parseInt(q.getOrDefault("limit", "200"));
            Http.json(ex, 200, M.writeValueAsString(store.recent(
                    limit, q.getOrDefault("did", ""), q.getOrDefault("event", ""),
                    q.getOrDefault("status", ""))));

        } else if (path.equals("/stats") && method.equals("GET")) {
            Http.json(ex, 200, store.stats().toString());

        } else if (path.equals("/coverage") && method.equals("GET")) {
            String env = Http.query(ex).getOrDefault("env", "prod");
            var pgids = merge(store.seenPgids(), clickhouse.seenPgids(env));
            var eids = merge(store.seenEids(), clickhouse.seenEids(env));
            var codes = merge(store.seenBizCodes(), clickhouse.seenBizCodes(env));
            Http.json(ex, 200, registry.coverage(pgids, eids, codes).toString());

        } else if (path.equals("/hourly") && method.equals("GET")) {
            Http.json(ex, 200, store.hourly().toString());

        } else if (path.equals("/assert") && method.equals("POST")) {
            assertCase(ex);

        } else if (path.equals("/stream") && method.equals("GET")) {
            sse.subscribe(ex); // 长连接，不关闭

        } else if (path.equals("/clear") && method.equals("POST")) {
            store.clearRecent();
            Http.json(ex, 200, "{\"ok\":true}");

        } else {
            Http.empty(ex, 404);
        }
    }

    private static java.util.Set<String> merge(java.util.Set<String> a, java.util.Set<String> b) {
        var out = new java.util.HashSet<>(a);
        out.addAll(b);
        return out;
    }

    /**
     * 联调用例断言：声明期望事件序列，与该设备实报事件按序比对（有序子序列匹配）。
     *
     * 请求: { "did": "...", "expect": [ { "event":"page_enter", "pgid":"home" },
     *                                   { "event":"element_click", "eid":"video_card" },
     *                                   { "event":"biz_event", "code":"video_play_start" } ] }
     * 响应: { "pass": bool, "matched": n, "expected": m,
     *         "detail": [ { "expect":..., "hit": true, "at": 实报序号 } | { "expect":..., "hit": false } ] }
     */
    private void assertCase(HttpExchange ex) throws IOException {
        var req = M.readTree(Http.readBody(ex));
        String did = req.path("did").asText("");
        var expect = req.path("expect");
        if (did.isEmpty() || !expect.isArray() || expect.isEmpty()) {
            Http.json(ex, 400, "{\"error\":\"需要 did 和非空 expect 数组\"}");
            return;
        }
        var actual = store.recentByDid(did);
        var detail = M.createArrayNode();
        int cursor = 0, matched = 0;
        for (var exp : expect) {
            int hitAt = -1;
            for (int i = cursor; i < actual.size(); i++) {
                var data = actual.get(i).path("data");
                if (!data.path("event").asText().equals(exp.path("event").asText())) continue;
                if (exp.has("pgid") && !data.path("page").path("pgid").asText()
                        .equals(exp.path("pgid").asText())) continue;
                if (exp.has("eid") && !data.path("element").path("eid").asText()
                        .equals(exp.path("eid").asText())) continue;
                if (exp.has("code") && !data.path("biz").path("code").asText()
                        .equals(exp.path("code").asText())) continue;
                hitAt = i;
                break;
            }
            var d = M.createObjectNode();
            d.set("expect", exp);
            d.put("hit", hitAt >= 0);
            if (hitAt >= 0) {
                d.put("at", hitAt);
                cursor = hitAt + 1; // 有序匹配：下一条期望只能命中其后的实报
                matched++;
            }
            detail.add(d);
        }
        var resp = M.createObjectNode();
        resp.put("pass", matched == expect.size());
        resp.put("matched", matched);
        resp.put("expected", expect.size());
        resp.put("actual_events", actual.size());
        resp.set("detail", detail);
        Http.json(ex, 200, resp.toString());
    }
}
