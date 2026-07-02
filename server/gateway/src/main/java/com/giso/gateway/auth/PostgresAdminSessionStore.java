package com.giso.gateway.auth;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL 会话：多副本共享、退出即失效。 */
public final class PostgresAdminSessionStore implements AdminSessionStore {
    private final DataSource ds;
    private final String dbSchema;

    public PostgresAdminSessionStore(DataSource ds, String dbSchema) {
        this.ds = ds;
        this.dbSchema = dbSchema == null || dbSchema.isBlank() ? "public" : dbSchema;
    }

    @Override
    public String create(String username, String role, long ttlMs) throws SQLException {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        Instant expires = Instant.now().plusMillis(ttlMs);
        String sql = """
                INSERT INTO %s.admin_sessions (session_id, username, role, expires_at)
                VALUES (?, ?, ?, ?)
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, username);
            ps.setString(3, role);
            ps.setTimestamp(4, java.sql.Timestamp.from(expires));
            ps.executeUpdate();
        }
        return id;
    }

    @Override
    public Optional<Session> find(String sessionId) throws SQLException {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        String sql = """
                SELECT username, role FROM %s.admin_sessions
                WHERE session_id = ? AND expires_at > now()
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Session(rs.getString(1), rs.getString(2)));
            }
        }
    }

    @Override
    public void delete(String sessionId) throws SQLException {
        if (sessionId == null || sessionId.isBlank()) return;
        String sql = "DELETE FROM %s.admin_sessions WHERE session_id = ?".formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    private void purgeExpired() throws SQLException {
        String sql = "DELETE FROM %s.admin_sessions WHERE expires_at <= now()".formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
