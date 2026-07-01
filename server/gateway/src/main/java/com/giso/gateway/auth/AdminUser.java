package com.giso.gateway.auth;

/** 管理台账号（配置种子或 PostgreSQL 持久化）。 */
public record AdminUser(String username, String password, String role) {
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_EDITOR = "editor";
    public static final String ROLE_VIEWER = "viewer";
}
