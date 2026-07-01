package com.giso.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giso.gateway.auth.AdminAuth;
import com.giso.gateway.auth.AdminPermissions;
import com.giso.gateway.registry.RegistryKinds;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * 管理 REST API（角色：admin / editor / viewer）。
 *   GET    /admin/api/me
 *   GET/POST/PUT/DELETE /admin/api/users[/{username}]
 *   GET    /admin/api/registry/pending
 *   POST   /admin/api/registry/{kind}/approve|reject|publish|deprecate
 */
public final class AdminHandler implements HttpHandler {
    private static final ObjectMapper M = new ObjectMapper();

    private final Registry registry;
    private final EventStore store;
    private final SseHub sse;
    private final AdminAuth auth;
    private final ClickHouseCoverage clickhouse;

    public AdminHandler(Registry registry, EventStore store, SseHub sse, AdminAuth auth,
                        GatewayConfig config) {
        this.registry = registry;
        this.store = store;
        this.sse = sse;
        this.auth = auth;
        this.clickhouse = new ClickHouseCoverage(config.clickhouseUrl);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Http.handlePreflight(ex)) return;
        String path = ex.getRequestURI().getPath().substring("/admin/api".length());
        String method = ex.getRequestMethod();
        try {
            if (path.equals("/login") && method.equals("POST")) {
                routeLogin(ex);
                return;
            }
            if (path.equals("/logout") && method.equals("POST")) {
                auth.logout(ex);
                Http.json(ex, 200, "{\"ok\":true}");
                return;
            }
            if (auth.unauthorized(ex)) {
                Http.unauthorizedJson(ex);
                return;
            }
            String role = auth.role(ex);
            if (!authorize(ex, role, method, path)) return;
            route(ex, method, path, role);
        } catch (Exception e) {
            Http.json(ex, 500, M.writeValueAsString(Map.of("error", String.valueOf(e.getMessage()))));
        }
    }

    private void routeLogin(HttpExchange ex) throws Exception {
        if (!auth.authEnabled()) {
            Http.json(ex, 400, "{\"error\":\"当前环境未启用登录\"}");
            return;
        }
        Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
        String username = str(body, "username");
        String password = str(body, "password");
        if (username.isBlank() || password.isBlank()) {
            Http.json(ex, 400, "{\"error\":\"用户名和密码不能为空\"}");
            return;
        }
        if (!auth.login(ex, username, password)) {
            Http.json(ex, 401, "{\"error\":\"用户名或密码错误\"}");
            return;
        }
        var me = auth.me(ex);
        me.put("pending_count", registry.pendingCount());
        Http.json(ex, 200, M.writeValueAsString(me));
    }

    private boolean authorize(HttpExchange ex, String role, String method, String path) throws IOException {
        if (path.equals("/me") && method.equals("GET")) return true;
        if (path.startsWith("/users")) {
            if (method.equals("GET") && path.equals("/users")) {
                return require(ex, role, AdminPermissions.canManageUsers(role));
            }
            if (!AdminPermissions.canManageUsers(role)) {
                Http.json(ex, 403, "{\"error\":\"仅管理员可管理账号\"}");
                return false;
            }
            return true;
        }
        if (path.equals("/registry/pending") && method.equals("GET")) return true;
        if (path.equals("/clear") && method.equals("POST")) {
            return require(ex, role, AdminPermissions.canClearBuffer(role));
        }
        if (path.startsWith("/registry/")) {
            String rest = path.substring("/registry/".length());
            String action = rest.contains("/") ? rest.substring(rest.indexOf('/') + 1) : null;
            if ("approve".equals(action) || "reject".equals(action)) {
                return require(ex, role, AdminPermissions.canApproveRegistry(role));
            }
            if ("publish".equals(action) || "deprecate".equals(action)) {
                return require(ex, role, AdminPermissions.canPublishRegistry(role));
            }
            if (path.equals("/registry/reload") && method.equals("POST")) {
                return require(ex, role, AdminPermissions.canPublishRegistry(role));
            }
            if (method.equals("POST") && action == null) {
                return require(ex, role, AdminPermissions.canEditRegistry(role));
            }
            if (method.equals("DELETE")) {
                return require(ex, role, AdminPermissions.canEditRegistry(role));
            }
            return true;
        }
        return true;
    }

    private static boolean require(HttpExchange ex, String role, boolean ok) throws IOException {
        if (ok) return true;
        Http.json(ex, 403, "{\"error\":\"当前角色（" + role + "）无权执行此操作\"}");
        return false;
    }

    private void route(HttpExchange ex, String method, String path, String role) throws Exception {
        if (path.equals("/me") && method.equals("GET")) {
            var me = auth.me(ex);
            if (me == null) {
                Http.unauthorizedJson(ex);
                return;
            }
            me.put("pending_count", registry.pendingCount());
            Http.json(ex, 200, M.writeValueAsString(me));
            return;
        }
        if (path.startsWith("/users")) {
            routeUsers(ex, method, path);
            return;
        }
        if (path.equals("/registry/pending") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(registry.pending()));
            return;
        }
        if (path.equals("/registry") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(registry.all()));

        } else if (path.equals("/registry/meta") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(registry.meta()));

        } else if (path.equals("/registry/audit") && method.equals("GET")) {
            var q = Http.query(ex);
            int limit = Integer.parseInt(q.getOrDefault("limit", "50"));
            Http.json(ex, 200, M.writeValueAsString(registry.audit(
                    q.getOrDefault("kind", ""), q.getOrDefault("key", ""), limit)));

        } else if (path.equals("/registry/reload") && method.equals("POST")) {
            registry.reload();
            Http.json(ex, 200, "{\"ok\":true}");

        } else if (path.startsWith("/registry/")) {
            routeRegistry(ex, method, path, role);

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
            sse.subscribe(ex);

        } else if (path.equals("/clear") && method.equals("POST")) {
            store.clearRecent();
            Http.json(ex, 200, "{\"ok\":true}");

        } else {
            Http.empty(ex, 404);
        }
    }

    private void routeUsers(HttpExchange ex, String method, String path) throws Exception {
        if (path.equals("/users") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(auth.listUsers()));
            return;
        }
        if (path.equals("/users") && method.equals("POST")) {
            Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
            String err = auth.saveUser(
                    str(body, "username"),
                    str(body, "password"),
                    str(body, "role"),
                    str(body, "display_name"));
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if (path.startsWith("/users/")) {
            String username = path.substring("/users/".length());
            if (username.isBlank()) {
                Http.empty(ex, 404);
                return;
            }
            if (method.equals("PUT")) {
                Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
                String pass = body.containsKey("password") ? str(body, "password") : null;
                if (pass != null && pass.isBlank()) pass = null;
                String err = auth.saveUser(username, pass, str(body, "role"), str(body, "display_name"));
                if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
                else Http.json(ex, 200, "{\"ok\":true}");
                return;
            }
            if (method.equals("DELETE")) {
                String err = auth.disableUser(username);
                if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
                else Http.json(ex, 200, "{\"ok\":true}");
                return;
            }
        }
        Http.empty(ex, 405);
    }

    private void routeRegistry(HttpExchange ex, String method, String path, String role) throws Exception {
        String rest = path.substring("/registry/".length());
        String[] slash = rest.split("/", 2);
        String kind = slash[0];
        String action = slash.length > 1 ? slash[1] : null;
        if (!RegistryKinds.isKnown(kind)) {
            Http.json(ex, 404, "{\"error\":\"unknown kind\"}");
            return;
        }
        String operator = auth.operator(ex);
        String key = Http.query(ex).getOrDefault("key", "");
        if ("approve".equals(action) && method.equals("POST")) {
            String err = registry.approve(kind, key, operator);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if ("reject".equals(action) && method.equals("POST")) {
            String err = registry.reject(kind, key, operator);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if ("publish".equals(action) && method.equals("POST")) {
            String err = registry.publish(kind, key, operator);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if ("deprecate".equals(action) && method.equals("POST")) {
            String err = registry.deprecate(kind, key, operator);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if (action != null) {
            Http.json(ex, 404, "{\"error\":\"unknown action\"}");
            return;
        }
        if (method.equals("POST")) {
            Map<String, Object> item = M.readValue(Http.readBody(ex), new TypeReference<>() { });
            String err = registry.upsert(kind, item, operator, role);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
        } else if (method.equals("DELETE")) {
            String err = registry.delete(kind, key, operator, role);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
        } else {
            Http.empty(ex, 405);
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static java.util.Set<String> merge(java.util.Set<String> a, java.util.Set<String> b) {
        var out = new java.util.HashSet<>(a);
        out.addAll(b);
        return out;
    }

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
                cursor = hitAt + 1;
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
