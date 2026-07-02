package com.giso.gateway.auth;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 本地 / yaml 模式：进程内会话（单副本开发联调）。 */
public final class InMemoryAdminSessionStore implements AdminSessionStore {
    private record Entry(String username, String role, long expiresAt) { }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    @Override
    public String create(String username, String role, long ttlMs) {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        sessions.put(id, new Entry(username, role, System.currentTimeMillis() + ttlMs));
        return id;
    }

    @Override
    public Optional<Session> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        Entry e = sessions.get(sessionId);
        if (e == null) return Optional.empty();
        if (e.expiresAt() <= System.currentTimeMillis()) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(new Session(e.username(), e.role()));
    }

    @Override
    public void delete(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }
}
