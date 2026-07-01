package com.giso.gateway.auth;

/** 管理台角色权限（admin / editor / viewer）。 */
public final class AdminPermissions {
    private AdminPermissions() { }

    public static boolean canEditRegistry(String role) {
        return AdminUser.ROLE_ADMIN.equals(role) || AdminUser.ROLE_EDITOR.equals(role);
    }

    public static boolean canApproveRegistry(String role) {
        return AdminUser.ROLE_ADMIN.equals(role);
    }

    public static boolean canPublishRegistry(String role) {
        return AdminUser.ROLE_ADMIN.equals(role);
    }

    public static boolean canClearBuffer(String role) {
        return AdminUser.ROLE_ADMIN.equals(role);
    }

    public static boolean canManageUsers(String role) {
        return AdminUser.ROLE_ADMIN.equals(role);
    }

    public static boolean editorMayEditStatus(String status) {
        return "pending".equals(status) || "draft".equals(status);
    }
}
