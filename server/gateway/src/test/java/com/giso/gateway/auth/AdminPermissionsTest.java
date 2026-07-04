package com.giso.gateway.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPermissionsTest {
    @Test
    void platformAdminFullAccess() {
        assertTrue(AdminPermissions.canEditRegistry("system_admin", null));
        assertTrue(AdminPermissions.canApproveRegistry("admin", null));
        assertTrue(AdminPermissions.canManageUsers("system_admin"));
        assertTrue(AdminPermissions.canManageSpaces("system_admin"));
        assertTrue(AdminPermissions.canManageSystemSettings("admin"));
    }

    @Test
    void spaceRoles() {
        assertTrue(AdminPermissions.canEditRegistry("user", AdminUser.ROLE_EDITOR));
        assertFalse(AdminPermissions.canEditRegistry("user", AdminUser.ROLE_VIEWER));
        assertTrue(AdminPermissions.canApproveRegistry("user", AdminUser.ROLE_SPACE_ADMIN));
        assertFalse(AdminPermissions.canApproveRegistry("user", AdminUser.ROLE_EDITOR));
        assertFalse(AdminPermissions.canManageUsers("user"));
        assertTrue(AdminPermissions.canManageSpaceMembers("user", AdminUser.ROLE_SPACE_ADMIN));
        assertFalse(AdminPermissions.canManageSpaceMembers("user", AdminUser.ROLE_VIEWER));
    }

    @Test
    void editorMayEditStatus() {
        assertTrue(AdminPermissions.editorMayEditStatus("draft"));
        assertTrue(AdminPermissions.editorMayEditStatus("pending"));
        assertFalse(AdminPermissions.editorMayEditStatus("live"));
    }
}
