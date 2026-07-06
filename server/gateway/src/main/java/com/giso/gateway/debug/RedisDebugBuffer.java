package com.giso.gateway.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.List;

/** Redis 共享联调缓冲：多 Gateway 副本读写同一份近期事件。 */
public final class RedisDebugBuffer implements DebugBuffer {
    private static final ObjectMapper M = new ObjectMapper();

    private final JedisPool pool;
    private final DebugBufferKeys keys;
    private final int recentMax;
    private final int ttlSec;

    public RedisDebugBuffer(RedisConnections.Info redis, String keyPrefix, int recentMax, int ttlSec) {
        if (redis == null) {
            throw new IllegalArgumentException("debug_buffer redis connection required when backend=redis");
        }
        this.pool = RedisConnections.createPool(redis);
        this.keys = new DebugBufferKeys(keyPrefix);
        this.recentMax = Math.max(1, recentMax);
        this.ttlSec = Math.max(60, ttlSec);
    }

    @Override
    public void append(ObjectNode wrapped, String spaceKey) {
        String json = wrapped.toString();
        String listKey = keys.recentList(spaceKey);
        String channel = keys.pubChannel(spaceKey);
        try (Jedis j = pool.getResource()) {
            j.lpush(listKey, json);
            j.ltrim(listKey, 0, recentMax - 1);
            j.expire(listKey, ttlSec);
            j.publish(channel, json);
        } catch (RuntimeException e) {
            System.err.println("[redis-debug-buffer] append failed: " + e.getMessage());
        }
    }

    @Override
    public List<ObjectNode> recent(int limit, String spaceKey, String did, String event, String status) {
        if (spaceKey == null || spaceKey.isBlank()) {
            return DebugBufferFilter.filter(loadAllRecent(), limit, null, did, event, status);
        }
        return DebugBufferFilter.filter(loadList(keys.recentList(spaceKey)), limit, spaceKey, did, event, status);
    }

    @Override
    public List<ObjectNode> recentByDid(String did) {
        List<ObjectNode> out = DebugBufferFilter.byDid(loadAllRecent(), did);
        java.util.Collections.reverse(out);
        return out;
    }

    @Override
    public void clearRecent(String spaceKey) {
        try (Jedis j = pool.getResource()) {
            if (spaceKey == null || spaceKey.isBlank()) {
                ScanParams params = new ScanParams().match(keys.recentPattern()).count(100);
                String cursor = ScanParams.SCAN_POINTER_START;
                do {
                    ScanResult<String> scan = j.scan(cursor, params);
                    for (String key : scan.getResult()) j.del(key);
                    cursor = scan.getCursor();
                } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
                return;
            }
            j.del(keys.recentList(spaceKey));
        } catch (RuntimeException e) {
            System.err.println("[redis-debug-buffer] clear failed: " + e.getMessage());
        }
    }

    @Override
    public String backendName() {
        return "redis";
    }

    @Override
    public void close() {
        pool.close();
    }

    JedisPool pool() {
        return pool;
    }

    DebugBufferKeys keys() {
        return keys;
    }

    private List<ObjectNode> loadAllRecent() {
        List<ObjectNode> all = new ArrayList<>();
        try (Jedis j = pool.getResource()) {
            ScanParams params = new ScanParams().match(keys.recentPattern()).count(100);
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                ScanResult<String> scan = j.scan(cursor, params);
                for (String key : scan.getResult()) all.addAll(loadList(key));
                cursor = scan.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        }
        all.sort((a, b) -> Long.compare(b.path("stime").asLong(0), a.path("stime").asLong(0)));
        return all;
    }

    private List<ObjectNode> loadList(String listKey) {
        List<ObjectNode> out = new ArrayList<>();
        try (Jedis j = pool.getResource()) {
            List<String> raw = j.lrange(listKey, 0, recentMax - 1);
            for (String s : raw) {
                ObjectNode n = parse(s);
                if (n != null) out.add(n);
            }
        }
        return out;
    }

    private static ObjectNode parse(String json) {
        try {
            return (ObjectNode) M.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
