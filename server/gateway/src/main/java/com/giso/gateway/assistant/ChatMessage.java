package com.giso.gateway.assistant;

/** 对话消息。 */
public record ChatMessage(String role, String content) {
    public static ChatMessage user(String c) { return new ChatMessage("user", c); }
    public static ChatMessage assistant(String c) { return new ChatMessage("assistant", c); }
}
