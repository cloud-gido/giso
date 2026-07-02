package com.giso.gateway.auth;

/** 管理台账号（配置种子或 PostgreSQL 持久化）。 */
public record AdminUser(String username, String password, String role) {
    /** 平台超级管理员（PostgreSQL 全局角色）。 */
    public static final String ROLE_SYSTEM_ADMIN = "system_admin";
    /** 普通平台用户（空间权限见 space_members）。 */
    public static final String ROLE_USER = "user";
    /** 本地 yaml 开发模式兼容别名。 */
    public static final String ROLE_ADMIN = "admin";

    /** 空间内角色（space_members）。 */
    public static final String ROLE_SPACE_ADMIN = "space_admin";
    public static final String ROLE_EDITOR = "editor";
    public static final String ROLE_VIEWER = "viewer";

    /** 空库且未配 Doppler 时的默认管理员（与本地 compose 一致）；登录后请在「账号管理」改密。 */
    public static final String DEFAULT_ADMIN_USER = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    public static boolean isSystemAdmin(String role) {
        return ROLE_SYSTEM_ADMIN.equals(role) || ROLE_ADMIN.equals(role);
    }
}
