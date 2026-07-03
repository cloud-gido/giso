package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.giso.gateway.space.SpaceService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.sql.SQLException;
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

    public AdminAuth(GatewayConfig config, PostgresRegistryStore registryStore, SpaceService spaces)
            throws Exception {
        AdminSessionStore sessionStore;
        if ("postgres".equalsIgnoreCase(config.registryBackend) && registryStore != null) {
            this.store = PostgresAdminUserStore.create(registryStore.dataSource(), config);
            String schema = config.dbSchema == null || config.dbSchema.isBlank()
                    ? "public" : config.dbSchema;
            sessionStore = new PostgresAdminSessionStore(registryStore.dataSource(), schema);
        } else {
            this.store = new ConfigAdminUserStore(config);
            sessionStore = new InMemoryAdminSessionStore();
        }
        this.spaces = spaces;
        this.sessions = new AdminSessionManager(sessionStore);
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
            return sessions.resolve(ex);
        } catch (Exception e) {
            return Optional.empty();
        }
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
     * 登录：校验密码 → 建立服务端会话 → 返回用户上下文。
     * 失败返回 empty（用户名/密码错误或 auth 未启用）。
     */
    public Optional<AuthContext> login(HttpExchange ex, String username, String password) throws Exception {
        if (!authEnabled()) return Optional.empty();
        String role = store.authenticate(username, password);
        if (role == null) return Optional.empty();
        return Optional.of(sessions.establish(ex, username, role));
    }

    public void logout(HttpExchange ex) throws IOException {
        try {
            sessions.logout(ex);
        } catch (SQLException e) {
            AdminSessionCookies.clearAll(ex);
        }
    }

    public List<Map<String, Object>> listUsers() throws Exception {
        return store.listUsers();
    }

    public String saveUser(String username, String password, String role, String displayName) throws Exception {
        return store.saveUser(username, password, role, displayName);
    }

    public String disableUser(String username) throws Exception {
        return store.disableUser(username);
    }

    public boolean authEnabled() {
        try {
            return store.authEnabled();
        } catch (Exception e) {
            return true;
        }
    }
}
