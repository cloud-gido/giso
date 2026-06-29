package com.giso.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * POST /v1/track — 事件上报入口。
 * 批量 JSON 数组；逐条按注册表校验：正常/缺失 → raw，错误 → 隔离区；全部推送 SSE。
 */
public final class TrackHandler implements HttpHandler {
    private static final ObjectMapper M = new ObjectMapper();

    private final Registry registry;
    private final EventStore store;
    private final SseHub sse;
    private final GatewayConfig config;
    private final RateLimiter rateLimiter;

    public TrackHandler(Registry registry, EventStore store, SseHub sse, GatewayConfig config) {
        this.registry = registry;
        this.store = store;
        this.sse = sse;
        this.config = config;
        this.rateLimiter = new RateLimiter(config.rateLimitRps, config.rateLimitBurst);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Http.handlePreflight(ex)) return;
        if (!ex.getRequestMethod().equals("POST")) {
            Http.empty(ex, 405);
            return;
        }
        // 鉴权：X-App-Key 或 ?k=（sendBeacon 无法带 header）
        if (!config.appKeys.isEmpty()) {
            String key = ex.getRequestHeaders().getFirst("X-App-Key");
            if (key == null) key = Http.query(ex).get("k");
            if (key == null || !config.appKeys.contains(key)) {
                respond(ex, 401, "{\"error\":\"invalid app key\"}");
                return;
            }
        }
        // 限流（按客户端 IP 令牌桶）
        if (!rateLimiter.allow(Http.clientIp(ex))) {
            respond(ex, 429, "{\"error\":\"rate limited\"}");
            return;
        }
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
            // 兼容 sendBeacon 单条对象
            if (batch.isObject()) batch = M.createArrayNode().add(batch);
            else {
                respond(ex, 400, "{\"error\":\"expect array\"}");
                return;
            }
        }
        for (JsonNode ev : batch) {
            Registry.Result result = registry.validate(ev);
            ObjectNode wrapped = store.accept(ev, result);
            sse.broadcast(wrapped.toString());
        }
        Metrics.inc("giso_track_responses_total{code=\"204\"}");
        Http.empty(ex, 204);
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        Metrics.inc("giso_track_responses_total{code=\"" + code + "\"}");
        Http.json(ex, code, body);
    }
}
