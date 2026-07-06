package com.giso.gateway.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 注册表引用治理：列表去重、池内引用校验、反向关联与覆盖率提示。
 */
public final class RegistryRefs {
    private static final Pattern SNAKE = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");
    private static final Set<String> PARAM_TYPES = Set.of("string", "int", "float", "bool", "object");
    private static final Set<String> STATUSES = Set.of(
            "draft", "dev", "testing", "pending", "live", "deprecated");

    private RegistryRefs() { }

    public static void normalizeLists(Map<String, Object> item) {
        if (item == null) return;
        for (String field : List.of("params", "elements", "children")) {
            Object raw = item.get(field);
            if (!(raw instanceof List<?> list)) continue;
            LinkedHashSet<String> deduped = new LinkedHashSet<>();
            for (Object o : list) {
                String s = String.valueOf(o).trim();
                if (!s.isEmpty() && !"null".equals(s)) deduped.add(s);
            }
            item.put(field, new ArrayList<>(deduped));
        }
    }

    public static String validate(Map<String, Map<String, Map<String, Object>>> tables,
            String kind, Map<String, Object> item) {
        String idField = RegistryKinds.idField(kind);
        String key = String.valueOf(item.get(idField));
        if (key == null || key.equals("null") || !SNAKE.matcher(key).matches()) {
            return "命名违规（小写 snake_case，≤32 字符）: " + key;
        }
        if (kind.equals("params")) {
            if (!PARAM_TYPES.contains(String.valueOf(item.get("type")))) {
                return "参数类型非法: " + item.get("type");
            }
        }
        if (!kind.equals("params")) {
            String err = checkParamRefs(tables, item.get("params"));
            if (err != null) return err;
        }
        if (kind.equals("pages")) {
            String err = checkElementRefs(tables, item.get("elements"), "elements");
            if (err != null) return err;
        }
        if (kind.equals("elements")) {
            String err = checkElementRefs(tables, item.get("children"), "children");
            if (err != null) return err;
            Object ch = item.get("children");
            if (ch instanceof List<?> list) {
                for (Object c : list) {
                    if (key.equals(String.valueOf(c))) {
                        return "元素不能引用自身为子元素: " + key;
                    }
                }
            }
        }
        Object st = item.get("status");
        if (st != null && !STATUSES.contains(String.valueOf(st))) {
            return "status 非法: " + st + "（draft/dev/testing/pending/live/deprecated）";
        }
        return null;
    }

    private static String checkParamRefs(Map<String, Map<String, Map<String, Object>>> tables, Object ps) {
        if (!(ps instanceof List<?> list)) return null;
        Map<String, Map<String, Object>> params = tables.get("params");
        for (Object p : list) {
            String k = String.valueOf(p);
            if (!params.containsKey(k)) {
                return "引用了未登记参数: " + k + "（请先在参数池登记）";
            }
        }
        return null;
    }

    private static String checkElementRefs(Map<String, Map<String, Map<String, Object>>> tables,
            Object els, String fieldLabel) {
        if (!(els instanceof List<?> list)) return null;
        Map<String, Map<String, Object>> elements = tables.get("elements");
        for (Object e : list) {
            String id = String.valueOf(e);
            if (!elements.containsKey(id)) {
                return fieldLabel + " 引用未登记元素: " + id + "（请先在元素池登记）";
            }
        }
        return null;
    }

    /** 编辑/保存后的引用治理提示（信息性，不阻断）。 */
    public static Map<String, Object> hints(Map<String, Map<String, Map<String, Object>>> tables,
            String kind, Map<String, Object> item) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (item == null) item = Map.of();
        String idField = RegistryKinds.idField(kind);
        String key = str(item.get(idField));

        Set<String> referencedParams = referencedParams(tables);
        Set<String> boundElements = boundElements(tables);
        Map<String, List<String>> elementToPages = elementToPages(tables);
        Map<String, List<String>> paramReferencedBy = paramReferencedBy(tables);

        List<String> orphanParams = new ArrayList<>();
        for (String pk : tables.get("params").keySet()) {
            if (!referencedParams.contains(pk)) orphanParams.add(pk);
        }
        out.put("orphan_params", trimList(orphanParams, 15));
        out.put("orphan_params_count", orphanParams.size());

        List<String> orphanElements = new ArrayList<>();
        for (String eid : tables.get("elements").keySet()) {
            if (!boundElements.contains(eid)) orphanElements.add(eid);
        }
        out.put("orphan_elements", trimList(orphanElements, 15));
        out.put("orphan_elements_count", orphanElements.size());

        out.put("owners", collectOwners(tables));

        if (kind.equals("params") && !key.isEmpty()) {
            List<Map<String, Object>> refs = new ArrayList<>();
            for (String ref : paramReferencedBy.getOrDefault(key, List.of())) {
                String[] p = ref.split(":", 2);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", p[0]);
                row.put("id", p.length > 1 ? p[1] : "");
                refs.add(row);
            }
            out.put("referenced_by", refs);
            if (refs.isEmpty() && isLiveish(item)) {
                out.put("warn", "该参数已登记但未被任何页面/元素/事件引用");
            }
        }

        if (kind.equals("pages") && !key.isEmpty()) {
            List<String> selected = asStringList(item.get("elements"));
            String domain = str(item.get("domain"));
            List<Map<String, Object>> elementLinks = new ArrayList<>();
            for (String eid : selected) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("eid", eid);
                row.put("pages", elementToPages.getOrDefault(eid, List.of()));
                Map<String, Object> el = tables.get("elements").get(eid);
                if (el != null) row.put("desc", str(el.get("desc")));
                elementLinks.add(row);
            }
            out.put("element_links", elementLinks);
            out.put("suggested_elements", suggestElements(tables, domain, selected));
            List<String> pageParams = asStringList(item.get("params"));
            out.put("params_not_on_page", paramsInPoolNotSelected(tables, pageParams));
        }

        if (kind.equals("elements") && !key.isEmpty()) {
            out.put("parent_pages", elementToPages.getOrDefault(key, List.of()));
            String domain = str(item.get("domain"));
            out.put("suggested_pages", suggestPages(tables, domain, key));
            List<String> children = asStringList(item.get("children"));
            List<Map<String, Object>> childLinks = new ArrayList<>();
            for (String cid : children) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("eid", cid);
                row.put("pages", elementToPages.getOrDefault(cid, List.of()));
                childLinks.add(row);
            }
            out.put("child_links", childLinks);
        }

        if (kind.equals("events") && !key.isEmpty()) {
            List<String> evParams = asStringList(item.get("params"));
            out.put("params_not_on_event", paramsInPoolNotSelected(tables, evParams));
            out.put("referenced_by", List.of());
        }

        return out;
    }

    private static List<String> suggestElements(Map<String, Map<String, Map<String, Object>>> tables,
            String domain, List<String> selected) {
        Set<String> sel = new HashSet<>(selected);
        List<String> out = new ArrayList<>();
        for (Map<String, Object> el : tables.get("elements").values()) {
            String eid = str(el.get("eid"));
            if (eid.isEmpty() || sel.contains(eid)) continue;
            if (!domain.isEmpty() && !domain.equals(str(el.get("domain")))) continue;
            out.add(eid);
            if (out.size() >= 12) break;
        }
        return out;
    }

    private static List<String> suggestPages(Map<String, Map<String, Map<String, Object>>> tables,
            String domain, String eid) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> pg : tables.get("pages").values()) {
            String pgid = str(pg.get("pgid"));
            if (pgid.isEmpty()) continue;
            if (asStringList(pg.get("elements")).contains(eid)) continue;
            if (!domain.isEmpty() && !domain.equals(str(pg.get("domain")))) continue;
            out.add(pgid);
            if (out.size() >= 8) break;
        }
        return out;
    }

    private static List<String> paramsInPoolNotSelected(
            Map<String, Map<String, Map<String, Object>>> tables, List<String> selected) {
        Set<String> sel = new HashSet<>(selected);
        List<String> out = new ArrayList<>();
        for (String pk : tables.get("params").keySet()) {
            if (!sel.contains(pk)) {
                out.add(pk);
                if (out.size() >= 12) break;
            }
        }
        return out;
    }

    private static Set<String> referencedParams(Map<String, Map<String, Map<String, Object>>> tables) {
        Set<String> s = new HashSet<>();
        for (String kind : List.of("pages", "elements", "events")) {
            for (Map<String, Object> item : tables.get(kind).values()) {
                s.addAll(asStringList(item.get("params")));
            }
        }
        return s;
    }

    private static Set<String> boundElements(Map<String, Map<String, Map<String, Object>>> tables) {
        Set<String> s = new HashSet<>();
        for (Map<String, Object> pg : tables.get("pages").values()) {
            s.addAll(asStringList(pg.get("elements")));
        }
        return s;
    }

    private static Map<String, List<String>> elementToPages(
            Map<String, Map<String, Map<String, Object>>> tables) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (Map<String, Object> pg : tables.get("pages").values()) {
            String pgid = str(pg.get("pgid"));
            for (String eid : asStringList(pg.get("elements"))) {
                m.computeIfAbsent(eid, k -> new ArrayList<>()).add(pgid);
            }
        }
        return m;
    }

    private static Map<String, List<String>> paramReferencedBy(
            Map<String, Map<String, Map<String, Object>>> tables) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (Map<String, Object> pg : tables.get("pages").values()) {
            String id = str(pg.get("pgid"));
            for (String p : asStringList(pg.get("params"))) {
                m.computeIfAbsent(p, k -> new ArrayList<>()).add("pages:" + id);
            }
        }
        for (Map<String, Object> el : tables.get("elements").values()) {
            String id = str(el.get("eid"));
            for (String p : asStringList(el.get("params"))) {
                m.computeIfAbsent(p, k -> new ArrayList<>()).add("elements:" + id);
            }
        }
        for (Map<String, Object> ev : tables.get("events").values()) {
            String id = str(ev.get("code"));
            for (String p : asStringList(ev.get("params"))) {
                m.computeIfAbsent(p, k -> new ArrayList<>()).add("events:" + id);
            }
        }
        return m;
    }

    public static List<String> collectOwners(Map<String, Map<String, Map<String, Object>>> tables) {
        TreeSet<String> owners = new TreeSet<>();
        for (String kind : RegistryKinds.FILES.keySet()) {
            for (Map<String, Object> item : tables.get(kind).values()) {
                String o = str(item.get("owner"));
                if (!o.isEmpty()) owners.add(o);
            }
        }
        return new ArrayList<>(owners);
    }

    private static boolean isLiveish(Map<String, Object> item) {
        Object st = item.get("status");
        if (st == null) return true;
        String s = String.valueOf(st);
        return s.equals("live") || s.equals("testing");
    }

    private static List<String> asStringList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            String s = String.valueOf(o).trim();
            if (!s.isEmpty() && !"null".equals(s)) out.add(s);
        }
        return out;
    }

    private static List<String> trimList(List<String> in, int max) {
        if (in.size() <= max) return in;
        return new ArrayList<>(in.subList(0, max));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
