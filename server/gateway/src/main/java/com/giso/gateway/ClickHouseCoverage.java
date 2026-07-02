package com.giso.gateway;

import java.util.Set;

/** 从 ClickHouse / Doris HTTP 反算登记覆盖率所需的已见集合。 */
public final class ClickHouseCoverage {
    private final String baseUrl;
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(3)).build();

    public ClickHouseCoverage(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
    }

    public boolean enabled() {
        return !baseUrl.isEmpty();
    }

    public Set<String> seenPgids(String env, String spaceKey) {
        return distinct("pgid", "pgid != ''", env, spaceKey);
    }

    public Set<String> seenEids(String env, String spaceKey) {
        return distinct("eid", "eid != ''", env, spaceKey);
    }

    public Set<String> seenBizCodes(String env, String spaceKey) {
        return distinct("biz_code", "biz_code != ''", env, spaceKey);
    }

    /** @deprecated 使用带 spaceKey 的重载 */
    @Deprecated
    public Set<String> seenPgids(String env) {
        return seenPgids(env, null);
    }

    @Deprecated
    public Set<String> seenEids(String env) {
        return seenEids(env, null);
    }

    @Deprecated
    public Set<String> seenBizCodes(String env) {
        return seenBizCodes(env, null);
    }

    private Set<String> distinct(String column, String extraWhere, String env, String spaceKey) {
        Set<String> out = new java.util.HashSet<>();
        if (!enabled()) return out;
        StringBuilder where = new StringBuilder(extraWhere);
        if (env != null && !env.isEmpty()) where.append(" AND env = '").append(esc(env)).append("'");
        if (spaceKey != null && !spaceKey.isBlank()) {
            where.append(" AND space_key = '").append(esc(spaceKey)).append("'");
        }
        String sql = "SELECT DISTINCT " + column + " FROM tracking.ods_events WHERE " + where
                + " FORMAT TabSeparated";
        try {
            String url = baseUrl + "/?query=" + java.net.URLEncoder.encode(sql, java.nio.charset.StandardCharsets.UTF_8);
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10)).GET().build();
            var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return out;
            for (String line : resp.body().split("\n")) {
                String v = line.strip();
                if (!v.isEmpty()) out.add(v);
            }
        } catch (Exception e) {
            System.err.println("[clickhouse-coverage] query failed: " + e.getMessage());
        }
        return out;
    }

    private static String esc(String s) {
        return s.replace("'", "\\'");
    }
}
