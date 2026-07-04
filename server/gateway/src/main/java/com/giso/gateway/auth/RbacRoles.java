package com.giso.gateway.auth;

import com.giso.gateway.space.SpaceService;

import java.util.Set;

/**
 * RBAC 角色规范化与准入规则（平台角色 + 空间角色两层模型唯一入口）。
 */
public final class RbacRoles {
    private static final Set<String> PLATFORM_ROLES = Set.of(
            AdminUser.ROLE_SYSTEM_ADMIN, AdminUser.ROLE_USER,
            AdminUser.ROLE_ADMIN, AdminUser.ROLE_EDITOR, AdminUser.ROLE_VIEWER);
    private static final Set<String> SPACE_ROLES = Set.of(
            AdminUser.ROLE_SPACE_ADMIN, AdminUser.ROLE_EDITOR, AdminUser.ROLE_VIEWER);

    private RbacRoles() { }

    /** 将 API/配置入参规范为 admin_users.role（system_admin / user）。 */
    public static String normalizePlatformRole(String role) {
        if (role == null || role.isBlank()) return AdminUser.ROLE_USER;
        if (!PLATFORM_ROLES.contains(role)) return null;
        if (AdminUser.ROLE_ADMIN.equals(role)) return AdminUser.ROLE_SYSTEM_ADMIN;
        if (AdminUser.ROLE_EDITOR.equals(role) || AdminUser.ROLE_VIEWER.equals(role)) {
            return AdminUser.ROLE_USER;
        }
        return role;
    }

    /**
     * 解析空间成员角色：优先 space_role；否则从平台角色 editor/viewer 推断；
     * 平台 user 默认 viewer（只读，最小权限）。
     */
    public static String normalizeSpaceRole(String spaceRole, String platformRoleInput) {
        if (spaceRole != null && !spaceRole.isBlank() && SPACE_ROLES.contains(spaceRole)) {
            return spaceRole;
        }
        if (AdminUser.ROLE_EDITOR.equals(platformRoleInput)) return AdminUser.ROLE_EDITOR;
        if (AdminUser.ROLE_VIEWER.equals(platformRoleInput)) return AdminUser.ROLE_VIEWER;
        if (AdminUser.ROLE_SPACE_ADMIN.equals(platformRoleInput)) return AdminUser.ROLE_SPACE_ADMIN;
        return AdminUser.ROLE_VIEWER;
    }

    public static boolean requiresSpaceMembership(String platformRole) {
        return !AdminUser.isSystemAdmin(platformRole);
    }

    public static String defaultSpaceKey(String spaceKey) {
        if (spaceKey == null || spaceKey.isBlank() || "__all__".equals(spaceKey)) {
            return SpaceService.DEFAULT_SPACE;
        }
        return spaceKey.trim();
    }

    public static boolean provisionAllSpaces(String spaceKey) {
        return "__all__".equals(spaceKey);
    }
}
