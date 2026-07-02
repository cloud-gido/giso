package com.giso.gateway.assistant;

import com.giso.gateway.Registry;
import com.giso.gateway.settings.SystemSettingsService;

import java.util.List;
import java.util.Map;

/** 组装登记上下文；Provider 随系统设置热更新。 */
public final class AssistantService {
    private final SystemSettingsService settings;
    private final Registry registry;

    public AssistantService(SystemSettingsService settings, Registry registry) {
        this.settings = settings;
        this.registry = registry;
    }

    public AssistantProvider provider() { return settings.assistantProvider(); }

    public ChatResponse chat(String message, List<ChatMessage> history, String topic, String spaceKey)
            throws Exception {
        return provider().chat(new ChatRequest(
                message, history, topic, spaceKey, buildRegistryContext(spaceKey)));
    }

    private String buildRegistryContext(String spaceKey) {
        if (registry == null || spaceKey == null) return "";
        Map<String, List<Map<String, Object>>> all = registry.all(spaceKey);
        long pages = all.getOrDefault("pages", List.of()).size();
        long elements = all.getOrDefault("elements", List.of()).size();
        long events = all.getOrDefault("events", List.of()).size();
        long params = all.getOrDefault("params", List.of()).size();
        return "- space: `" + spaceKey + "`\n"
                + "- 登记条目: params=" + params + ", pages=" + pages
                + ", elements=" + elements + ", events=" + events + "\n"
                + "- 待审批: " + registry.pendingCount(spaceKey);
    }
}
