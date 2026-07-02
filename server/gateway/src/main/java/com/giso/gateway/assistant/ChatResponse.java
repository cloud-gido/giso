package com.giso.gateway.assistant;

import java.util.List;

/** Copilot 回复（含引用来源，便于审计）。 */
public record ChatResponse(
        String answer,
        String provider,
        List<String> sources,
        List<String> suggestedFollowups
) {
    public ChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        suggestedFollowups = suggestedFollowups == null ? List.of() : List.copyOf(suggestedFollowups);
    }
}
