package com.giso.gateway.debug;

import com.giso.gateway.SseHub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/** 订阅 Redis Pub/Sub，将联调事件 fan-out 到本 Pod 的 SSE 连接。 */
public final class RedisSseRelay implements AutoCloseable {
    private final RedisConnections.Info redis;
    private final String pubPattern;
    private final String keyPrefix;
    private final SseHub sse;
    private volatile Thread thread;
    private volatile Jedis subscriber;

    public RedisSseRelay(RedisConnections.Info redis, String keyPrefix, SseHub sse) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.pubPattern = new DebugBufferKeys(keyPrefix).pubPattern();
        this.sse = sse;
    }

    public void start() {
        thread = new Thread(this::run, "redis-sse-relay");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Jedis jedis = null;
            try {
                jedis = RedisConnections.createClient(redis);
                subscriber = jedis;
                jedis.psubscribe(new JedisPubSub() {
                    @Override
                    public void onPMessage(String pattern, String channel, String message) {
                        String space = DebugBufferKeys.spaceFromPubChannel(channel, keyPrefix);
                        sse.broadcast(message, space);
                    }
                }, pubPattern);
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("[redis-sse-relay] disconnected from " + redis.safeLabel()
                            + " (auth=" + redis.authMode() + "): " + e.getMessage()
                            + "; reconnect in 2s");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                try {
                    if (jedis != null) jedis.close();
                } catch (Exception ignored) { }
                subscriber = null;
            }
        }
    }

    @Override
    public void close() {
        if (thread != null) thread.interrupt();
        Jedis j = subscriber;
        if (j != null) {
            try {
                j.close();
            } catch (Exception ignored) { }
        }
    }
}
