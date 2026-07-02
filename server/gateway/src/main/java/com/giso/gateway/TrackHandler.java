package com.giso.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.space.SpaceService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * POST /v1/track — 事件上报入口。
 * 批量 JSON 数组；按 App Key 解析空间，注入 common.space，按空间注册表校验。
 */
public final class TrackHandler implements HttpHandler {
    private static final ObjectMapper M = new ObjectMapper();

    private final Registry registry;
    private final EventStore store;
    private final SseHub sse;
    private final GatewayConfig config;
    private final RateLimiter rateLimiter;
    private final SpaceService spaces;

    public TrackHandler(Registry registry, EventStore store, SseHub sse, GatewayConfig config,
                        SpaceService spaces) {
        this.registry = registry;
        this.store = store;
        this.sse = sse;
        this.config = config;
        this.spaces = spaces;
        this.rateLimiter = new RateLimiter(config.rateLimitRps, config.rateLimitBurst);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Http.handlePreflight(ex)) return;
        if (!ex.getRequestMethod().equals("POST")) {
            Http.empty(ex, 405);
            return;
        }
        String appKey = ex.getRequestHeaders().getFirst("X-App-Key");
        if (appKey == null) appKey = Http.query(ex).get("k");
        if (!config.appKeys.isEmpty()) {
            boolean allowed = appKey != null
                    && (config.appKeys.contains(appKey)
                    || (spaces != null && spaces.hasAppKey(appKey)));
            if (!allowed) {
                respond(ex, 401, "{\"error\":\"invalid app key\"}");
                return;
            }
        }
        if (!rateLimiter.allow(Http.clientIp(ex))) {
            respond(ex, 429, "{\"error\":\"rate limited\"}");
            return;
        }
        String spaceKey = spaces != null
                ? spaces.resolveSpaceForAppKey(appKey)
                : SpaceService.DEFAULT_SPACE;

        JsonNode batch;
        try {
            byte[] body = Http.readBody(ex, config.maxBodyBytes);
            if (body == null) {
                respond(ex, 413, "{\"error\":\"body too large\"}");
                return;
            }
            batch = M.readTree(body);
        } catch (IOException e) {
            respond(ex, 400, "{\"error\":\"invalid json\"}");
            return;
        }
        if (!batch.isArray()) {
            if (batch.isObject()) batch = M.createArrayNode().add(batch);
            else {
                respond(ex, 400, "{\"error\":\"expect array\"}");
                return;
            }
        }
        for (JsonNode ev : batch) {
            if (!eventEnabled(config, ev)) continue;
            if (!sampled(config, ev)) continue;
            ObjectNode enriched = enrichSpace(ev, spaceKey);
            Registry.Result result = registry.validate(enriched, spaceKey);
            ObjectNode wrapped = store.accept(enriched, result, spaceKey);
            sse.broadcast(wrapped.toString(), spaceKey);
        }
        Metrics.inc("giso_track_responses_total{code=\"204\"}");
        Http.empty(ex, 204);
    }

    private static ObjectNode enrichSpace(JsonNode ev, String spaceKey) {
        ObjectNode out = ev.deepCopy();
        JsonNode common = out.get("common");
        if (common instanceof ObjectNode cn) {
            cn.put("space", spaceKey);
        } else {
            ObjectNode cn = M.createObjectNode();
            cn.put("space", spaceKey);
            out.set("common", cn);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static boolean eventEnabled(GatewayConfig config, JsonNode ev) {
        Object disabled = config.sdkConfig.get("events_disabled");
        if (!(disabled instanceof List<?> list)) return true;
        String name = ev.path("event").asText("");
        return !list.contains(name);
    }

    @SuppressWarnings("unchecked")
    private static boolean sampled(GatewayConfig config, JsonNode ev) {
        Object rates = config.sdkConfig.get("event_sample_rates");
        if (!(rates instanceof Map<?, ?> map)) return true;
        String name = ev.path("event").asText("");
        Object rate = map.get(name);
        if (rate == null) rate = map.get("*");
        if (rate == null) return true;
        double p = rate instanceof Number n ? n.doubleValue() : 1.0;
        if (p >= 1.0) return true;
        if (p <= 0.0) return false;
        return Math.random() < p;
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        Metrics.inc("giso_track_responses_total{code=\"" + code + "\"}");
        Http.json(ex, code, body);
    }
}
