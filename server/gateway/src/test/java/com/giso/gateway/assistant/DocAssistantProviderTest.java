package com.giso.gateway.assistant;

import com.giso.gateway.GatewayConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocAssistantProviderTest {
    @Test
    void answersAppKeyQuestion() throws Exception {
        GatewayConfig c = new GatewayConfig();
        c.assistantDocsDirs = java.util.List.of(); // force classpath corpus
        c.assistantCorpusClasspath = "copilot/corpus";
        var provider = new DocAssistantProvider(c);
        assertTrue(provider.ready());
        ChatResponse resp = provider.chat(new ChatRequest(
                "App Key 怎么配置 test 和 prod？", java.util.List.of(), "tracking_flow", "default", ""));
        assertTrue(resp.answer().contains("App Key") || resp.answer().contains("video-android"),
                resp.answer());
        assertTrue(!resp.sources().isEmpty());
    }

    @Test
    void answersTrackingFlow() throws Exception {
        GatewayConfig c = new GatewayConfig();
        c.assistantDocsDirs = java.util.List.of();
        c.assistantCorpusClasspath = "copilot/corpus";
        var provider = new DocAssistantProvider(c);
        ChatResponse resp = provider.chat(new ChatRequest(
                "埋点上报流程", java.util.List.of(), null, "default", "- pages=1"));
        assertTrue(resp.answer().contains("登记") || resp.answer().contains("SDK"),
                resp.answer());
    }

    @Test
    void answersFlutterIntegration() throws Exception {
        GatewayConfig c = new GatewayConfig();
        c.assistantDocsDirs = java.util.List.of();
        c.assistantCorpusClasspath = "copilot/corpus";
        var provider = new DocAssistantProvider(c);
        ChatResponse resp = provider.chat(new ChatRequest(
                "Flutter 怎么接入 GISO", java.util.List.of(), null, "default", ""));
        assertTrue(resp.answer().contains("giso_tracker") || resp.answer().contains("Flutter"),
                resp.answer());
    }
}
