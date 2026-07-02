package com.giso.gateway.assistant;

import com.giso.gateway.GatewayConfig;

import java.util.List;

/** 按配置组装 Copilot 后端（doc / openai / gido_proxy）。 */
public final class AssistantFactory {
    private AssistantFactory() { }

    public static AssistantProvider create(GatewayConfig config) throws Exception {
        if (!config.assistantEnabled) {
            return new DisabledAssistantProvider();
        }
        String type = config.assistantProvider == null ? "doc" : config.assistantProvider.trim().toLowerCase();
        return switch (type) {
            case "doc", "docs", "local" -> new DocAssistantProvider(config);
            case "openai", "llm" -> new OpenAiAssistantProvider(config);
            case "gido_proxy", "gido" -> new GidoProxyAssistantProvider(config);
            default -> throw new IllegalArgumentException("未知 assistant provider: " + type
                    + "（支持 doc / openai / gido_proxy）");
        };
    }

    private static final class DisabledAssistantProvider implements AssistantProvider {
        @Override public String name() { return "disabled"; }
        @Override public boolean ready() { return false; }
        @Override public ChatResponse chat(ChatRequest request) {
            return new ChatResponse("Copilot 未启用。设置 assistant.enabled=true 或 GISO_ASSISTANT_ENABLED=1。",
                    name(), List.of(), List.of());
        }
    }
}
