package com.giso.gateway.auth;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** PostgreSQL 持久化登录失败计数（多副本共享状态）。 */
public final class PostgresLoginSecurityStore implements LoginSecurityStore {
    private final HikariDataSource ds;
    private final String dbSchema;
    private final LoginSecurityConfig config;

    public PostgresLoginSecurityStore(HikariDataSource ds, String dbSchema, LoginSecurityConfig config) {
        this.ds = ds;
        this.dbSchema = dbSchema == null || dbSchema.isBlank() ? "public" : dbSchema;
        this.config = config;
    }

    @Override
    public Optional<BlockStatus> checkBlock(String ip, String username) throws SQLException {
        Instant now = Instant.now();
        Optional<Instant> ipUntil = ipBlockedUntil(ip);
        if (ipUntil.isPresent() && ipUntil.get().isAfter(now)) {
            return Optional.of(new BlockStatus("ip", LoginSecurityStore.retryAfterSec(ipUntil.get())));
        }
        if (username == null || username.isBlank()) return Optional.empty();
        Optional<Instant> userUntil = userLockedUntil(username);
        if (userUntil.isPresent() && userUntil.get().isAfter(now)) {
            return Optional.of(new BlockStatus("account", LoginSecurityStore.retryAfterSec(userUntil.get())));
        }
        return Optional.empty();
    }

    @Override
    public FailureStats recordFailure(String ip, String username) throws SQLException {
        int ipCount = recordIpFailure(ip);
        int userCount = 0;
        Integer remaining = null;
        if (username != null && !username.isBlank() && userExists(username)) {
            userCount = recordUserFailure(username);
            if (userCount < config.maxAttemptsPerUser) {
                remaining = config.maxAttemptsPerUser - userCount;
            }
        }
        int delayBase = Math.max(ipCount, userCount);
        int delayMs = Math.min(3000, config.delayMsPerFailure * Math.max(1, delayBase));
        return new FailureStats(userCount, remaining, delayMs);
    }

    @Override
    public void recordSuccess(String username) throws SQLException {
        if (username == null || username.isBlank()) return;
        String sql = """
                UPDATE %s.admin_users
                SET login_failed_count = 0, login_failed_at = NULL, locked_until = NULL, updated_at = now()
                WHERE username = ? AND disabled_at IS NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }

    @Override
    public void unlockUser(String username) throws SQLException {
        if (username == null || username.isBlank()) return;
        String sql = """
                UPDATE %s.admin_users
                SET login_failed_count = 0, login_failed_at = NULL, locked_until = NULL, updated_at = now()
                WHERE username = ?
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            if (ps.executeUpdate() == 0) return;
        }
    }

    private Optional<Instant> ipBlockedUntil(String ip) throws SQLException {
        String sql = """
                SELECT blocked_until FROM %s.admin_login_ip_throttle WHERE ip_key = ?
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? Optional.empty() : Optional.of(ts.toInstant());
            }
        }
    }

    private Optional<Instant> userLockedUntil(String username) throws SQLException {
        String sql = """
                SELECT locked_until FROM %s.admin_users
                WHERE username = ? AND disabled_at IS NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? Optional.empty() : Optional.of(ts.toInstant());
            }
        }
    }

    private int recordIpFailure(String ip) throws SQLException {
        Instant now = Instant.now();
        String select = """
                SELECT window_start, attempt_count, blocked_until
                FROM %s.admin_login_ip_throttle WHERE ip_key = ?
                """.formatted(dbSchema);
        Instant windowStart = now;
        int count = 1;
        Instant blockedUntil = null;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(select)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Instant prevStart = rs.getTimestamp(1).toInstant();
                    int prevCount = rs.getInt(2);
                    Timestamp prevBlocked = rs.getTimestamp(3);
                    if (prevBlocked != null && prevBlocked.toInstant().isAfter(now)) {
                        return prevCount;
                    }
                    if (minutesBetween(prevStart, now) < config.ipWindowMinutes) {
                        windowStart = prevStart;
                        count = prevCount + 1;
                    }
                }
            }
        }
        if (count >= config.maxAttemptsPerIp) {
            blockedUntil = now.plusSeconds(config.ipBlockMinutes * 60L);
        }
        String upsert = """
                INSERT INTO %s.admin_login_ip_throttle (ip_key, window_start, attempt_count, blocked_until)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (ip_key) DO UPDATE SET
                    window_start = EXCLUDED.window_start,
                    attempt_count = EXCLUDED.attempt_count,
                    blocked_until = EXCLUDED.blocked_until
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, ip);
            ps.setTimestamp(2, Timestamp.from(windowStart));
            ps.setInt(3, count);
            ps.setTimestamp(4, blockedUntil == null ? null : Timestamp.from(blockedUntil));
            ps.executeUpdate();
        }
        return count;
    }

    private int recordUserFailure(String username) throws SQLException {
        Instant now = Instant.now();
        int count = 1;
        Instant windowStart = now;
        Instant lockedUntil = null;
        String select = """
                SELECT login_failed_count, login_failed_at, locked_until
                FROM %s.admin_users WHERE username = ? AND disabled_at IS NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(select)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp failedAt = rs.getTimestamp(2);
                    Timestamp prevLocked = rs.getTimestamp(3);
                    if (prevLocked != null && prevLocked.toInstant().isAfter(now)) {
                        return rs.getInt(1);
                    }
                    if (failedAt != null && minutesBetween(failedAt.toInstant(), now) < config.attemptWindowMinutes) {
                        windowStart = failedAt.toInstant();
                        count = rs.getInt(1) + 1;
                    }
                }
            }
        }
        if (count >= config.maxAttemptsPerUser) {
            lockedUntil = now.plusSeconds(config.lockoutMinutes * 60L);
        }
        String update = """
                UPDATE %s.admin_users
                SET login_failed_count = ?, login_failed_at = ?, locked_until = ?, updated_at = now()
                WHERE username = ? AND disabled_at IS NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(update)) {
            ps.setInt(1, count);
            ps.setTimestamp(2, Timestamp.from(windowStart));
            ps.setTimestamp(3, lockedUntil == null ? null : Timestamp.from(lockedUntil));
            ps.setString(4, username);
            ps.executeUpdate();
        }
        return count;
    }

    private boolean userExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM %s.admin_users WHERE username = ? AND disabled_at IS NULL"
                .formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static long minutesBetween(Instant a, Instant b) {
        return Math.abs(b.getEpochSecond() - a.getEpochSecond()) / 60;
    }
}
