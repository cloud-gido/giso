package com.giso.gateway.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.GatewayConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** OpenAI 兼容 Chat Completions（文档 RAG + 可选登记上下文）。 */
public final class OpenAiAssistantProvider implements AssistantProvider {
    private static final ObjectMapper M = new ObjectMapper();

    private final GatewayConfig config;
    private final DocCorpus corpus;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiAssistantProvider(GatewayConfig config) throws Exception {
        this.config = config;
        this.corpus = new DocCorpus(config);
    }

    @Override
    public String name() { return "openai"; }

    @Override
    public boolean ready() {
        return config.openAiApiKey != null && !config.openAiApiKey.isBlank();
    }

    @Override
    public ChatResponse chat(ChatRequest req) throws Exception {
        if (!ready()) throw new IllegalStateException("openai provider: API key not configured");

        var hits = corpus.search(req.message(), config.assistantMaxChunks);
        List<String> sources = new ArrayList<>();
        StringBuilder rag = new StringBuilder();
        for (DocCorpus.Chunk c : hits) {
            sources.add(c.source() + " · " + c.heading());
            rag.append("## ").append(c.heading()).append("\n").append(c.body()).append("\n\n");
        }

        String system = config.assistantSystemPrompt + "\n\n--- 检索到的文档片段 ---\n" + rag
                + "\n--- 当前空间登记 ---\n" + nullToEmpty(req.registryContext());

        ArrayNode messages = M.createArrayNode();
        messages.add(objMsg("system", system));
        for (ChatMessage h : req.history()) {
            if ("user".equals(h.role()) || "assistant".equals(h.role())) {
                messages.add(objMsg(h.role(), h.content()));
            }
        }
        messages.add(objMsg("user", req.message()));

        ObjectNode body = M.createObjectNode();
        body.put("model", config.openAiModel);
        body.set("messages", messages);
        body.put("temperature", 0.2);

        String url = config.openAiBaseUrl.replaceAll("/+$", "") + "/chat/completions";
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.openAiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("LLM HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode root = M.readTree(resp.body());
        String answer = root.path("choices").path(0).path("message").path("content").asText("");
        return new ChatResponse(answer, name(), sources, List.of());
    }

    private static ObjectNode objMsg(String role, String content) {
        ObjectNode n = M.createObjectNode();
        n.put("role", role);
        n.put("content", content);
        return n;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
