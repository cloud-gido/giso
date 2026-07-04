package com.giso.gateway.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacRolesTest {
    @Test
    void normalizePlatformRole() {
        assertEquals(AdminUser.ROLE_SYSTEM_ADMIN, RbacRoles.normalizePlatformRole("admin"));
        assertEquals(AdminUser.ROLE_SYSTEM_ADMIN, RbacRoles.normalizePlatformRole("system_admin"));
        assertEquals(AdminUser.ROLE_USER, RbacRoles.normalizePlatformRole("editor"));
        assertEquals(AdminUser.ROLE_USER, RbacRoles.normalizePlatformRole("viewer"));
        assertNull(RbacRoles.normalizePlatformRole("superuser"));
    }

    @Test
    void normalizeSpaceRole() {
        assertEquals(AdminUser.ROLE_VIEWER, RbacRoles.normalizeSpaceRole(null, "user"));
        assertEquals(AdminUser.ROLE_EDITOR, RbacRoles.normalizeSpaceRole("", "editor"));
        assertEquals(AdminUser.ROLE_SPACE_ADMIN, RbacRoles.normalizeSpaceRole("space_admin", "user"));
    }

    @Test
    void requiresSpaceMembership() {
        assertFalse(RbacRoles.requiresSpaceMembership(AdminUser.ROLE_SYSTEM_ADMIN));
        assertFalse(RbacRoles.requiresSpaceMembership(AdminUser.ROLE_ADMIN));
        assertTrue(RbacRoles.requiresSpaceMembership(AdminUser.ROLE_USER));
    }

    @Test
    void provisionAllSpaces() {
        assertTrue(RbacRoles.provisionAllSpaces("__all__"));
        assertFalse(RbacRoles.provisionAllSpaces("default"));
    }
}
