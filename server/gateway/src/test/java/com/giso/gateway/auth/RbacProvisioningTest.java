package com.giso.gateway.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacProvisioningTest {
    @Test
    void defaultSpaceKeyFallsBackToDefault() {
        assertEquals("default", RbacRoles.defaultSpaceKey(null));
        assertEquals("default", RbacRoles.defaultSpaceKey(""));
        assertEquals("default", RbacRoles.defaultSpaceKey("__all__"));
        assertEquals("longvideo", RbacRoles.defaultSpaceKey("longvideo"));
    }

    @Test
    void normalizeSpaceRolePrefersExplicitViewer() {
        assertEquals(AdminUser.ROLE_VIEWER, RbacRoles.normalizeSpaceRole("viewer", "user"));
        assertEquals(AdminUser.ROLE_EDITOR, RbacRoles.normalizeSpaceRole("editor", "user"));
        // 无显式空间角色时，平台 user 默认只读（最小权限）
        assertEquals(AdminUser.ROLE_VIEWER, RbacRoles.normalizeSpaceRole(null, "user"));
    }

    @Test
    void allSpacesIsExplicitOptIn() {
        assertTrue(RbacRoles.provisionAllSpaces("__all__"));
        assertFalse(RbacRoles.provisionAllSpaces("longvideo"));
        assertFalse(RbacRoles.provisionAllSpaces(null));
    }
}
