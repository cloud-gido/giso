package com.giso.gateway.auth;

import com.sun.net.httpserver.HttpExchange;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理台会话生命周期（唯一入口）。
 * <p>
 * 对齐成熟 B 端：opaque id + HttpOnly Cookie、密码不落 Cookie、登录撤销旧会话、活跃滑动续期。
 */
public final class AdminSessionManager {
    static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L;
    /** 滑动续期最小间隔，避免每个 API 都打 DB。 */
    private static final long TOUCH_INTERVAL_MS = 5 * 60 * 1000L;

    private final AdminSessionStore store;
    private final ConcurrentHashMap<String, Long> lastTouchMs = new ConcurrentHashMap<>();

    public AdminSessionManager(AdminSessionStore store) {
        this.store = store;
    }

    /**
     * 登录成功：清 Cookie → 撤销该用户其它会话 → 签发新 session → 写 Cookie。
     * 返回当前用户上下文（不依赖响应 Cookie 是否已被浏览器接收）。
     */
    public AuthContext establish(HttpExchange ex, String username, String role) throws SQLException {
        AdminSessionCookies.clearAll(ex);
        store.revokeAllForUser(username);
        String sessionId = store.create(username, role, SESSION_TTL_MS);
        AdminSessionCookies.writeSession(ex, sessionId);
        lastTouchMs.put(sessionId, System.currentTimeMillis());
        return new AuthContext(username, role);
    }

    /** 从请求 Cookie 解析会话；过期或无效返回 empty。 */
    public Optional<AuthContext> resolve(HttpExchange ex) throws SQLException {
        String sessionId = AdminSessionCookies.readSessionId(ex);
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        Optional<AdminSessionStore.Session> session = store.find(sessionId);
        if (session.isEmpty()) {
            lastTouchMs.remove(sessionId);
            return Optional.empty();
        }
        touchIfDue(sessionId);
        AdminSessionStore.Session s = session.get();
        return Optional.of(new AuthContext(s.username(), s.role()));
    }

    /** 将当前请求的会话 role 与库中保持一致。 */
    public void syncRole(HttpExchange ex, String role) throws SQLException {
        String sessionId = AdminSessionCookies.readSessionId(ex);
        if (sessionId == null || sessionId.isBlank() || role == null || role.isBlank()) return;
        store.updateRole(sessionId, role);
    }

    public void logout(HttpExchange ex) throws SQLException {
        String sessionId = AdminSessionCookies.readSessionId(ex);
        if (sessionId != null) {
            store.delete(sessionId);
            lastTouchMs.remove(sessionId);
        }
        AdminSessionCookies.clearAll(ex);
    }

    private void touchIfDue(String sessionId) throws SQLException {
        long now = System.currentTimeMillis();
        Long prev = lastTouchMs.get(sessionId);
        if (prev != null && now - prev < TOUCH_INTERVAL_MS) return;
        store.touch(sessionId, SESSION_TTL_MS);
        lastTouchMs.put(sessionId, now);
    }
}
