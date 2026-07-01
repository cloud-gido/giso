package com.giso.gateway.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 四张注册表 kind → YAML 文件名 / 根键 / 主键字段。 */
public final class RegistryKinds {
    public static final Map<String, String[]> FILES = Map.of(
            "params", new String[]{"params.yaml", "params", "key"},
            "pages", new String[]{"pages.yaml", "pages", "pgid"},
            "elements", new String[]{"elements.yaml", "elements", "eid"},
            "events", new String[]{"biz_events.yaml", "events", "code"});

    private RegistryKinds() { }

    public static boolean isKnown(String kind) {
        return FILES.containsKey(kind);
    }

    public static String idField(String kind) {
        return FILES.get(kind)[2];
    }

    public static String yamlFile(String kind) {
        return FILES.get(kind)[0];
    }

    public static String rootKey(String kind) {
        return FILES.get(kind)[1];
    }

    public static Map<String, Map<String, Map<String, Object>>> emptyTables() {
        Map<String, Map<String, Map<String, Object>>> out = new LinkedHashMap<>();
        for (String kind : FILES.keySet()) {
            out.put(kind, new LinkedHashMap<>());
        }
        return out;
    }

    public static void putItems(String kind, List<Map<String, Object>> items,
            Map<String, Map<String, Map<String, Object>>> tables) {
        String idField = idField(kind);
        Map<String, Map<String, Object>> table = tables.get(kind);
        table.clear();
        for (Map<String, Object> item : items) {
            table.put(String.valueOf(item.get(idField)), item);
        }
    }
}
