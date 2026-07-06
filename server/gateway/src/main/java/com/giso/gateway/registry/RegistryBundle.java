package com.giso.gateway.registry;

import com.giso.gateway.space.SpaceService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 注册表整包导出/导入（测试 → 生产晋升，live 条目 + 其依赖闭包）。 */
public final class RegistryBundle {
    public static final String FORMAT = "giso-registry-bundle";
    public static final int VERSION = 1;
    public static final String STATUS_LIVE = "live";
    /** 导出 JSON 内各池排列顺序（可读性）。 */
    public static final List<String> KIND_ORDER = List.of("params", "pages", "elements", "events");
    /** 导入写入顺序：元素须在页面之前，否则页面 elements 引用校验失败。 */
    public static final List<String> IMPORT_KIND_ORDER = List.of("params", "elements", "pages", "events");

    private RegistryBundle() { }

    public static Map<String, Object> buildExport(
            String spaceKey,
            Map<String, List<Map<String, Object>>> allInSpace,
            long revision,
            String operator) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", FORMAT);
        bundle.put("version", VERSION);
        bundle.put("space_key", sk(spaceKey));
        bundle.put("exported_at", Instant.now().toString());
        bundle.put("exported_by", operator == null ? "" : operator);
        bundle.put("status_filter", STATUS_LIVE);
        bundle.put("includes_dependency_closure", true);
        bundle.put("source_revision", revision);

        Map<String, Map<String, Map<String, Object>>> byId = indexAll(allInSpace);
        Map<String, Map<String, Map<String, Object>>> selected = RegistryKinds.emptyTables();

        int liveTotal = 0;
        for (String kind : KIND_ORDER) {
            for (Map<String, Object> item : allInSpace.getOrDefault(kind, List.of())) {
                if (!STATUS_LIVE.equals(statusOf(item))) continue;
                String id = idOf(kind, item);
                if (id.isEmpty()) continue;
                selected.get(kind).put(id, copyItem(item));
                liveTotal++;
            }
        }

        expandDependencyClosure(selected, byId);

        Map<String, List<Map<String, Object>>> registry = new LinkedHashMap<>();
        int total = 0;
        for (String kind : KIND_ORDER) {
            List<Map<String, Object>> rows = new ArrayList<>(selected.get(kind).values());
            registry.put(kind, rows);
            total += rows.size();
        }
        bundle.put("registry", registry);
        bundle.put("counts", countSummary(registry));
        bundle.put("live_total", liveTotal);
        bundle.put("dependency_total", Math.max(0, total - liveTotal));
        bundle.put("total", total);
        return bundle;
    }

    /** live 页面/元素/事件引用的 params、elements（含子元素）一并纳入，保证可导入。 */
    static void expandDependencyClosure(
            Map<String, Map<String, Map<String, Object>>> selected,
            Map<String, Map<String, Map<String, Object>>> byId) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> needParams = new LinkedHashSet<>();
            Set<String> needElements = new LinkedHashSet<>();
            collectParamRefs(selected, needParams);
            collectElementRefs(selected, needElements);
            for (String key : needParams) {
                if (selected.get("params").containsKey(key)) continue;
                Map<String, Object> item = byId.get("params").get(key);
                if (item == null) continue;
                selected.get("params").put(key, copyItem(item));
                changed = true;
            }
            for (String eid : needElements) {
                if (selected.get("elements").containsKey(eid)) continue;
                Map<String, Object> item = byId.get("elements").get(eid);
                if (item == null) continue;
                selected.get("elements").put(eid, copyItem(item));
                changed = true;
            }
        }
    }

    static void collectParamRefs(
            Map<String, Map<String, Map<String, Object>>> selected, Set<String> out) {
        for (Map<String, Object> page : selected.get("pages").values()) {
            stringList(page.get("params")).forEach(out::add);
        }
        for (Map<String, Object> el : selected.get("elements").values()) {
            stringList(el.get("params")).forEach(out::add);
        }
        for (Map<String, Object> ev : selected.get("events").values()) {
            stringList(ev.get("params")).forEach(out::add);
        }
    }

    static void collectElementRefs(
            Map<String, Map<String, Map<String, Object>>> selected, Set<String> out) {
        for (Map<String, Object> page : selected.get("pages").values()) {
            stringList(page.get("elements")).forEach(out::add);
        }
        for (Map<String, Object> el : selected.get("elements").values()) {
            stringList(el.get("children")).forEach(out::add);
        }
    }

    static Map<String, Map<String, Map<String, Object>>> indexAll(
            Map<String, List<Map<String, Object>>> allInSpace) {
        Map<String, Map<String, Map<String, Object>>> byId = RegistryKinds.emptyTables();
        for (String kind : KIND_ORDER) {
            for (Map<String, Object> item : allInSpace.getOrDefault(kind, List.of())) {
                String id = idOf(kind, item);
                if (!id.isEmpty()) byId.get(kind).put(id, item);
            }
        }
        return byId;
    }

    @SuppressWarnings("unchecked")
    public static String validateBundleShape(Map<String, Object> bundle) {
        if (bundle == null || bundle.isEmpty()) return "导入包为空";
        if (!FORMAT.equals(String.valueOf(bundle.getOrDefault("format", "")))) {
            return "不支持的 format（期望 " + FORMAT + "）";
        }
        int ver = bundle.get("version") instanceof Number n ? n.intValue() : -1;
        if (ver != VERSION) return "不支持的 version: " + ver;
        Object reg = bundle.get("registry");
        if (!(reg instanceof Map<?, ?> map)) return "缺少 registry 对象";
        for (String kind : KIND_ORDER) {
            Object v = map.get(kind);
            if (v != null && !(v instanceof List<?>)) return "registry." + kind + " 须为数组";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> itemsOf(Map<String, Object> bundle, String kind) {
        Object reg = bundle.get("registry");
        if (!(reg instanceof Map<?, ?> map)) return List.of();
        Object list = map.get(kind);
        if (!(list instanceof List<?> raw)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> item = new LinkedHashMap<>();
                m.forEach((k, v) -> item.put(String.valueOf(k), v));
                out.add(item);
            }
        }
        return out;
    }

    private static Map<String, Integer> countSummary(Map<String, List<Map<String, Object>>> registry) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String kind : KIND_ORDER) {
            counts.put(kind, registry.getOrDefault(kind, List.of()).size());
        }
        return counts;
    }

    private static String sk(String spaceKey) {
        return spaceKey == null || spaceKey.isBlank() ? SpaceService.DEFAULT_SPACE : spaceKey;
    }

    private static String statusOf(Map<String, Object> item) {
        Object st = item.get("status");
        return st == null || String.valueOf(st).isBlank() ? STATUS_LIVE : String.valueOf(st);
    }

    private static String idOf(String kind, Map<String, Object> item) {
        return str(item.get(RegistryKinds.idField(kind)));
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            String s = str(o);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Map<String, Object> copyItem(Map<String, Object> item) {
        return new LinkedHashMap<>(item);
    }
}
