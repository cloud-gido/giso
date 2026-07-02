package com.giso.gateway.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giso.gateway.GatewayConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 转发至 GIDO 平台 Copilot 代理（deployment 仓可部署 sidecar）。
 * POST {gido_proxy_url} body: { message, history, space_key, registry_context, topic }
 */
public final class GidoProxyAssistantProvider implements AssistantProvider {
    private static final ObjectMapper M = new ObjectMapper();
    private final String url;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public GidoProxyAssistantProvider(GatewayConfig config) {
        this.url = config.gidoProxyUrl == null ? "" : config.gidoProxyUrl.strip();
    }

    @Override
    public String name() { return "gido_proxy"; }

    @Override
    public boolean ready() { return !url.isEmpty(); }

    @Override
    public ChatResponse chat(ChatRequest req) throws Exception {
        if (!ready()) throw new IllegalStateException("gido_proxy: GISO_GIDO_COPILOT_URL not set");

        String payload = M.writeValueAsString(Map.of(
                "message", req.message(),
                "history", req.history(),
                "space_key", req.spaceKey() == null ? "" : req.spaceKey(),
                "registry_context", req.registryContext() == null ? "" : req.registryContext(),
                "topic", req.topic() == null ? "" : req.topic()));

        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("GIDO copilot proxy HTTP " + resp.statusCode());
        }
        var node = M.readTree(resp.body());
        List<String> sources = node.path("sources").isArray()
                ? M.convertValue(node.path("sources"), M.getTypeFactory().constructCollectionType(List.class, String.class))
                : List.of();
        List<String> followups = node.path("suggested_followups").isArray()
                ? M.convertValue(node.path("suggested_followups"),
                M.getTypeFactory().constructCollectionType(List.class, String.class))
                : List.of();
        return new ChatResponse(
                node.path("answer").asText(resp.body()),
                name(),
                sources,
                followups);
    }
}
