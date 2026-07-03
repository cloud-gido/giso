package com.giso.gateway.auth;

import com.giso.gateway.space.SpaceService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理台 /me 与登录响应的统一用户画像（避免 login 与 /me 字段不一致）。 */
final class AdminUserProfile {
    private AdminUserProfile() { }

    static Map<String, Object> build(AuthContext ctx, SpaceService spaces,
            String currentSpace, boolean authEnabled, int pendingCount) throws Exception {
        String username = ctx.username();
        String role = ctx.role();
        var out = new LinkedHashMap<String, Object>();
        out.put("username", username);
        out.put("role", role);
        out.put("auth_enabled", authEnabled);
        out.put("current_space", currentSpace);
        if (spaces != null) {
            out.put("spaces", spaces.spacesForUser(username, role));
            out.put("space_role", spaces.spaceRole(username, role, currentSpace));
        } else {
            out.put("spaces", List.of(Map.of(
                    "space_key", SpaceService.DEFAULT_SPACE,
                    "display_name", "默认空间",
                    "role", role)));
            out.put("space_role", role);
        }
        if (pendingCount >= 0) {
            out.put("pending_count", pendingCount);
        }
        return out;
    }
}
