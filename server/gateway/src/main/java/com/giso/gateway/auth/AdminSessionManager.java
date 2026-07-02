package com.giso.gateway.auth;

import com.sun.net.httpserver.HttpExchange;

import java.sql.SQLException;

/**
 * 管理台会话：登录签发 opaque id，请求凭 Cookie 查会话，退出删服务端记录并清 Cookie。
 * <p>
 * 对齐常见 B 端产品：密码只在校验时用一次，不写入 Cookie；退出后服务端会话立即失效。
 */
public final class AdminSessionManager {
    static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L;

    private final AdminSessionStore store;

    public AdminSessionManager(AdminSessionStore store) {
        this.store = store;
    }

    public void login(HttpExchange ex, String username, String role) throws SQLException {
        AdminSessionCookies.clearAll(ex);
        String sessionId = store.create(username, role, SESSION_TTL_MS);
        AdminSessionCookies.writeSession(ex, sessionId);
    }

    public AuthContext resolve(HttpExchange ex) throws SQLException {
        String sessionId = AdminSessionCookies.readSessionId(ex);
        if (sessionId == null) return null;
        return store.find(sessionId)
                .map(s -> new AuthContext(s.username(), s.role()))
                .orElse(null);
    }

    public void logout(HttpExchange ex) throws SQLException {
        String sessionId = AdminSessionCookies.readSessionId(ex);
        store.delete(sessionId);
        AdminSessionCookies.clearAll(ex);
    }
}
