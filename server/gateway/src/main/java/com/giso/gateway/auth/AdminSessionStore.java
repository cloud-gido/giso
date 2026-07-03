package com.giso.gateway.auth;

import java.sql.SQLException;
import java.util.Optional;

/** 管理台登录会话存储（opaque session id → 用户）。 */
public interface AdminSessionStore {

    record Session(String username, String role) { }

    String create(String username, String role, long ttlMs) throws SQLException;

    Optional<Session> find(String sessionId) throws SQLException;

    void delete(String sessionId) throws SQLException;

    /** 登录时撤销该用户其它会话（管理台单端登录）。 */
    void revokeAllForUser(String username) throws SQLException;

    /** 滑动续期：活跃请求延长 expires_at。 */
    void touch(String sessionId, long ttlMs) throws SQLException;
}
