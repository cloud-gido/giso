package com.giso.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 注册表：加载 schema/*.yaml（唯一事实源），提供事件校验与管理页面的增删改（写回 YAML）。
 */
public final class Registry {
    private static final ObjectMapper M = new ObjectMapper();
    public static final java.util.Set<String> STANDARD_EVENTS = java.util.Set.of(
            "app_install", "app_launch", "app_foreground", "app_background",
            "page_enter", "page_exit", "element_exposure", "element_click", "biz_event");

    private static final Pattern SNAKE = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");

    private final Path schemaDir;
    // kind -> (key -> item)，kind ∈ params/pages/elements/events
    private final Map<String, Map<String, Map<String, Object>>> tables = new LinkedHashMap<>();

    private static final Map<String, String[]> FILES = Map.of(
            "params", new String[]{"params.yaml", "params", "key"},
            "pages", new String[]{"pages.yaml", "pages", "pgid"},
            "elements", new String[]{"elements.yaml", "elements", "eid"},
            "events", new String[]{"biz_events.yaml", "events", "code"});

    public Registry(Path schemaDir) throws IOException {
        this.schemaDir = schemaDir;
        reload();
    }

    public synchronized void reload() throws IOException {
        Yaml yaml = new Yaml();
        for (var e : FILES.entrySet()) {
            Map<String, Object> doc = yaml.load(Files.readString(schemaDir.resolve(e.getValue()[0])));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get(e.getValue()[1]);
            Map<String, Map<String, Object>> table = new LinkedHashMap<>();
            for (Map<String, Object> item : items) {
                table.put(String.valueOf(item.get(e.getValue()[2])), item);
            }
            tables.put(e.getKey(), table);
        }
    }

    public synchronized Map<String, List<Map<String, Object>>> all() {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        tables.forEach((k, v) -> out.put(k, new ArrayList<>(v.values())));
        return out;
    }

    // ── 管理页面写入（写回 YAML，仍以 Git 提交为最终审计） ──────────

    public synchronized String upsert(String kind, Map<String, Object> item) throws IOException {
        String idField = FILES.get(kind)[2];
        String key = String.valueOf(item.get(idField));
        if (key == null || key.equals("null") || !SNAKE.matcher(key).matches()) {
            return "命名违规（小写 snake_case，≤32 字符）: " + key;
        }
        if (kind.equals("params") && !java.util.Set.of("string", "int", "float", "bool", "object")
                .contains(String.valueOf(item.get("type")))) {
            return "参数类型非法: " + item.get("type");
        }
        // 引用完整性：pages/elements/events 的 params 必须已登记
        if (!kind.equals("params")) {
            Object ps = item.get("params");
            if (ps instanceof List<?> list) {
                for (Object p : list) {
                    if (!tables.get("params").containsKey(String.valueOf(p))) {
                        return "引用了未登记参数: " + p + "（请先在参数池登记）";
                    }
                }
            }
        }
        // 页面结构体：elements 绑定必须引用已登记元素
        if (kind.equals("pages") && item.get("elements") instanceof List<?> els) {
            for (Object e : els) {
                if (!tables.get("elements").containsKey(String.valueOf(e))) {
                    return "elements 绑定引用未登记元素: " + e + "（请先在元素池登记）";
                }
            }
        }
        // 生命周期 status 枚举
        Object st = item.get("status");
        if (st != null && !java.util.Set.of("draft", "dev", "testing", "live", "deprecated")
                .contains(String.valueOf(st))) {
            return "status 非法: " + st + "（draft/dev/testing/live/deprecated）";
        }
        tables.get(kind).put(key, item);
        persist(kind);
        return null;
    }

    public synchronized String delete(String kind, String key) throws IOException {
        if (tables.get(kind).remove(key) == null) return "不存在: " + key;
        persist(kind);
        return null;
    }

    private void persist(String kind) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setAllowUnicode(true);
        opts.setIndent(2);
        Yaml yaml = new Yaml(opts);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", "1.0");
        doc.put(FILES.get(kind)[1], new ArrayList<>(tables.get(kind).values()));
        StringWriter sw = new StringWriter();
        sw.write("# " + FILES.get(kind)[0] + " — 由管理页面写回；最终以 Git 提交为审计记录\n");
        yaml.dump(doc, sw);
        Files.writeString(schemaDir.resolve(FILES.get(kind)[0]), sw.toString());
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

    public synchronized Result validate(JsonNode ev) {
        List<Issue> issues = new ArrayList<>();
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

        JsonNode page = ev.get("page");
        if (page != null && page.isObject()) {
            String pgid = text(page, "pgid");
            if (!pgid.isEmpty()) {
                Map<String, Object> pg = tables.get("pages").get(pgid);
                if (pg == null) {
                    issues.add(new Issue("error", "page.pgid", "未登记页面: " + pgid));
                } else {
                    checkParams(issues, "page.pg_params", page.get("pg_params"), requiredParams(pg));
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
                Map<String, Object> def = tables.get("elements").get(eid);
                if (def == null) {
                    issues.add(new Issue("error", "element.eid", "未登记元素: " + eid));
                } else {
                    // 页面结构体：页面声明了 elements 绑定时，强校验元素归属
                    String pgid = page != null ? text(page, "pgid") : "";
                    Map<String, Object> pgDef = pgid.isEmpty() ? null : tables.get("pages").get(pgid);
                    if (pgDef != null && pgDef.get("elements") instanceof List<?> bound
                            && !bound.contains(eid)) {
                        issues.add(new Issue("error", "element.eid",
                                "元素未绑定到页面: " + eid + " ∉ " + pgid + "（页面结构体，见 pages.yaml elements）"));
                    }
                    // pos 在信封层（element.pos）而非 params 内，合并后再校验必携参数
                    JsonNode params = el.get("params");
                    com.fasterxml.jackson.databind.node.ObjectNode effective =
                            params != null && params.isObject()
                                    ? (com.fasterxml.jackson.databind.node.ObjectNode) params.deepCopy()
                                    : new ObjectMapper().createObjectNode();
                    if (el.has("pos") && !effective.has("pos")) effective.set("pos", el.get("pos"));
                    checkParams(issues, "element.params", effective, requiredParams(def));
                }
            }
        }

        if (event.equals("biz_event")) {
            JsonNode biz = ev.get("biz");
            String code = biz == null ? "" : text(biz, "code");
            if (code.isEmpty()) {
                issues.add(new Issue("error", "biz.code", "业务事件缺少 code"));
            } else {
                Map<String, Object> def = tables.get("events").get(code);
                if (def == null) {
                    issues.add(new Issue("error", "biz.code", "未登记业务事件: " + code));
                } else {
                    // 客户端/服务端事实分流：登记为 server 事实源的事件不允许端上上报
                    String source = String.valueOf(def.getOrDefault("source", "client"));
                    String platform = text(common, "platform");
                    if (source.equals("server") && !platform.equals("server")) {
                        issues.add(new Issue("error", "biz.code",
                                "服务端事实事件不允许端上上报: " + code + "（钱相关以服务端直写为准，见 02-协议 §6）"));
                    }
                    checkParams(issues, "biz.params", biz.get("params"), requiredParams(def));
                }
            }
        }
        return Result.of(issues);
    }

    /** 登记未上报：注册表 live 条目 vs 网关已见集合求差集 */
    public synchronized ObjectNode coverage(java.util.Set<String> seenPgids,
            java.util.Set<String> seenEids, java.util.Set<String> seenBizCodes) {
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

    /** 校验参数对象：key 必须已登记且类型正确；必携参数缺失记 missing */
    private void checkParams(List<Issue> issues, String where, JsonNode params, List<String> required) {
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
