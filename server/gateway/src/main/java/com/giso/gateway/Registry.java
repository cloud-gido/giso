package com.giso.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.auth.AdminPermissions;
import com.giso.gateway.auth.AdminUser;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.giso.gateway.registry.RegistryBundle;
import com.giso.gateway.registry.RegistryKinds;
import com.giso.gateway.registry.RegistryRefs;
import com.giso.gateway.registry.RegistrySnapshot;
import com.giso.gateway.registry.RegistryStore;
import com.giso.gateway.registry.RegistryStores;
import com.giso.gateway.registry.WriteResult;
import com.giso.gateway.space.SpaceService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 注册表：按空间隔离的内存快照 + 事件校验；持久化委托 {@link RegistryStore}。
 */
public final class Registry {
    private static final ObjectMapper M = new ObjectMapper();
    public static final java.util.Set<String> STANDARD_EVENTS = java.util.Set.of(
            "app_install", "app_launch", "app_foreground", "app_background",
            "page_enter", "page_exit", "element_exposure", "element_click", "biz_event");

    private static final Pattern SNAKE = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");

    private final RegistryStore store;
    private final Map<String, Map<String, Map<String, Map<String, Object>>>> bySpace = new LinkedHashMap<>();
    private volatile long globalRevision;

    private Registry(RegistryStore store) throws Exception {
        this.store = store;
        reload();
    }

    public static Registry create(GatewayConfig config) throws Exception {
        return new Registry(RegistryStores.create(config));
    }

    public synchronized void reload() throws Exception {
        RegistrySnapshot snap = store.load();
        bySpace.clear();
        bySpace.putAll(snap.bySpace());
        globalRevision = snap.globalRevision();
    }

    private Map<String, Map<String, Map<String, Object>>> tablesFor(String spaceKey) {
        return bySpace.computeIfAbsent(spaceKey, k -> RegistryKinds.emptyTables());
    }

    public String backendName() {
        return store.backendName();
    }

    public long globalRevision() {
        return globalRevision;
    }

    public int entryCount() {
        return bySpace.values().stream()
                .mapToInt(space -> space.values().stream().mapToInt(Map::size).sum())
                .sum();
    }

    public RegistryStore store() {
        return store;
    }

    public synchronized Map<String, List<Map<String, Object>>> all(String spaceKey) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        tablesFor(spaceKey).forEach((k, v) -> out.put(k, new ArrayList<>(v.values())));
        return out;
    }

    public synchronized String upsert(String spaceKey, String kind, Map<String, Object> item, String operator)
            throws Exception {
        return upsert(spaceKey, kind, item, operator, AdminUser.ROLE_SPACE_ADMIN);
    }

    public synchronized String upsert(String spaceKey, String kind, Map<String, Object> item,
            String operator, String spaceRole) throws Exception {
        String err = applyUpsert(spaceKey, kind, item, operator, spaceRole);
        if (err == null) reload();
        return err;
    }

    private synchronized String applyUpsert(String spaceKey, String kind, Map<String, Object> item,
            String operator, String spaceRole) throws Exception {
        String idField = RegistryKinds.idField(kind);
        String key = String.valueOf(item.get(idField));
        Map<String, Object> existing = tablesFor(spaceKey).get(kind).get(key);
        item = new LinkedHashMap<>(item);
        RegistryRefs.normalizeLists(item);

        if (AdminUser.ROLE_EDITOR.equals(spaceRole)) {
            if (existing != null) {
                String est = String.valueOf(existing.getOrDefault("status", "live"));
                if (!AdminPermissions.editorMayEditStatus(est)) {
                    return "编辑员只能修改「待审批/登记中」条目，已生效条目请联系管理员审批";
                }
            }
            item.put("status", "pending");
        } else if ("postgres".equals(store.backendName()) && existing == null) {
            item.putIfAbsent("status", "draft");
        }

        String err = validateUpsert(spaceKey, kind, item);
        if (err != null) return err;
        WriteResult wr = store.upsert(spaceKey, kind, item, operator);
        if (wr.error() != null) return wr.error();
        return null;
    }

    public synchronized String delete(String spaceKey, String kind, String key, String operator) throws Exception {
        return delete(spaceKey, kind, key, operator, AdminUser.ROLE_SPACE_ADMIN);
    }

    public synchronized String delete(String spaceKey, String kind, String key, String operator, String spaceRole)
            throws Exception {
        String err = applyDelete(spaceKey, kind, key, operator, spaceRole);
        if (err == null) reload();
        return err;
    }

    private synchronized String applyDelete(String spaceKey, String kind, String key, String operator, String spaceRole)
            throws Exception {
        Map<String, Object> existing = tablesFor(spaceKey).get(kind).get(key);
        if (AdminUser.ROLE_EDITOR.equals(spaceRole) && existing != null) {
            String est = String.valueOf(existing.getOrDefault("status", "live"));
            if (!AdminPermissions.editorMayEditStatus(est)) {
                return "编辑员只能删除「待审批/登记中」条目";
            }
        }
        WriteResult wr = store.delete(spaceKey, kind, key, operator);
        if (wr.error() != null) return wr.error();
        return null;
    }

    public boolean registryReady() {
        try {
            return store.ping();
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> meta() throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backend", store.backendName());
        out.put("revision", globalRevision);
        out.put("entries", entryCount());
        if (store instanceof PostgresRegistryStore pg) {
            out.put("schema", pg.dbSchema());
        }
        return out;
    }

    public List<Map<String, Object>> audit(String spaceKey, String kind, String key, int limit) throws Exception {
        return store.audit(spaceKey, kind, key, limit);
    }

    public synchronized String publish(String spaceKey, String kind, String key, String operator) throws Exception {
        String err = applyPublish(spaceKey, kind, key, operator);
        if (err == null) reload();
        return err;
    }

    private synchronized String applyPublish(String spaceKey, String kind, String key, String operator)
            throws Exception {
        WriteResult wr = store.publish(spaceKey, kind, key, operator);
        if (wr.error() != null) return wr.error();
        return null;
    }

    public synchronized String deprecate(String spaceKey, String kind, String key, String operator) throws Exception {
        String err = applyDeprecate(spaceKey, kind, key, operator);
        if (err == null) reload();
        return err;
    }

    private synchronized String applyDeprecate(String spaceKey, String kind, String key, String operator)
            throws Exception {
        WriteResult wr = store.deprecate(spaceKey, kind, key, operator);
        if (wr.error() != null) return wr.error();
        return null;
    }

    public synchronized String approve(String spaceKey, String kind, String key, String operator) throws Exception {
        String err = applyApprove(spaceKey, kind, key, operator);
        if (err == null) reload();
        return err;
    }

    private synchronized String applyApprove(String spaceKey, String kind, String key, String operator)
            throws Exception {
        WriteResult wr = store.approve(spaceKey, kind, key, operator);
        if (wr.error() != null) return wr.error();
        return null;
    }

    public synchronized String reject(String spaceKey, String kind, String key, String operator) throws Exception {
        String err = applyReject(spaceKey, kind, key, operator);
        if (err == null) reload();
        return err;
    }

    private synchronized String applyReject(String spaceKey, String kind, String key, String operator)
            throws Exception {
        WriteResult wr = store.reject(spaceKey, kind, key, operator);
        if (wr.error() != null) return wr.error();
        return null;
    }

    /**
     * 批量操作：submit（提交审批）/ approve / reject / publish / deprecate / delete。
     * 单次 reload，返回逐条成败。
     */
    public synchronized Map<String, Object> batch(String spaceKey, String kind, List<String> keys,
            String action, String operator, String spaceRole) throws Exception {
        List<Map<String, String>> errors = new ArrayList<>();
        int ok = 0;
        boolean changed = false;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String err = switch (action) {
                case "submit" -> applySubmit(spaceKey, kind, key.trim(), operator, spaceRole);
                case "approve" -> applyApprove(spaceKey, kind, key.trim(), operator);
                case "reject" -> applyReject(spaceKey, kind, key.trim(), operator);
                case "publish" -> applyPublish(spaceKey, kind, key.trim(), operator);
                case "deprecate" -> applyDeprecate(spaceKey, kind, key.trim(), operator);
                case "delete" -> applyDelete(spaceKey, kind, key.trim(), operator, spaceRole);
                default -> "未知操作: " + action;
            };
            if (err == null) {
                ok++;
                changed = true;
            } else {
                errors.add(Map.of("key", key.trim(), "error", err));
            }
        }
        if (changed) reload();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("failed", errors.size());
        out.put("errors", errors);
        return out;
    }

    /** 批量导入（CSV 解析后的条目列表），单次 reload。 */
    public synchronized Map<String, Object> importItems(String spaceKey, String kind,
            List<Map<String, Object>> items, String operator, String spaceRole) throws Exception {
        List<Map<String, String>> errors = new ArrayList<>();
        int ok = 0;
        boolean changed = false;
        for (Map<String, Object> item : items) {
            String err = applyUpsert(spaceKey, kind, item, operator, spaceRole);
            String idField = RegistryKinds.idField(kind);
            String key = String.valueOf(item.getOrDefault(idField, "?"));
            if (err == null) {
                ok++;
                changed = true;
            } else {
                errors.add(Map.of("key", key, "error", err));
            }
        }
        if (changed) reload();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("failed", errors.size());
        out.put("errors", errors);
        return out;
    }

    /** 导出当前空间 live 条目为整包（测试 → 生产晋升）。 */
    public synchronized Map<String, Object> exportBundle(String spaceKey, String operator) {
        return RegistryBundle.buildExport(spaceKey, all(spaceKey), globalRevision, operator);
    }

    /**
     * 导入整包：强制 status=live，按 params → pages → elements → events 顺序写入。
     * {@code dryRun=true} 时仅校验，不落库。
     */
    public synchronized Map<String, Object> importBundle(String spaceKey, Map<String, Object> bundle,
            boolean dryRun, String operator) throws Exception {
        String shapeErr = RegistryBundle.validateBundleShape(bundle);
        if (shapeErr != null) {
            Map<String, Object> bad = new LinkedHashMap<>();
            bad.put("dry_run", dryRun);
            bad.put("ok", 0);
            bad.put("failed", 1);
            bad.put("created", 0);
            bad.put("updated", 0);
            bad.put("errors", List.of(Map.of("kind", "", "key", "", "error", shapeErr)));
            bad.put("detail", List.of());
            return bad;
        }
        String bundleSpace = String.valueOf(bundle.getOrDefault("space_key", ""));
        String spaceWarn = null;
        if (!bundleSpace.isBlank() && !bundleSpace.equals(spaceKey)) {
            spaceWarn = "包内 space_key=" + bundleSpace + " 与当前空间 " + spaceKey
                    + " 不一致，将写入当前空间";
        }
        List<Map<String, String>> errors = new ArrayList<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int ok = 0;
        boolean changed = false;
        for (String kind : RegistryBundle.KIND_ORDER) {
            for (Map<String, Object> item : RegistryBundle.itemsOf(bundle, kind)) {
                String idField = RegistryKinds.idField(kind);
                String key = String.valueOf(item.getOrDefault(idField, "?"));
                Map<String, Object> existing = tablesFor(spaceKey).get(kind).get(key);
                String action = existing == null ? "create" : "update";
                String err = applyBundleUpsert(spaceKey, kind, item, operator, dryRun);
                if (err == null) {
                    ok++;
                    if ("create".equals(action)) created++;
                    else updated++;
                    if (!dryRun) changed = true;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("kind", kind);
                    row.put("key", key);
                    row.put("action", action);
                    row.put("status", RegistryBundle.STATUS_LIVE);
                    detail.add(row);
                } else {
                    errors.add(Map.of("kind", kind, "key", key, "error", err));
                }
            }
        }
        if (changed) reload();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dry_run", dryRun);
        out.put("ok", ok);
        out.put("failed", errors.size());
        out.put("created", created);
        out.put("updated", updated);
        out.put("errors", errors);
        out.put("detail", detail);
        if (spaceWarn != null) out.put("warning", spaceWarn);
        return out;
    }

    private synchronized String applyBundleUpsert(String spaceKey, String kind, Map<String, Object> item,
            String operator, boolean dryRun) throws Exception {
        String idField = RegistryKinds.idField(kind);
        Object idVal = item.get(idField);
        if (idVal == null || String.valueOf(idVal).isBlank()) {
            return "缺少 " + idField;
        }
        item = new LinkedHashMap<>(item);
        RegistryRefs.normalizeLists(item);
        item.put("status", RegistryBundle.STATUS_LIVE);
        String err = validateUpsert(spaceKey, kind, item);
        if (err != null) return err;
        if (dryRun) return null;
        WriteResult wr = store.upsert(spaceKey, kind, item, operator);
        if (wr.error() != null) return wr.error();
        return null;
    }

    private String applySubmit(String spaceKey, String kind, String key, String operator, String spaceRole)
            throws Exception {
        Map<String, Object> existing = tablesFor(spaceKey).get(kind).get(key);
        if (existing == null) return "条目不存在: " + key;
        String status = String.valueOf(existing.getOrDefault("status", "live"));
        if (AdminUser.ROLE_EDITOR.equals(spaceRole)) {
            if (!AdminPermissions.editorMayEditStatus(status)) {
                return "只能提交「登记中/待审批」条目";
            }
        } else if (!"pending".equals(status)
                && !java.util.Set.of("draft", "dev", "testing").contains(status)) {
            return "只能提交非线上条目（当前: " + status + "）";
        }
        Map<String, Object> item = new LinkedHashMap<>(existing);
        item.put("status", "pending");
        return applyUpsert(spaceKey, kind, item, operator, spaceRole);
    }

    public synchronized Map<String, List<Map<String, Object>>> pending(String spaceKey) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        tablesFor(spaceKey).forEach((kind, map) -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> item : map.values()) {
                if ("pending".equals(String.valueOf(item.get("status")))) {
                    rows.add(item);
                }
            }
            out.put(kind, rows);
        });
        return out;
    }

    public synchronized int pendingCount(String spaceKey) {
        return pending(spaceKey).values().stream().mapToInt(List::size).sum();
    }

    private String validateUpsert(String spaceKey, String kind, Map<String, Object> item) {
        return RegistryRefs.validate(tablesFor(spaceKey), kind, item);
    }

    public synchronized Map<String, Object> refHints(String spaceKey, String kind, Map<String, Object> item) {
        Map<String, Object> draft = item == null ? Map.of() : new LinkedHashMap<>(item);
        RegistryRefs.normalizeLists(draft);
        return RegistryRefs.hints(tablesFor(spaceKey), kind, draft);
    }

    // ── 事件校验 ───────────────────────────────────────────────

    public record Issue(String level, String field, String msg) { }

    public record Result(String status, List<Issue> issues) {
        static Result of(List<Issue> issues) {
            boolean error = issues.stream().anyMatch(i -> i.level().equals("error"));
            boolean missing = issues.stream().anyMatch(i -> i.level().equals("missing"));
            return new Result(error ? "error" : missing ? "missing" : "ok", issues);
        }
    }

    public Result validate(JsonNode ev) {
        return validate(ev, SpaceService.DEFAULT_SPACE);
    }

    public synchronized Result validate(JsonNode ev, String spaceKey) {
        List<Issue> issues = new ArrayList<>();
        var tables = tablesFor(spaceKey);
        String event = text(ev, "event");

        if (!STANDARD_EVENTS.contains(event)) {
            issues.add(new Issue("error", "event", "非标准事件: " + event));
            return Result.of(issues);
        }
        if (text(ev, "log_id").isEmpty()) issues.add(new Issue("missing", "log_id", "缺少 log_id"));
        JsonNode common = ev.get("common");
        if (common == null || !common.isObject()) {
            issues.add(new Issue("error", "common", "缺少公共参数"));
            return Result.of(issues);
        }
        for (String k : new String[]{"app_id", "platform", "did"}) {
            if (text(common, k).isEmpty()) issues.add(new Issue("missing", "common." + k, "公共参数为空: " + k));
        }
        String appVrsn = text(common, "app_vrsn");

        JsonNode page = ev.get("page");
        if (page != null && page.isObject()) {
            String pgid = text(page, "pgid");
            if (!pgid.isEmpty()) {
                Map<String, Object> pg = registeredPage(tables, pgid, appVrsn);
                if (pg == null) {
                    issues.add(new Issue("error", "page.pgid", "未登记页面: " + pgid));
                } else {
                    checkParams(issues, tables, "page.pg_params", page.get("pg_params"), requiredParams(pg));
                }
            } else if (event.startsWith("page_") || event.startsWith("element_")) {
                issues.add(new Issue("missing", "page.pgid", "页面事件缺少 pgid"));
            }
        }

        if (event.startsWith("element_")) {
            JsonNode el = ev.get("element");
            String eid = el == null ? "" : text(el, "eid");
            if (eid.isEmpty()) {
                issues.add(new Issue("error", "element.eid", "元素事件缺少 eid"));
            } else {
                Map<String, Object> def = registeredElement(tables, eid, appVrsn);
                if (def == null) {
                    issues.add(new Issue("error", "element.eid", "未登记元素: " + eid));
                } else {
                    String pgid = page != null ? text(page, "pgid") : "";
                    Map<String, Object> pgDef = pgid.isEmpty() ? null : registeredPage(tables, pgid, appVrsn);
                    if (pgDef != null && pgDef.get("elements") instanceof List<?> bound
                            && !bound.contains(eid)) {
                        issues.add(new Issue("error", "element.eid",
                                "元素未绑定到页面: " + eid + " ∉ " + pgid + "（页面结构体，见 pages.yaml elements）"));
                    }
                    JsonNode params = el.get("params");
                    ObjectNode effective = params != null && params.isObject()
                            ? (ObjectNode) params.deepCopy()
                            : M.createObjectNode();
                    if (el.has("pos") && !effective.has("pos")) effective.set("pos", el.get("pos"));
                    checkParams(issues, tables, "element.params", effective, requiredParams(def));
                }
            }
        }

        if (event.equals("biz_event")) {
            JsonNode biz = ev.get("biz");
            String code = biz == null ? "" : text(biz, "code");
            if (code.isEmpty()) {
                issues.add(new Issue("error", "biz.code", "业务事件缺少 code"));
            } else {
                Map<String, Object> def = registeredEvent(tables, code, appVrsn);
                if (def == null) {
                    issues.add(new Issue("error", "biz.code", "未登记业务事件: " + code));
                } else {
                    String source = String.valueOf(def.getOrDefault("source", "client"));
                    String platform = text(common, "platform");
                    if (source.equals("server") && !platform.equals("server")) {
                        issues.add(new Issue("error", "biz.code",
                                "服务端事实事件不允许端上上报: " + code + "（钱相关以服务端直写为准，见 02-协议 §6）"));
                    }
                    checkParams(issues, tables, "biz.params", biz.get("params"), requiredParams(def));
                }
            }
        }
        return Result.of(issues);
    }

    private static Map<String, Object> registeredPage(
            Map<String, Map<String, Map<String, Object>>> tables, String pgid, String appVrsn) {
        Map<String, Object> pg = tables.get("pages").get(pgid);
        return appliesToAppVersion(pg, appVrsn) ? pg : null;
    }

    private static Map<String, Object> registeredElement(
            Map<String, Map<String, Map<String, Object>>> tables, String eid, String appVrsn) {
        Map<String, Object> el = tables.get("elements").get(eid);
        return appliesToAppVersion(el, appVrsn) ? el : null;
    }

    private static Map<String, Object> registeredEvent(
            Map<String, Map<String, Map<String, Object>>> tables, String code, String appVrsn) {
        Map<String, Object> ev = tables.get("events").get(code);
        return appliesToAppVersion(ev, appVrsn) ? ev : null;
    }

    /** live/testing 且 since 为空或 app_vrsn ≥ since（灰度版本化校验）。 */
    private static boolean appliesToAppVersion(Map<String, Object> item, String appVrsn) {
        if (!validatesAsRegistered(item)) return false;
        Object since = item.get("since");
        if (since == null || String.valueOf(since).isBlank()) return true;
        if (appVrsn == null || appVrsn.isBlank()) return true;
        return SemVer.compare(appVrsn, String.valueOf(since)) >= 0;
    }

    private static boolean validatesAsRegistered(Map<String, Object> item) {
        if (item == null) return false;
        Object st = item.get("status");
        if (st == null) return true;
        String s = String.valueOf(st);
        return s.equals("live") || s.equals("testing");
    }

    public synchronized ObjectNode coverage(String spaceKey, java.util.Set<String> seenPgids,
            java.util.Set<String> seenEids, java.util.Set<String> seenBizCodes) {
        var tables = tablesFor(spaceKey);
        ObjectNode o = M.createObjectNode();
        var pagesMissing = o.putArray("pages_missing");
        int pagesLive = 0;
        for (Map<String, Object> item : tables.get("pages").values()) {
            if (!isLive(item)) continue;
            pagesLive++;
            String pgid = String.valueOf(item.get("pgid"));
            if (!seenPgids.contains(pgid)) {
                ObjectNode row = pagesMissing.addObject();
                row.put("pgid", pgid);
                row.put("desc", String.valueOf(item.getOrDefault("desc", "")));
            }
        }
        var elementsMissing = o.putArray("elements_missing");
        int elementsLive = 0;
        for (Map<String, Object> item : tables.get("elements").values()) {
            if (!isLive(item)) continue;
            elementsLive++;
            String eid = String.valueOf(item.get("eid"));
            if (!seenEids.contains(eid)) {
                ObjectNode row = elementsMissing.addObject();
                row.put("eid", eid);
                row.put("desc", String.valueOf(item.getOrDefault("desc", "")));
            }
        }
        var eventsMissing = o.putArray("events_missing");
        int eventsLive = 0;
        for (Map<String, Object> item : tables.get("events").values()) {
            if (!isLive(item)) continue;
            if (!"client".equals(String.valueOf(item.getOrDefault("source", "client")))) continue;
            eventsLive++;
            String code = String.valueOf(item.get("code"));
            if (!seenBizCodes.contains(code)) {
                ObjectNode row = eventsMissing.addObject();
                row.put("code", code);
                row.put("desc", String.valueOf(item.getOrDefault("desc", "")));
            }
        }
        ObjectNode summary = o.putObject("summary");
        summary.put("pages_live", pagesLive);
        summary.put("pages_missing", pagesMissing.size());
        summary.put("elements_live", elementsLive);
        summary.put("elements_missing", elementsMissing.size());
        summary.put("events_live", eventsLive);
        summary.put("events_missing", eventsMissing.size());
        return o;
    }

    private static boolean isLive(Map<String, Object> item) {
        Object st = item.get("status");
        if (st == null) return true;
        String s = String.valueOf(st);
        return s.equals("live") || s.equals("testing");
    }

    private static void checkParams(List<Issue> issues,
            Map<String, Map<String, Map<String, Object>>> tables,
            String where, JsonNode params, List<String> required) {
        if (params != null && params.isObject()) {
            for (Iterator<String> it = params.fieldNames(); it.hasNext(); ) {
                String k = it.next();
                Map<String, Object> def = tables.get("params").get(k);
                if (def == null) {
                    issues.add(new Issue("error", where + "." + k, "未登记参数: " + k));
                } else if (!typeOk(String.valueOf(def.get("type")), params.get(k))) {
                    issues.add(new Issue("error", where + "." + k,
                            "类型错误: " + k + " 应为 " + def.get("type")));
                }
            }
        }
        for (String r : required) {
            if (params == null || params.get(r) == null || params.get(r).isNull()
                    || (params.get(r).isTextual() && params.get(r).asText().isEmpty())) {
                issues.add(new Issue("missing", where + "." + r, "必携参数缺失: " + r));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> requiredParams(Map<String, Object> def) {
        Object p = def.get("params");
        if (p instanceof List<?> list) return (List<String>) list;
        return List.of();
    }

    private static boolean typeOk(String type, JsonNode v) {
        return switch (type) {
            case "string" -> v.isTextual();
            case "int" -> v.isIntegralNumber();
            case "float" -> v.isNumber();
            case "bool" -> v.isBoolean() || (v.isIntegralNumber() && (v.asInt() == 0 || v.asInt() == 1));
            case "object" -> v.isObject() || v.isArray();
            default -> true;
        };
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }
}
