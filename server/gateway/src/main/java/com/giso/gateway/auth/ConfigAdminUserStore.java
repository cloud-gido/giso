package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 gateway.yaml / 环境变量读取账号（本地开发或未启用 PG 时）。 */
public final class ConfigAdminUserStore implements AdminUserStore {
    private final List<AdminUser> users;

    public ConfigAdminUserStore(GatewayConfig config) {
        this.users = GatewayConfig.resolveAuthUsers(config);
    }

    @Override
    public boolean authEnabled() {
        return !users.isEmpty();
    }

    @Override
    public String authenticate(String username, String password) {
        if (username == null || password == null) return null;
        for (AdminUser u : users) {
            if (u.username().equals(username) && u.password().equals(password)) {
                return u.role();
            }
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AdminUser u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", u.username());
            row.put("role", u.role());
            row.put("source", "config");
            out.add(row);
        }
        return out;
    }

    @Override
    public String saveUser(String username, String password, String role, String displayName) {
        return "本地配置模式不支持账号管理，请使用 PostgreSQL 后端";
    }

    @Override
    public String disableUser(String username) {
        return "本地配置模式不支持账号管理，请使用 PostgreSQL 后端";
    }
}
