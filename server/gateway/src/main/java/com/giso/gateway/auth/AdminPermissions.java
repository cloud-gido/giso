package com.giso.gateway.auth;

/** 管理台权限：平台 system_admin + 空间 space_admin / editor / viewer。 */
public final class AdminPermissions {
    private AdminPermissions() { }

    public static boolean canEditRegistry(String globalRole, String spaceRole) {
        if (AdminUser.isSystemAdmin(globalRole)) return true;
        if (AdminUser.ROLE_SPACE_ADMIN.equals(spaceRole)) return true;
        return AdminUser.ROLE_EDITOR.equals(spaceRole);
    }

    public static boolean canApproveRegistry(String globalRole, String spaceRole) {
        return AdminUser.isSystemAdmin(globalRole) || AdminUser.ROLE_SPACE_ADMIN.equals(spaceRole);
    }

    public static boolean canPublishRegistry(String globalRole, String spaceRole) {
        return canApproveRegistry(globalRole, spaceRole);
    }

    public static boolean canClearBuffer(String globalRole, String spaceRole) {
        return AdminUser.isSystemAdmin(globalRole) || AdminUser.ROLE_SPACE_ADMIN.equals(spaceRole);
    }

    public static boolean canManageUsers(String globalRole) {
        return AdminUser.isSystemAdmin(globalRole);
    }

    public static boolean canManageSpaces(String globalRole) {
        return AdminUser.isSystemAdmin(globalRole);
    }

    public static boolean canManageSpaceMembers(String globalRole, String spaceRole) {
        return AdminUser.isSystemAdmin(globalRole) || AdminUser.ROLE_SPACE_ADMIN.equals(spaceRole);
    }

    public static boolean canManageSystemSettings(String globalRole) {
        return AdminUser.isSystemAdmin(globalRole);
    }

    public static boolean editorMayEditStatus(String status) {
        return "pending".equals(status) || "draft".equals(status);
    }
}
