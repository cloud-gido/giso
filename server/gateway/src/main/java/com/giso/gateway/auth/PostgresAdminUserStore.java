package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理台账号持久化到 PostgreSQL（与注册表同库 giso）。 */
public final class PostgresAdminUserStore implements AdminUserStore {
    private final HikariDataSource ds;
    private final String dbSchema;
    private final List<AdminUser> seedUsers;

    private PostgresAdminUserStore(HikariDataSource ds, String dbSchema, List<AdminUser> seedUsers) {
        this.ds = ds;
        this.dbSchema = dbSchema;
        this.seedUsers = seedUsers;
    }

    public static PostgresAdminUserStore create(HikariDataSource ds, GatewayConfig config) throws Exception {
        PostgresAdminUserStore store = new PostgresAdminUserStore(
                ds, config.dbSchema, GatewayConfig.resolveAuthUsers(config));
        store.migrate();
        store.bootstrapIfEmpty();
        return store;
    }

    @Override
    public boolean authEnabled() {
        try {
            if (!seedUsers.isEmpty()) return true;
            return userCount() > 0;
        } catch (SQLException e) {
            return true;
        }
    }

    @Override
    public String authenticate(String username, String password) throws SQLException {
        if (username == null || password == null) return null;
        String sql = """
                SELECT password_hash, role FROM %s.admin_users
                WHERE username = ? AND disabled_at IS NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String hash = rs.getString(1);
                if (!BCrypt.checkpw(password, hash)) return null;
                return rs.getString(2);
            }
        }
    }

    @Override
    public List<Map<String, Object>> listUsers() throws SQLException {
        String sql = """
                SELECT username, role, display_name, created_at::text
                FROM %s.admin_users WHERE disabled_at IS NULL ORDER BY username
                """.formatted(dbSchema);
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("username", rs.getString(1));
                row.put("role", rs.getString(2));
                row.put("display_name", rs.getString(3));
                row.put("created_at", rs.getString(4));
                row.put("source", "postgres");
                out.add(row);
            }
        }
        return out;
    }

    @Override
    public String saveUser(String username, String password, String role, String displayName)
            throws SQLException {
        if (username == null || username.isBlank()) return "username 不能为空";
        boolean exists = fetchRole(username) != null;
        if (role == null || role.isBlank()) {
            if (!exists) return "新账号必须指定 role";
            role = fetchRole(username);
        }
        if (!java.util.Set.of(AdminUser.ROLE_ADMIN, AdminUser.ROLE_EDITOR, AdminUser.ROLE_VIEWER)
                .contains(role)) {
            return "role 非法（admin/editor/viewer）";
        }
        String disp = displayName == null || displayName.isBlank() ? username : displayName;
        if (!exists && (password == null || password.length() < 6)) {
            return "新账号密码至少 6 位";
        }
        String sql = exists && (password == null || password.isBlank())
                ? """
                UPDATE %s.admin_users SET role = ?, display_name = ?, updated_at = now(), disabled_at = NULL
                WHERE username = ?
                """.formatted(dbSchema)
                : """
                INSERT INTO %s.admin_users (username, password_hash, role, display_name)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (username) DO UPDATE SET
                    password_hash = EXCLUDED.password_hash,
                    role = EXCLUDED.role,
                    display_name = EXCLUDED.display_name,
                    updated_at = now(),
                    disabled_at = NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (exists && (password == null || password.isBlank())) {
                ps.setString(1, role);
                ps.setString(2, disp);
                ps.setString(3, username);
            } else {
                ps.setString(1, username);
                ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt(12)));
                ps.setString(3, role);
                ps.setString(4, disp);
            }
            ps.executeUpdate();
        }
        return null;
    }

    @Override
    public String disableUser(String username) throws SQLException {
        if (username == null || username.isBlank()) return "username 不能为空";
        String sql = """
                UPDATE %s.admin_users SET disabled_at = now(), updated_at = now()
                WHERE username = ? AND disabled_at IS NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            if (ps.executeUpdate() == 0) return "用户不存在或已禁用";
        }
        return null;
    }

    private String fetchRole(String username) throws SQLException {
        String sql = "SELECT role FROM %s.admin_users WHERE username = ? AND disabled_at IS NULL"
                .formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void migrate() throws IOException, SQLException {
        runSqlResource("/db/V2__admin_users.sql");
        runSqlResource("/db/V3__approval.sql");
    }

    private void runSqlResource(String path) throws IOException, SQLException {
        String sql;
        try (var in = PostgresAdminUserStore.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException(path + " not found");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private void bootstrapIfEmpty() throws SQLException {
        if (userCount() > 0 || seedUsers.isEmpty()) return;
        String upsert = """
                INSERT INTO %s.admin_users (username, password_hash, role, display_name)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (username) DO UPDATE SET
                    password_hash = EXCLUDED.password_hash,
                    role = EXCLUDED.role,
                    updated_at = now(),
                    disabled_at = NULL
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(upsert)) {
            for (AdminUser u : seedUsers) {
                ps.setString(1, u.username());
                ps.setString(2, BCrypt.hashpw(u.password(), BCrypt.gensalt(12)));
                ps.setString(3, u.role());
                ps.setString(4, u.username());
                ps.executeUpdate();
            }
        }
    }

    private int userCount() throws SQLException {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM %s.admin_users WHERE disabled_at IS NULL".formatted(dbSchema))) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
