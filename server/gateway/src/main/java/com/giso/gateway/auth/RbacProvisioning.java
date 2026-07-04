package com.giso.gateway.auth;

import com.giso.gateway.space.SpaceService;

import java.util.List;
import java.util.Map;

/** 账号创建后的空间成员自动授权（避免「有账号、无空间」无法登录）。 */
public final class RbacProvisioning {
    private RbacProvisioning() { }

    /**
     * 为平台用户补齐空间成员；system_admin 跳过。
     *
     * @return null 成功，否则错误信息
     */
    public static String provisionSpaceAccess(
            SpaceService spaces,
            String username,
            String platformRole,
            String spaceKey,
            String spaceRole,
            boolean allSpaces) throws Exception {
        if (spaces == null || username == null || username.isBlank()) return null;
        String normalized = RbacRoles.normalizePlatformRole(platformRole);
        if (normalized == null || !RbacRoles.requiresSpaceMembership(normalized)) return null;

        String memberRole = RbacRoles.normalizeSpaceRole(spaceRole, platformRole);
        if (allSpaces || RbacRoles.provisionAllSpaces(spaceKey)) {
            List<Map<String, Object>> active = spaces.listSpaces().stream()
                    .filter(sp -> "active".equals(sp.get("status")))
                    .toList();
            if (active.isEmpty()) {
                return "尚无可用空间，请先创建空间";
            }
            for (Map<String, Object> sp : active) {
                String key = (String) sp.get("space_key");
                String err = spaces.saveMember(key, username, memberRole);
                if (err != null && !err.startsWith("ADDED:") && !err.startsWith("UPDATED:")) {
                    return err;
                }
            }
            return null;
        }
        String key = RbacRoles.defaultSpaceKey(spaceKey);
        if (!spaces.spaceExists(key)) {
            return "空间「" + key + "」不存在";
        }
        String err = spaces.saveMember(key, username, memberRole);
        if (err != null && !err.startsWith("ADDED:") && !err.startsWith("UPDATED:")) {
            return err;
        }
        return null;
    }

    /** 登录前检查：非平台管理员须至少属于一个 active 空间。 */
    public static boolean hasSpaceAccess(SpaceService spaces, String username, String platformRole)
            throws Exception {
        if (spaces == null || AdminUser.isSystemAdmin(platformRole)) return true;
        return !spaces.spacesForUser(username, platformRole).isEmpty();
    }
}
