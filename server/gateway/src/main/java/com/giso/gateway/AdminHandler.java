package com.giso.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giso.gateway.assistant.AssistantService;
import com.giso.gateway.assistant.ChatMessage;
import com.giso.gateway.auth.AdminAuth;
import com.giso.gateway.auth.AdminPermissions;
import com.giso.gateway.auth.AdminUser;
import com.giso.gateway.auth.AuthContext;
import com.giso.gateway.registry.RegistryKinds;
import com.giso.gateway.registry.RegistryImportTemplates;
import com.giso.gateway.settings.SystemSettingsService;
import com.giso.gateway.space.SpaceService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理 REST API（平台 system_admin + 空间角色）。
 *   GET    /admin/api/me
 *   GET/POST /admin/api/spaces[...]
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
    private final SpaceService spaces;
    private final AssistantService assistant;
    private final SystemSettingsService systemSettings;

    public AdminHandler(Registry registry, EventStore store, SseHub sse, AdminAuth auth,
                        GatewayConfig config, SpaceService spaces,
                        SystemSettingsService systemSettings) throws Exception {
        this.registry = registry;
        this.store = store;
        this.sse = sse;
        this.auth = auth;
        this.spaces = spaces;
        this.clickhouse = new ClickHouseCoverage(config.clickhouseUrl);
        this.systemSettings = systemSettings;
        this.assistant = new AssistantService(systemSettings, registry);
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
            String globalRole = auth.role(ex);
            String spaceKey = Http.spaceKey(ex);
            String spaceRole = resolveSpaceRole(ex, globalRole, spaceKey);
            if (spaceRole == null && auth.authEnabled() && spaces != null
                    && !AdminUser.isSystemAdmin(globalRole)) {
                Http.json(ex, 403, "{\"error\":\"无权访问该空间\"}");
                return;
            }
            if (!authorize(ex, globalRole, spaceRole, method, path)) return;
            route(ex, method, path, globalRole, spaceRole, spaceKey);
        } catch (Exception e) {
            Http.json(ex, 500, M.writeValueAsString(Map.of("error", String.valueOf(e.getMessage()))));
        }
    }

    private String resolveSpaceRole(HttpExchange ex, String globalRole, String spaceKey) throws Exception {
        if (spaces == null) {
            return globalRole == null ? AdminUser.ROLE_ADMIN : globalRole;
        }
        String username = auth.operator(ex);
        return spaces.spaceRole(username, globalRole, spaceKey);
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
        Optional<AuthContext> ctx = auth.login(ex, username, password);
        if (ctx.isEmpty()) {
            Http.json(ex, 401, "{\"error\":\"用户名或密码错误\",\"code\":\"invalid_credentials\"}");
            return;
        }
        String spaceKey = Http.spaceKey(ex);
        var profile = auth.userProfile(ctx.get(), spaceKey, registry.pendingCount(spaceKey));
        profile.put("ok", true);
        Http.json(ex, 200, M.writeValueAsString(profile));
    }

    private boolean authorize(HttpExchange ex, String globalRole, String spaceRole,
            String method, String path) throws IOException {
        if (path.equals("/me") && method.equals("GET")) return true;
        if (path.equals("/spaces") && method.equals("GET")) return true;
        if (path.startsWith("/spaces")) {
            if (path.equals("/spaces") && method.equals("POST")) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canManageSpaces(globalRole), "仅平台管理员可创建空间");
            }
            if (path.endsWith("/members")) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canManageSpaceMembers(globalRole, spaceRole), "无权管理空间成员");
            }
            if (path.endsWith("/member-candidates")) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canManageSpaceMembers(globalRole, spaceRole), "无权管理空间成员");
            }
            if (path.endsWith("/app-keys")) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canManageSpaceMembers(globalRole, spaceRole), "无权管理 App Key");
            }
            return true;
        }
        if (path.startsWith("/users")) {
            if (method.equals("GET") && path.equals("/users")) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canManageUsers(globalRole), "仅平台管理员可管理账号");
            }
            if (!AdminPermissions.canManageUsers(globalRole)) {
                Http.json(ex, 403, "{\"error\":\"仅平台管理员可管理账号\"}");
                return false;
            }
            return true;
        }
        if (path.equals("/registry/pending") && method.equals("GET")) return true;
        if (path.equals("/registry/import-template") && method.equals("GET")) return true;
        if (path.equals("/registry/batch") && method.equals("POST")) return true;
        if (path.equals("/registry/import") && method.equals("POST")) {
            return require(ex, globalRole, spaceRole,
                    AdminPermissions.canEditRegistry(globalRole, spaceRole), "无权导入注册表");
        }
        if (path.startsWith("/assistant/")) return true;
        if (path.startsWith("/settings")) {
            if (method.equals("GET")) return true;
            return require(ex, globalRole, spaceRole,
                    AdminPermissions.canManageSystemSettings(globalRole), "仅平台管理员可修改系统设置");
        }
        if (path.equals("/clear") && method.equals("POST")) {
            return require(ex, globalRole, spaceRole,
                    AdminPermissions.canClearBuffer(globalRole, spaceRole), "无权清空缓冲");
        }
        if (path.startsWith("/registry/")) {
            String rest = path.substring("/registry/".length());
            String action = rest.contains("/") ? rest.substring(rest.indexOf('/') + 1) : null;
            if ("approve".equals(action) || "reject".equals(action)) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canApproveRegistry(globalRole, spaceRole), "无权审批");
            }
            if ("publish".equals(action) || "deprecate".equals(action)) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canPublishRegistry(globalRole, spaceRole), "无权发布");
            }
            if (path.equals("/registry/reload") && method.equals("POST")) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canPublishRegistry(globalRole, spaceRole), "无权重载");
            }
            if (method.equals("POST") && action == null) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canEditRegistry(globalRole, spaceRole), "无权编辑注册表");
            }
            if (method.equals("DELETE")) {
                return require(ex, globalRole, spaceRole,
                        AdminPermissions.canEditRegistry(globalRole, spaceRole), "无权删除");
            }
            return true;
        }
        return true;
    }

    private static boolean require(HttpExchange ex, String globalRole, String spaceRole,
            boolean ok, String msg) throws IOException {
        if (ok) return true;
        String label = spaceRole != null ? spaceRole : globalRole;
        Http.json(ex, 403, "{\"error\":\"" + msg + "（当前角色 " + label + "）\"}");
        return false;
    }

    private void route(HttpExchange ex, String method, String path, String globalRole,
            String spaceRole, String spaceKey) throws Exception {
        if (path.equals("/me") && method.equals("GET")) {
            var profile = auth.userProfile(ex, spaceKey, registry.pendingCount(spaceKey));
            if (profile == null) {
                Http.unauthorizedJson(ex);
                return;
            }
            Http.json(ex, 200, M.writeValueAsString(profile));
            return;
        }
        if (path.startsWith("/spaces")) {
            routeSpaces(ex, method, path, spaceKey);
            return;
        }
        if (path.startsWith("/users")) {
            routeUsers(ex, method, path);
            return;
        }
        if (path.equals("/registry/pending") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(registry.pending(spaceKey)));
            return;
        }
        if (path.equals("/assistant/status") && method.equals("GET")) {
            var st = new java.util.LinkedHashMap<String, Object>();
            st.put("enabled", systemSettings.assistantProvider().ready()
                    || !systemSettings.assistantProvider().name().equals("disabled"));
            st.put("provider", systemSettings.assistantProvider().name());
            st.put("ready", systemSettings.assistantProvider().ready());
            st.put("topics", List.of("product", "tracking_flow", "registry", "deploy", "quarantine"));
            Http.json(ex, 200, M.writeValueAsString(st));
            return;
        }
        if (path.equals("/settings") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(systemSettings.getPublicSettings()));
            return;
        }
        if (path.equals("/settings") && method.equals("PUT")) {
            routeSettingsUpdate(ex);
            return;
        }
        if (path.equals("/assistant/chat") && method.equals("POST")) {
            routeAssistantChat(ex, spaceKey);
            return;
        }
        if (path.equals("/registry/meta") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(registry.meta()));

        } else if (path.equals("/registry") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(registry.all(spaceKey)));

        } else if (path.equals("/registry/audit") && method.equals("GET")) {
            var q = Http.query(ex);
            int limit = Integer.parseInt(q.getOrDefault("limit", "50"));
            Http.json(ex, 200, M.writeValueAsString(registry.audit(
                    spaceKey, q.getOrDefault("kind", ""), q.getOrDefault("key", ""), limit)));

        } else if (path.equals("/registry/reload") && method.equals("POST")) {
            registry.reload();
            Http.json(ex, 200, "{\"ok\":true}");

        } else if (path.equals("/registry/visual-draft") && method.equals("POST")) {
            routeVisualDraft(ex, spaceKey, spaceRole);

        } else if (path.equals("/registry/import-template") && method.equals("GET")) {
            routeImportTemplate(ex);

        } else if (path.equals("/registry/import") && method.equals("POST")) {
            routeImport(ex, spaceKey, spaceRole);

        } else if (path.equals("/registry/batch") && method.equals("POST")) {
            routeBatch(ex, spaceKey, globalRole, spaceRole);

        } else if (path.startsWith("/registry/")) {
            routeRegistry(ex, method, path, spaceKey, spaceRole);

        } else if (path.equals("/events") && method.equals("GET")) {
            var q = Http.query(ex);
            int limit = Integer.parseInt(q.getOrDefault("limit", "200"));
            Http.json(ex, 200, M.writeValueAsString(store.recent(
                    limit, spaceKey, q.getOrDefault("did", ""), q.getOrDefault("event", ""),
                    q.getOrDefault("status", ""))));

        } else if (path.equals("/stats") && method.equals("GET")) {
            Http.json(ex, 200, store.stats(spaceKey).toString());

        } else if (path.equals("/coverage") && method.equals("GET")) {
            String env = Http.query(ex).getOrDefault("env", "prod");
            var pgids = merge(store.seenPgids(spaceKey), clickhouse.seenPgids(env, spaceKey));
            var eids = merge(store.seenEids(spaceKey), clickhouse.seenEids(env, spaceKey));
            var codes = merge(store.seenBizCodes(spaceKey), clickhouse.seenBizCodes(env, spaceKey));
            Http.json(ex, 200, registry.coverage(spaceKey, pgids, eids, codes).toString());

        } else if (path.equals("/hourly") && method.equals("GET")) {
            Http.json(ex, 200, store.hourly(spaceKey).toString());

        } else if (path.equals("/assert") && method.equals("POST")) {
            assertCase(ex);

        } else if (path.equals("/stream") && method.equals("GET")) {
            sse.subscribe(ex, spaceKey);

        } else if (path.equals("/clear") && method.equals("POST")) {
            store.clearRecent(spaceKey);
            Http.json(ex, 200, "{\"ok\":true}");

        } else {
            Http.empty(ex, 404);
        }
    }

    private void routeSpaces(HttpExchange ex, String method, String path, String spaceKey) throws Exception {
        if (spaces == null) {
            Http.json(ex, 400, "{\"error\":\"当前环境未启用空间（需 PostgreSQL）\"}");
            return;
        }
        if (path.equals("/spaces") && method.equals("GET")) {
            Http.json(ex, 200, M.writeValueAsString(spaces.listSpaces()));
            return;
        }
        if (path.equals("/spaces") && method.equals("POST")) {
            Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
            String err = spaces.createSpace(str(body, "space_key"), str(body, "display_name"));
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if (path.startsWith("/spaces/")) {
            String rest = path.substring("/spaces/".length());
            if (rest.endsWith("/member-candidates")) {
                String sk = rest.substring(0, rest.length() - "/member-candidates".length());
                if (method.equals("GET")) {
                    Http.json(ex, 200, M.writeValueAsString(spaces.listMemberCandidates(sk)));
                    return;
                }
            }
            if (rest.endsWith("/members")) {
                String sk = rest.substring(0, rest.length() - "/members".length());
                if (method.equals("GET")) {
                    Http.json(ex, 200, M.writeValueAsString(spaces.listMembers(sk)));
                    return;
                }
                if (method.equals("POST")) {
                    Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
                    String username = str(body, "username");
                    String role = str(body, "role");
                    String result = spaces.saveMember(sk, username, role);
                    if (result != null && !result.startsWith("ADDED:") && !result.startsWith("UPDATED:")) {
                        Http.json(ex, 400, M.writeValueAsString(Map.of("error", result)));
                        return;
                    }
                    Map<String, Object> ok = new java.util.LinkedHashMap<>();
                    ok.put("ok", true);
                    if (result != null && result.startsWith("UPDATED:")) {
                        ok.put("message", "已更新成员「" + result.substring(8) + "」的空间角色");
                    } else {
                        String name = result != null && result.startsWith("ADDED:")
                                ? result.substring(6) : username;
                        ok.put("message", "已添加成员「" + name + "」");
                    }
                    ok.put("role", role);
                    Http.json(ex, 200, M.writeValueAsString(ok));
                    return;
                }
                if (method.equals("DELETE")) {
                    String username = Http.query(ex).getOrDefault("username", "");
                    String err = spaces.removeMember(sk, username);
                    if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
                    else Http.json(ex, 200, "{\"ok\":true}");
                    return;
                }
            }
            if (rest.endsWith("/app-keys")) {
                String sk = rest.substring(0, rest.length() - "/app-keys".length());
                if (method.equals("GET")) {
                    Http.json(ex, 200, M.writeValueAsString(spaces.listAppKeys(sk)));
                    return;
                }
                if (method.equals("POST")) {
                    Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
                    String err = spaces.bindAppKey(sk, str(body, "app_key"));
                    if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
                    else {
                        spaces.reloadAppKeys();
                        Http.json(ex, 200, "{\"ok\":true}");
                    }
                    return;
                }
            }
        }
        Http.empty(ex, 404);
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

    private void routeAssistantChat(HttpExchange ex, String spaceKey) throws Exception {
        var body = M.readValue(Http.readBody(ex), new TypeReference<Map<String, Object>>() { });
        String message = str(body, "message");
        if (message.isEmpty()) {
            Http.json(ex, 400, "{\"error\":\"message required\"}");
            return;
        }
        String topic = str(body, "topic");
        List<ChatMessage> history = List.of();
        if (body.get("history") instanceof List<?> raw) {
            List<ChatMessage> parsed = new ArrayList<>();
            for (Object o : raw) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String role = String.valueOf(m.get("role"));
                String content = String.valueOf(m.get("content"));
                parsed.add(new ChatMessage(role, content));
            }
            history = parsed;
        }
        try {
            var resp = assistant.chat(message, history, topic, spaceKey);
            Http.json(ex, 200, M.writeValueAsString(Map.of(
                    "answer", resp.answer(),
                    "provider", resp.provider(),
                    "sources", resp.sources(),
                    "suggested_followups", resp.suggestedFollowups())));
        } catch (Exception e) {
            Http.json(ex, 502, M.writeValueAsString(Map.of(
                    "error", e.getMessage(),
                    "provider", systemSettings.assistantProvider().name())));
        }
    }

    private void routeSettingsUpdate(HttpExchange ex) throws Exception {
        Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
        try {
            var result = systemSettings.update(body, auth.operator(ex));
            Http.json(ex, 200, M.writeValueAsString(result));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Http.json(ex, 400, M.writeValueAsString(Map.of("error", e.getMessage())));
        }
    }

    private void routeVisualDraft(HttpExchange ex, String spaceKey, String spaceRole) throws Exception {
        Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
        String pgid = str(body, "pgid");
        if (pgid.isEmpty()) {
            Http.json(ex, 400, "{\"error\":\"需要 pgid\"}");
            return;
        }
        String operator = auth.operator(ex);
        List<String> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Object els = body.get("elements");
        if (els instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> raw)) continue;
                Map<String, Object> el = new HashMap<>();
                raw.forEach((k, v) -> el.put(String.valueOf(k), v));
                el.putIfAbsent("status", "draft");
                el.putIfAbsent("domain", str(body, "domain").isEmpty() ? "common" : str(body, "domain"));
                el.putIfAbsent("owner", operator != null ? operator : "visual-picker");
                el.putIfAbsent("params", List.of());
                String eid = str(el, "eid");
                if (eid.isEmpty()) {
                    errors.add("缺少 eid");
                    continue;
                }
                String err = registry.upsert(spaceKey, "elements", el, operator, spaceRole);
                if (err != null) errors.add(eid + ": " + err);
                else created.add(eid);
            }
        }

        Map<String, Object> pageUpdate = null;
        for (Map<String, Object> p : registry.all(spaceKey).getOrDefault("pages", List.of())) {
            if (pgid.equals(str(p, "pgid"))) {
                pageUpdate = new HashMap<>(p);
                break;
            }
        }
        if (pageUpdate == null) {
            pageUpdate = new HashMap<>();
            pageUpdate.put("pgid", pgid);
            pageUpdate.put("desc", str(body, "desc").isEmpty() ? pgid : str(body, "desc"));
            pageUpdate.put("domain", str(body, "domain").isEmpty() ? "common" : str(body, "domain"));
            pageUpdate.put("status", "draft");
            pageUpdate.put("params", List.of());
            pageUpdate.put("elements", new ArrayList<String>());
        }
        String screenshot = str(body, "screenshot");
        if (!screenshot.isEmpty()) pageUpdate.put("screenshot", screenshot);

        @SuppressWarnings("unchecked")
        List<String> bound = pageUpdate.get("elements") instanceof List<?> bl
                ? new ArrayList<>((List<String>) bl.stream().map(String::valueOf).toList())
                : new ArrayList<>();
        for (String e : created) {
            if (!bound.contains(e)) bound.add(e);
        }
        pageUpdate.put("elements", bound);

        String perr = registry.upsert(spaceKey, "pages", pageUpdate, operator, spaceRole);
        if (perr != null) errors.add("page: " + perr);

        Http.json(ex, 200, M.writeValueAsString(Map.of(
                "ok", errors.isEmpty(),
                "created_elements", created,
                "errors", errors)));
    }

    private void routeImportTemplate(HttpExchange ex) throws IOException {
        String kind = Http.query(ex).getOrDefault("kind", "");
        if (!RegistryImportTemplates.supports(kind)) {
            Http.json(ex, 400, "{\"error\":\"未知 kind\"}");
            return;
        }
        String csv = RegistryImportTemplates.csvTemplate(kind);
        String filename = RegistryKinds.yamlFile(kind).replace(".yaml", "_import_template.csv");
        Http.csvAttachment(ex, filename, csv);
    }

    private void routeImport(HttpExchange ex, String spaceKey, String spaceRole) throws Exception {
        Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
        String kind = str(body, "kind");
        if (!RegistryKinds.isKnown(kind)) {
            Http.json(ex, 400, "{\"error\":\"未知 kind\"}");
            return;
        }
        List<Map<String, Object>> items;
        if (body.get("items") instanceof List<?> list) {
            items = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) m;
                    items.add(row);
                }
            }
        } else {
            String csv = str(body, "csv");
            if (csv.isBlank()) {
                Http.json(ex, 400, "{\"error\":\"需要 items 或 csv 字段\"}");
                return;
            }
            items = RegistryImportTemplates.parseCsv(kind, csv);
        }
        if (items.isEmpty()) {
            Http.json(ex, 400, "{\"error\":\"没有可导入的条目\"}");
            return;
        }
        if (items.size() > 500) {
            Http.json(ex, 400, "{\"error\":\"单次最多导入 500 条\"}");
            return;
        }
        String operator = auth.operator(ex);
        Http.json(ex, 200, M.writeValueAsString(registry.importItems(spaceKey, kind, items, operator, spaceRole)));
    }

    private void routeBatch(HttpExchange ex, String spaceKey, String globalRole, String spaceRole)
            throws Exception {
        Map<String, Object> body = M.readValue(Http.readBody(ex), new TypeReference<>() { });
        String kind = str(body, "kind");
        String action = str(body, "action");
        if (!RegistryKinds.isKnown(kind)) {
            Http.json(ex, 400, "{\"error\":\"未知 kind\"}");
            return;
        }
        boolean allowed = switch (action) {
            case "submit", "delete" -> AdminPermissions.canEditRegistry(globalRole, spaceRole);
            case "approve", "reject" -> AdminPermissions.canApproveRegistry(globalRole, spaceRole);
            case "publish", "deprecate" -> AdminPermissions.canPublishRegistry(globalRole, spaceRole);
            default -> false;
        };
        if (!allowed) {
            Http.json(ex, 403, "{\"error\":\"当前角色无权执行: " + action + "\"}");
            return;
        }
        List<String> keys = new ArrayList<>();
        if (body.get("keys") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) keys.add(String.valueOf(o).trim());
            }
        }
        if (keys.isEmpty()) {
            Http.json(ex, 400, "{\"error\":\"keys 不能为空\"}");
            return;
        }
        if (keys.size() > 200) {
            Http.json(ex, 400, "{\"error\":\"单次最多操作 200 条\"}");
            return;
        }
        String operator = auth.operator(ex);
        Http.json(ex, 200, M.writeValueAsString(
                registry.batch(spaceKey, kind, keys, action, operator, spaceRole)));
    }

    private void routeRegistry(HttpExchange ex, String method, String path,
            String spaceKey, String spaceRole) throws Exception {
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
            String err = registry.approve(spaceKey, kind, key, operator);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if ("reject".equals(action) && method.equals("POST")) {
            String err = registry.reject(spaceKey, kind, key, operator);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if ("publish".equals(action) && method.equals("POST")) {
            String err = registry.publish(spaceKey, kind, key, operator);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
            return;
        }
        if ("deprecate".equals(action) && method.equals("POST")) {
            String err = registry.deprecate(spaceKey, kind, key, operator);
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
            String err = registry.upsert(spaceKey, kind, item, operator, spaceRole);
            if (err != null) Http.json(ex, 400, M.writeValueAsString(Map.of("error", err)));
            else Http.json(ex, 200, "{\"ok\":true}");
        } else if (method.equals("DELETE")) {
            String err = registry.delete(spaceKey, kind, key, operator, spaceRole);
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
