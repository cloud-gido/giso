package com.giso.gateway.assistant;

/** 插拔式 Copilot 后端（对齐 SinkFactory / EventSink 模式）。 */
public interface AssistantProvider {
    String name();

    /** 是否已配置可用（如 doc 默认可用；openai 需 API Key）。 */
    boolean ready();

    ChatResponse chat(ChatRequest request) throws Exception;
}
