package com.giso.gateway;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/** 从 ClickHouse 反算登记覆盖率所需的已见集合（与网关内存累计合并）。 */
public final class ClickHouseCoverage {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ClickHouseCoverage(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
    }

    public boolean enabled() {
        return !baseUrl.isEmpty();
    }

    public Set<String> seenPgids(String env) {
        return distinct("pgid", "pgid != ''", env);
    }

    public Set<String> seenEids(String env) {
        return distinct("eid", "eid != ''", env);
    }

    public Set<String> seenBizCodes(String env) {
        return distinct("biz_code", "biz_code != ''", env);
    }

    private Set<String> distinct(String column, String extraWhere, String env) {
        Set<String> out = new HashSet<>();
        if (!enabled()) return out;
        String envClause = env.isEmpty() ? "" : " AND env = '" + esc(env) + "'";
        String sql = "SELECT DISTINCT " + column + " FROM tracking.ods_events WHERE " + extraWhere
                + envClause + " FORMAT TabSeparated";
        try {
            String url = baseUrl + "/?query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
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
