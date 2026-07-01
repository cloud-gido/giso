package com.giso.gateway.auth;

/** 管理台账号（配置种子或 PostgreSQL 持久化）。 */
public record AdminUser(String username, String password, String role) {
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_EDITOR = "editor";
    public static final String ROLE_VIEWER = "viewer";

    /** 空库且未配 Doppler 时的默认管理员（与本地 compose 一致）；登录后请在「账号管理」改密。 */
    public static final String DEFAULT_ADMIN_USER = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";
}
