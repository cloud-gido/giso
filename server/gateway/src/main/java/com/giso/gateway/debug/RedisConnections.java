package com.giso.gateway.debug;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.RedisProtocol;

/** 解析 Redis 连接（支持 rediss + 密码特殊字符），不依赖 java.net.URI。 */
public final class RedisConnections {
    public record Info(String scheme, String host, int port, String username, String password, int db) {
        public boolean ssl() {
            return "rediss".equalsIgnoreCase(scheme);
        }

        /** 日志用，密码打码 */
        public String safeLabel() {
            String user = username == null || username.isBlank() ? "" : username + "@";
            return scheme + "://" + user + host + ":" + port + "/" + db;
        }
    }

    private RedisConnections() { }

    public static Info parseUrl(String url, String overrideUsername, String overridePassword, int overrideDb) {
        String trimmed = url == null ? "" : url.trim();
        if (!isRedisUri(trimmed)) {
            throw new IllegalArgumentException("invalid redis url: " + trimmed);
        }
        int at = trimmed.lastIndexOf('@');
        String head;
        String tail;
        String auth;
        String scheme;
        if (at < 0) {
            scheme = trimmed.toLowerCase().startsWith("rediss://") ? "rediss" : "redis";
            int schemeEnd = trimmed.indexOf("://") + 3;
            tail = trimmed.substring(schemeEnd);
            auth = "";
        } else {
            head = trimmed.substring(0, at);
            tail = trimmed.substring(at + 1);
            scheme = head.toLowerCase().startsWith("rediss://") ? "rediss" : "redis";
            int schemeEnd = head.indexOf("://") + 3;
            auth = head.substring(schemeEnd);
        }
        String username = "";
        String password = "";
        if (!auth.isEmpty()) {
            if (auth.startsWith(":")) {
                password = auth.substring(1);
            } else {
                int colon = auth.indexOf(':');
                if (colon > 0) {
                    username = auth.substring(0, colon);
                    password = auth.substring(colon + 1);
                } else {
                    password = auth;
                }
            }
        }
        password = password == null ? "" : password.trim();
        username = username == null ? "" : username.trim();
        // PASSWORD 环境变量为运维主数据源（与 Doppler 截图一致时优先于 URL 内嵌）
        if (overridePassword != null && !overridePassword.isBlank()) {
            password = overridePassword.trim();
        }
        if ((username == null || username.isBlank()) && overrideUsername != null && !overrideUsername.isBlank()) {
            username = overrideUsername.trim();
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
        return normalizeForProvider(new Info(scheme, host, port, username, password, db));
    }

    /** ElastiCache：仅 db/0，ACL 默认用户 default；internal-redis 仍可用 db/2。 */
    static Info normalizeForProvider(Info info) {
        if (!isElastiCacheHost(info.host())) return info;
        String user = info.username();
        if (user == null || user.isBlank()) user = "default";
        int db = 0;
        return new Info(info.scheme(), info.host(), info.port(), user, info.password(), db);
    }

    static boolean isElastiCacheHost(String host) {
        return host != null && host.contains(".amazonaws.com");
    }

    public static Info fromParts(String scheme, String host, String username, String password, int port, int db) {
        String sch = scheme == null || scheme.isBlank() ? "redis" : scheme.trim();
        if (port <= 0) port = 6379;
        if (db < 0) db = 0;
        String user = username == null ? "" : username.trim();
        String pass = password == null ? "" : password.trim();
        return normalizeForProvider(new Info(sch, host.trim(), port, user, pass, db));
    }

    public static boolean isRedisUri(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase();
        return lower.startsWith("redis://") || lower.startsWith("rediss://");
    }

    private static void applyAuth(DefaultJedisClientConfig.Builder cfg, Info info) {
        cfg.protocol(RedisProtocol.RESP2);
        String user = info.username();
        String pass = info.password();
        if (user != null && !user.isBlank()) {
            cfg.user(user);
        }
        if (pass != null && !pass.isBlank()) {
            cfg.password(pass);
        }
    }

    public static JedisPool createPool(Info info) {
        HostAndPort hap = new HostAndPort(info.host(), info.port());
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                .database(info.db());
        applyAuth(cfg, info);
        if (info.ssl()) cfg.ssl(true);
        JedisPoolConfig poolCfg = new JedisPoolConfig();
        poolCfg.setMaxTotal(16);
        return new JedisPool(poolCfg, hap, cfg.build());
    }

    public static Jedis createClient(Info info) {
        HostAndPort hap = new HostAndPort(info.host(), info.port());
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                .database(info.db());
        applyAuth(cfg, info);
        if (info.ssl()) cfg.ssl(true);
        return new Jedis(hap, cfg.build());
    }
}
