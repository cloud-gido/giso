package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.giso.gateway.space.SpaceService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理台鉴权门面（对齐 GIDO：Ingress 白名单 + 应用层账号 + 服务端会话）。
 * <p>
 * 职责划分：{@link AdminUserStore} 校验凭证 · {@link AdminSessionManager} 管会话 ·
 * {@link AdminUserProfile} 组装 /me · {@link AdminPermissions} 管授权。
 */
public final class AdminAuth {
    private final AdminUserStore store;
    private final SpaceService spaces;
    private final AdminSessionManager sessions;
    private final LoginSecurity loginSecurity;

    public AdminAuth(GatewayConfig config, PostgresRegistryStore registryStore, SpaceService spaces)
            throws Exception {
        AdminSessionStore sessionStore;
        String dbSchema = config.dbSchema == null || config.dbSchema.isBlank()
                ? "public" : config.dbSchema;
        if ("postgres".equalsIgnoreCase(config.registryBackend) && registryStore != null) {
            this.store = PostgresAdminUserStore.create(registryStore.dataSource(), config);
            sessionStore = new PostgresAdminSessionStore(registryStore.dataSource(), dbSchema);
        } else {
            this.store = new ConfigAdminUserStore(config);
            sessionStore = new InMemoryAdminSessionStore();
            registryStore = null;
        }
        this.spaces = spaces;
        this.sessions = new AdminSessionManager(sessionStore);
        this.loginSecurity = LoginSecurity.create(config.loginSecurity, registryStore, dbSchema);
        syncSeedSpaceMemberships(config);
    }

    /** 为配置中的 viewer/editor 种子账号补齐各空间成员（仅缺失时插入）。 */
    private void syncSeedSpaceMemberships(GatewayConfig config) {
        if (spaces == null || !(store instanceof PostgresAdminUserStore)) return;
        try {
            spaces.syncSeedSpaceMembers(GatewayConfig.resolveAuthUsers(config));
        } catch (Exception e) {
            System.err.println("[giso] sync seed space members failed: " + e.getMessage());
        }
    }

    /** 当前请求是否未登录（auth 开启且 Cookie 无效时为 true）。 */
    public boolean unauthorized(HttpExchange ex) {
        return currentUser(ex).isEmpty() && authEnabled();
    }

    /** 解析当前登录用户；免登录模式恒为 dev admin。 */
    public Optional<AuthContext> currentUser(HttpExchange ex) {
        try {
            if (!authEnabled()) {
                return Optional.of(new AuthContext("admin", AdminUser.ROLE_ADMIN));
            }
            Optional<AuthContext> ctx = sessions.resolve(ex);
            if (ctx.isEmpty()) return Optional.empty();
            return Optional.of(refreshSessionRole(ex, ctx.get()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 会话中的 role 在登录时写入；若库中已变更（如启动时恢复平台管理员），此处同步。 */
    private AuthContext refreshSessionRole(HttpExchange ex, AuthContext ctx) throws Exception {
        String fresh = store.lookupRole(ctx.username());
        if (fresh == null || fresh.equals(ctx.role())) return ctx;
        sessions.syncRole(ex, fresh);
        return new AuthContext(ctx.username(), fresh);
    }

    public String role(HttpExchange ex) {
        return currentUser(ex).map(AuthContext::role).orElse(null);
    }

    public String operator(HttpExchange ex) {
        return currentUser(ex).map(AuthContext::username).orElse(null);
    }

    /** GET /me：凭 Cookie 解析用户并返回画像。 */
    public Map<String, Object> userProfile(HttpExchange ex, String currentSpace, int pendingCount)
            throws Exception {
        Optional<AuthContext> ctx = currentUser(ex);
        if (ctx.isEmpty() && authEnabled()) return null;
        AuthContext user = ctx.orElse(new AuthContext("admin", AdminUser.ROLE_ADMIN));
        return AdminUserProfile.build(user, spaces, currentSpace, authEnabled(), pendingCount);
    }

    /** POST /login 成功后：用已校验用户组装画像（不读 Cookie）。 */
    public Map<String, Object> userProfile(AuthContext ctx, String currentSpace, int pendingCount)
            throws Exception {
        return AdminUserProfile.build(ctx, spaces, currentSpace, authEnabled(), pendingCount);
    }

    /**
     * 登录：安全门禁 → 校验密码 → 建立服务端会话。
     */
    public LoginResult attemptLogin(HttpExchange ex, String clientIp, String username, String password)
            throws Exception {
        if (!authEnabled()) {
            return LoginResult.invalid("当前环境未启用登录", null, 0);
        }
        return loginSecurity.guardLogin(clientIp, ex, username, password, store::authenticate,
                (e, u, r) -> sessions.establish(e, u, r));
    }

    /** @deprecated 请使用 {@link #attemptLogin}；保留供单测。 */
    public Optional<AuthContext> login(HttpExchange ex, String username, String password) throws Exception {
        LoginResult r = attemptLogin(ex, "127.0.0.1", username, password);
        return r.success() ? r.context() : Optional.empty();
    }

    public LoginSecurity loginSecurity() {
        return loginSecurity;
    }

    public String unlockUser(String username) throws Exception {
        if (username == null || username.isBlank()) return "username 不能为空";
        loginSecurity.unlockUser(username);
        return null;
    }

    public void logout(HttpExchange ex) throws IOException {
        try {
            sessions.logout(ex);
        } catch (SQLException e) {
            AdminSessionCookies.clearAll(ex);
        }
    }

    public List<Map<String, Object>> listUsers() throws Exception {
        List<Map<String, Object>> users = store.listUsers();
        if (spaces == null) return users;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : users) {
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            String username = (String) row.get("username");
            if (username != null) {
                enriched.put("spaces", spaces.listMembershipsForUser(username));
            }
            out.add(enriched);
        }
        return out;
    }

    public String saveUser(String username, String password, String role, String displayName)
            throws Exception {
        return saveUser(username, password, role, displayName, null, null, false);
    }

    /**
     * 创建/更新账号；平台用户可同步授权空间成员（space_key / space_role / all_spaces）。
     */
    public String saveUser(String username, String password, String role, String displayName,
            String spaceKey, String spaceRole, boolean allSpaces) throws Exception {
        String err = store.saveUser(username, password, role, displayName);
        if (err != null) return err;
        String platformRole = RbacRoles.normalizePlatformRole(
                role != null && !role.isBlank() ? role : store.lookupRole(username));
        if (platformRole == null) return "role 非法";
        if (!RbacRoles.requiresSpaceMembership(platformRole)) return null;
        boolean shouldProvision = spaceRole != null && !spaceRole.isBlank()
                || allSpaces
                || RbacRoles.provisionAllSpaces(spaceKey)
                || (role != null && (AdminUser.ROLE_EDITOR.equals(role) || AdminUser.ROLE_VIEWER.equals(role)));
        if (!shouldProvision && spaces != null) {
            shouldProvision = spaces.listMembershipsForUser(username).isEmpty();
        }
        if (!shouldProvision) return null;
        return RbacProvisioning.provisionSpaceAccess(
                spaces, username, platformRole, spaceKey, spaceRole, allSpaces);
    }

    public String disableUser(String username) throws Exception {
        return store.disableUser(username);
    }

    public String changePassword(String username, String currentPassword, String newPassword)
            throws Exception {
        return store.changePassword(username, currentPassword, newPassword);
    }

    public boolean authEnabled() {
        try {
            return store.authEnabled();
        } catch (Exception e) {
            return true;
        }
    }
}
