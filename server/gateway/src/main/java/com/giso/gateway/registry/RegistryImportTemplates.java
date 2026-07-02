package com.giso.gateway.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注册表 CSV 导入模板与解析（管理台批量导入）。 */
public final class RegistryImportTemplates {
    private static final Map<String, List<String>> COLUMNS = Map.of(
            "params", List.of("key", "type", "desc", "rule", "owner", "since", "issue_link", "status"),
            "pages", List.of("pgid", "screenshot", "desc", "domain", "params", "elements", "owner", "since", "issue_link", "status"),
            "elements", List.of("eid", "desc", "domain", "params", "children", "owner", "since", "issue_link", "status"),
            "events", List.of("code", "desc", "domain", "source", "params", "owner", "since", "issue_link", "status"));

    private static final List<String> LIST_FIELDS = List.of("params", "elements", "children");

    private RegistryImportTemplates() { }

    public static boolean supports(String kind) {
        return COLUMNS.containsKey(kind);
    }

    public static String csvTemplate(String kind) {
        List<String> cols = COLUMNS.get(kind);
        if (cols == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", cols)).append('\n');
        sb.append(exampleRow(kind, cols)).append('\n');
        sb.append("# 说明：\n");
        sb.append("# 1. 首行为表头，请勿修改列名；从第二行起填写数据，# 开头为注释行\n");
        sb.append("# 2. params/elements/children 多个值用英文逗号分隔，如 vid,pos\n");
        sb.append("# 3. status 可选：draft/dev/testing/pending/live/deprecated，留空则按角色默认\n");
        sb.append("# 4. 保存为 UTF-8 CSV 后在管理台「导入」上传\n");
        return sb.toString();
    }

    private static String exampleRow(String kind, List<String> cols) {
        Map<String, String> ex = switch (kind) {
            case "params" -> Map.of(
                    "key", "sample_key", "type", "string", "desc", "示例参数",
                    "rule", "填写取值规则", "owner", "data-team", "since", "1.0", "status", "draft");
            case "pages" -> Map.of(
                    "pgid", "sample_page", "desc", "示例页面", "domain", "video",
                    "params", "vid", "elements", "play_btn", "owner", "pm-team", "since", "1.0", "status", "draft");
            case "elements" -> Map.of(
                    "eid", "sample_elem", "desc", "示例元素", "domain", "video",
                    "params", "vid,pos", "owner", "pm-team", "since", "1.0", "status", "draft");
            case "events" -> Map.of(
                    "code", "sample_event", "desc", "示例业务事件", "domain", "video",
                    "source", "client", "params", "vid", "owner", "pm-team", "since", "1.0", "status", "draft");
            default -> Map.of();
        };
        return cols.stream().map(c -> escapeCsv(ex.getOrDefault(c, ""))).reduce((a, b) -> a + "," + b).orElse("");
    }

    public static List<Map<String, Object>> parseCsv(String kind, String raw) {
        List<String> cols = COLUMNS.get(kind);
        if (cols == null) throw new IllegalArgumentException("unknown kind: " + kind);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            List<String> cells = splitCsvLine(trimmed);
            if (cells.isEmpty()) continue;
            if (cells.get(0).equals(cols.get(0))) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            for (int i = 0; i < cols.size(); i++) {
                String col = cols.get(i);
                String val = i < cells.size() ? cells.get(i).trim() : "";
                if (val.isEmpty()) continue;
                if (LIST_FIELDS.contains(col)) {
                    List<String> arr = new ArrayList<>();
                    for (String p : val.split(",")) {
                        String s = p.trim();
                        if (!s.isEmpty()) arr.add(s);
                    }
                    if (!arr.isEmpty()) item.put(col, arr);
                } else {
                    item.put(col, val);
                }
            }
            if (item.containsKey(cols.get(0))) out.add(item);
        }
        return out;
    }

    static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (ch == ',' && !inQuote) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String escapeCsv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
