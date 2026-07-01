package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.sun.net.httpserver.HttpExchange;

/**
 * 管理台鉴权（对齐 GIDO：Ingress IP 白名单 + 应用层账号）。
 * <p>
 * 生产：Doppler 种子账号 → PostgreSQL admin_users（BCrypt）；本地：yaml/env 明文比对。
 */
public final class AdminAuth {
    private final AdminUserStore store;

    public AdminAuth(GatewayConfig config, PostgresRegistryStore registryStore) throws Exception {
        if ("postgres".equalsIgnoreCase(config.registryBackend) && registryStore != null) {
            this.store = PostgresAdminUserStore.create(registryStore.dataSource(), config);
        } else {
            this.store = new ConfigAdminUserStore(config);
        }
    }

    /** 未配置账号时视为 admin（本地零配置）。 */
    public String role(HttpExchange ex) {
        try {
            if (!store.authEnabled()) return AdminUser.ROLE_ADMIN;
            String decoded = basicCredentials(ex);
            if (decoded == null) return null;
            int i = decoded.indexOf(':');
            if (i <= 0) return null;
            String user = decoded.substring(0, i);
            String pass = decoded.substring(i + 1);
            return store.authenticate(user, pass);
        } catch (Exception e) {
            return null;
        }
    }

    public String operator(HttpExchange ex) {
        String role = role(ex);
        if (role == null) return null;
        String decoded = basicCredentials(ex);
        if (decoded == null) return "admin";
        int i = decoded.indexOf(':');
        return i > 0 ? decoded.substring(0, i) : "admin";
    }

    public boolean unauthorized(HttpExchange ex) {
        try {
            return role(ex) == null && store.authEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    public java.util.Map<String, Object> me(HttpExchange ex) throws Exception {
        String role = role(ex);
        if (role == null && store.authEnabled()) return null;
        String username = operator(ex);
        var out = new java.util.LinkedHashMap<String, Object>();
        out.put("username", username);
        out.put("role", role == null ? AdminUser.ROLE_ADMIN : role);
        out.put("auth_enabled", store.authEnabled());
        return out;
    }

    public java.util.List<java.util.Map<String, Object>> listUsers() throws Exception {
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
