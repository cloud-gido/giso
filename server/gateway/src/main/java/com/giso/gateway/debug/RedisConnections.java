package com.giso.gateway.debug;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

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
        boolean hasEmbeddedPassword = false;
        if (!auth.isEmpty()) {
            if (auth.startsWith(":")) {
                password = auth.substring(1);
                hasEmbeddedPassword = !password.isBlank();
            } else {
                int colon = auth.indexOf(':');
                if (colon > 0) {
                    username = auth.substring(0, colon);
                    password = auth.substring(colon + 1);
                    hasEmbeddedPassword = !password.isBlank();
                } else {
                    password = auth;
                    hasEmbeddedPassword = !password.isBlank();
                }
            }
        }
        password = password == null ? "" : password.trim();
        username = username == null ? "" : username.trim();
        password = percentDecode(password);
        username = percentDecode(username);
        // 完整 rediss://:token@host 中的 token 是最贴近 Redis 实例的凭据；
        // 只有 URL 未带密码时才使用单独的 PASSWORD，避免 Secret 滞后覆盖正确 URL。
        if (!hasEmbeddedPassword && overridePassword != null && !overridePassword.isBlank()) {
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

    /** ElastiCache auth-token 模式：仅 db/0，且 AUTH 只传密码，不传 ACL username。 */
    static Info normalizeForProvider(Info info) {
        if (!isElastiCacheHost(info.host())) return info;
        int db = 0;
        return new Info(info.scheme(), info.host(), info.port(), "", info.password(), db);
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

    private static String percentDecode(String value) {
        if (value == null || value.indexOf('%') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) + lo));
                    i += 2;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static void applyAuth(DefaultJedisClientConfig.Builder cfg, Info info) {
        // Keep protocol unset so Jedis uses legacy AUTH and does not send HELLO AUTH.
        // ElastiCache auth-token endpoints accept AUTH <password> for the default user.
        cfg.protocol(null);
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
