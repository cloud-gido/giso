package com.giso.gateway.debug;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.GatewayConfig;
import com.giso.gateway.SseHub;

/** 按配置创建联调缓冲（memory | redis）。 */
public final class DebugBuffers {
    public record Handle(DebugBuffer buffer, AutoCloseable relay) {
        public void close() {
            try {
                if (relay != null) relay.close();
            } catch (Exception ignored) { }
            buffer.close();
        }
    }

    private DebugBuffers() { }

    public static Handle create(GatewayConfig config, SseHub sse) {
        String backend = config.debugBufferBackend == null ? "memory" : config.debugBufferBackend.trim();
        if ("redis".equalsIgnoreCase(backend)) {
            if (config.debugRedisInfo == null) {
                throw new IllegalStateException("debug_buffer.backend=redis but redis connection is not configured");
            }
            RedisDebugBuffer buffer = new RedisDebugBuffer(
                    config.debugRedisInfo,
                    config.debugRedisKeyPrefix,
                    config.adminRecentBuffer,
                    config.debugBufferTtlSec);
            RedisSseRelay relay = new RedisSseRelay(
                    config.debugRedisInfo, config.debugRedisKeyPrefix, sse);
            relay.start();
            System.out.println("  debug_buffer  : redis (" + config.debugRedisInfo.safeLabel() + ")");
            return new Handle(buffer, relay);
        }
        MemoryDebugBuffer buffer = new MemoryDebugBuffer(
                config.adminRecentBuffer,
                (ObjectNode w, String space) -> sse.broadcast(w.toString(), space));
        System.out.println("  debug_buffer  : memory");
        return new Handle(buffer, null);
    }
}
