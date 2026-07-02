package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.giso.gateway.space.SpaceService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理台鉴权（对齐 GIDO：Ingress IP 白名单 + 应用层账号）。
 * <p>
 * 会话模型：opaque session id + HttpOnly Cookie；密码不落 Cookie，退出删服务端会话。
 */
public final class AdminAuth {
    private final AdminUserStore store;
    private final SpaceService spaces;
    private final AdminSessionManager sessions;

    public AdminAuth(GatewayConfig config, PostgresRegistryStore registryStore, SpaceService spaces)
            throws Exception {
        AdminSessionStore sessionStore;
        if ("postgres".equalsIgnoreCase(config.registryBackend) && registryStore != null) {
            this.store = PostgresAdminUserStore.create(registryStore.dataSource(), config);
            String schema = config.dbSchema == null || config.dbSchema.isBlank()
                    ? "public" : config.dbSchema;
            sessionStore = new PostgresAdminSessionStore(registryStore.dataSource(), schema);
        } else {
            this.store = new ConfigAdminUserStore(config);
            sessionStore = new InMemoryAdminSessionStore();
        }
        this.spaces = spaces;
        this.sessions = new AdminSessionManager(sessionStore);
    }

    public AuthContext resolve(HttpExchange ex) {
        try {
            if (!store.authEnabled()) {
                return new AuthContext("admin", AdminUser.ROLE_ADMIN);
            }
            return sessions.resolve(ex);
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
        sessions.login(ex, username, role);
        return true;
    }

    public void logout(HttpExchange ex) throws IOException {
        try {
            sessions.logout(ex);
        } catch (SQLException e) {
            AdminSessionCookies.clearAll(ex);
        }
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
}
