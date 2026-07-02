package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.giso.gateway.space.SpaceService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理台鉴权（对齐 GIDO：Ingress IP 白名单 + 应用层账号）。
 * <p>
 * 生产：Doppler 种子账号 → PostgreSQL admin_users（BCrypt）；本地 yaml 未配账号时免登录。
 */
public final class AdminAuth {
    private static final String SESSION_COOKIE = "giso_admin_sess";
    /** 登录后会话缓存，避免每个管理 API 都 BCrypt + 查库（只读浏览注册表时体感接近 yaml 模式）。 */
    private static final long SESSION_CACHE_TTL_MS = 30 * 60 * 1000L;

    private final AdminUserStore store;
    private final SpaceService spaces;
    private final ConcurrentHashMap<String, CachedSession> sessionCache = new ConcurrentHashMap<>();

    private record CachedSession(String username, String role, long expiresAt) { }

    public AdminAuth(GatewayConfig config, PostgresRegistryStore registryStore, SpaceService spaces)
            throws Exception {
        if ("postgres".equalsIgnoreCase(config.registryBackend) && registryStore != null) {
            this.store = PostgresAdminUserStore.create(registryStore.dataSource(), config);
        } else {
            this.store = new ConfigAdminUserStore(config);
        }
        this.spaces = spaces;
    }

    public AuthContext resolve(HttpExchange ex) {
        try {
            if (!store.authEnabled()) {
                return new AuthContext("admin", AdminUser.ROLE_ADMIN);
            }
            String decoded = credentials(ex);
            if (decoded == null) return null;
            long now = System.currentTimeMillis();
            CachedSession cached = sessionCache.get(decoded);
            if (cached != null) {
                if (cached.expiresAt() > now) {
                    return new AuthContext(cached.username(), cached.role());
                }
                sessionCache.remove(decoded);
            }
            int i = decoded.indexOf(':');
            if (i <= 0) return null;
            String user = decoded.substring(0, i);
            String pass = decoded.substring(i + 1);
            String role = store.authenticate(user, pass);
            if (role == null) return null;
            sessionCache.put(decoded, new CachedSession(user, role, now + SESSION_CACHE_TTL_MS));
            return new AuthContext(user, role);
        } catch (Exception e) {
            return null;
        }
    }

    public String role(HttpExchange ex) {
        AuthContext ctx = resolve(ex);
        return ctx == null ? null : ctx.role();
    }

    public String operator(HttpExchange ex) {
        AuthContext ctx = resolve(ex);
        return ctx == null ? null : ctx.username();
    }

    public boolean unauthorized(HttpExchange ex) {
        try {
            return resolve(ex) == null && store.authEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    public Map<String, Object> me(HttpExchange ex, String currentSpace) throws Exception {
        String role = role(ex);
        if (role == null && store.authEnabled()) return null;
        String username = operator(ex);
        var out = new LinkedHashMap<String, Object>();
        out.put("username", username);
        out.put("role", role == null ? AdminUser.ROLE_ADMIN : role);
        out.put("auth_enabled", store.authEnabled());
        out.put("current_space", currentSpace);
        if (spaces != null) {
            out.put("spaces", spaces.spacesForUser(username, role == null ? AdminUser.ROLE_ADMIN : role));
            String spaceRole = spaces.spaceRole(username, role, currentSpace);
            out.put("space_role", spaceRole);
        } else {
            out.put("spaces", List.of(Map.of(
                    "space_key", SpaceService.DEFAULT_SPACE,
                    "display_name", "默认空间",
                    "role", role == null ? AdminUser.ROLE_ADMIN : role)));
            out.put("space_role", role == null ? AdminUser.ROLE_ADMIN : role);
        }
        return out;
    }

    public boolean login(HttpExchange ex, String username, String password) throws Exception {
        if (!store.authEnabled()) return false;
        String role = store.authenticate(username, password);
        if (role == null) return false;
        setSessionCookie(ex, username, password);
        sessionCache.put(username + ":" + password,
                new CachedSession(username, role, System.currentTimeMillis() + SESSION_CACHE_TTL_MS));
        return true;
    }

    public void logout(HttpExchange ex) throws IOException {
        String decoded = credentials(ex);
        if (decoded != null) sessionCache.remove(decoded);
        clearSessionCookie(ex);
    }

    public List<Map<String, Object>> listUsers() throws Exception {
        return store.listUsers();
    }

    public String saveUser(String username, String password, String role, String displayName) throws Exception {
        return store.saveUser(username, password, role, displayName);
    }

    public String disableUser(String username) throws Exception {
        return store.disableUser(username);
    }

    public boolean authEnabled() {
        try {
            return store.authEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    private static String credentials(HttpExchange ex) {
        String header = basicCredentials(ex);
        if (header != null) return header;
        return sessionCredentials(ex);
    }

    private static void setSessionCookie(HttpExchange ex, String user, String password) {
        String val = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        ex.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=" + val + "; Path=/admin; HttpOnly; SameSite=Lax; Max-Age=86400");
    }

    private static void clearSessionCookie(HttpExchange ex) {
        ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/admin; HttpOnly; Max-Age=0");
    }

    private static String sessionCredentials(HttpExchange ex) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || cookies.isBlank()) return null;
        String prefix = SESSION_COOKIE + "=";
        for (String part : cookies.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.startsWith(prefix)) continue;
            try {
                return new String(Base64.getDecoder().decode(trimmed.substring(prefix.length())),
                        StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private static String basicCredentials(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) return null;
        try {
            return new String(java.util.Base64.getDecoder().decode(header.substring(6)),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
