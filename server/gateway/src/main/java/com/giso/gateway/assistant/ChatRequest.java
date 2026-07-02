package com.giso.gateway.assistant;

import java.util.List;

/** 单轮或多轮对话请求。 */
public record ChatRequest(
        String message,
        List<ChatMessage> history,
        String topic,
        String spaceKey,
        String registryContext
) {
    public ChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
