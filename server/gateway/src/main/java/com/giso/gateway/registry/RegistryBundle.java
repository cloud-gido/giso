package com.giso.gateway.registry;

import com.giso.gateway.space.SpaceService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注册表整包导出/导入（测试 → 生产晋升，默认仅 live）。 */
public final class RegistryBundle {
    public static final String FORMAT = "giso-registry-bundle";
    public static final int VERSION = 1;
    public static final String STATUS_LIVE = "live";
    public static final List<String> KIND_ORDER = List.of("params", "pages", "elements", "events");

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
        bundle.put("source_revision", revision);
        Map<String, List<Map<String, Object>>> registry = new LinkedHashMap<>();
        int total = 0;
        for (String kind : KIND_ORDER) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> item : allInSpace.getOrDefault(kind, List.of())) {
                if (!STATUS_LIVE.equals(statusOf(item))) continue;
                rows.add(copyItem(item));
            }
            registry.put(kind, rows);
            total += rows.size();
        }
        bundle.put("registry", registry);
        bundle.put("counts", countSummary(registry));
        bundle.put("total", total);
        return bundle;
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

    private static Map<String, Object> copyItem(Map<String, Object> item) {
        return new LinkedHashMap<>(item);
    }
}
