package com.giso.gateway.debug;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/** 解析 Redis 连接（支持 rediss + 密码特殊字符），不依赖 java.net.URI。 */
public final class RedisConnections {
    public record Info(String scheme, String host, int port, String password, int db) {
        public boolean ssl() {
            return "rediss".equalsIgnoreCase(scheme);
        }

        /** 日志用，密码打码 */
        public String safeLabel() {
            return scheme + "://" + host + ":" + port + "/" + db;
        }
    }

    private RedisConnections() { }

    public static Info parseUrl(String url, String overridePassword, int overrideDb) {
        String trimmed = url == null ? "" : url.trim();
        if (!isRedisUri(trimmed)) {
            throw new IllegalArgumentException("invalid redis url: " + trimmed);
        }
        int at = trimmed.lastIndexOf('@');
        if (at < 0) {
            throw new IllegalArgumentException("redis url missing @ host: " + trimmed);
        }
        String head = trimmed.substring(0, at);
        String tail = trimmed.substring(at + 1);

        String scheme = head.toLowerCase().startsWith("rediss://") ? "rediss" : "redis";
        int schemeEnd = head.indexOf("://") + 3;
        String auth = head.substring(schemeEnd);
        String password = "";
        if (auth.startsWith(":")) password = auth.substring(1);
        else if (auth.contains(":")) {
            int colon = auth.indexOf(':');
            password = auth.substring(colon + 1);
        }
        if (overridePassword != null && !overridePassword.isBlank()) {
            password = overridePassword;
        }

        String hostPort;
        int db = 0;
        int slash = tail.indexOf('/');
        if (slash >= 0) {
            hostPort = tail.substring(0, slash);
            String dbPart = tail.substring(slash + 1);
            if (!dbPart.isBlank()) db = Integer.parseInt(dbPart);
        } else {
            hostPort = tail;
        }
        if (overrideDb >= 0) db = overrideDb;

        int port = 6379;
        String host;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            port = Integer.parseInt(hostPort.substring(colon + 1));
        } else {
            host = hostPort;
        }
        return new Info(scheme, host, port, password, db);
    }

    public static Info fromParts(String scheme, String host, String password, int port, int db) {
        String sch = scheme == null || scheme.isBlank() ? "redis" : scheme.trim();
        if (port <= 0) port = 6379;
        if (db < 0) db = 0;
        return new Info(sch, host.trim(), port, password, db);
    }

    public static boolean isRedisUri(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase();
        return lower.startsWith("redis://") || lower.startsWith("rediss://");
    }

    public static JedisPool createPool(Info info) {
        HostAndPort hap = new HostAndPort(info.host(), info.port());
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                .database(info.db());
        if (info.password() != null && !info.password().isBlank()) {
            cfg.password(info.password());
        }
        if (info.ssl()) cfg.ssl(true);
        JedisPoolConfig poolCfg = new JedisPoolConfig();
        poolCfg.setMaxTotal(16);
        return new JedisPool(poolCfg, hap, cfg.build());
    }

    public static Jedis createClient(Info info) {
        HostAndPort hap = new HostAndPort(info.host(), info.port());
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                .database(info.db());
        if (info.password() != null && !info.password().isBlank()) {
            cfg.password(info.password());
        }
        if (info.ssl()) cfg.ssl(true);
        return new Jedis(hap, cfg.build());
    }
}
