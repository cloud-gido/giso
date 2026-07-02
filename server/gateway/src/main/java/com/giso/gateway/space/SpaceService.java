package com.giso.gateway.space;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.auth.AdminUser;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 空间、成员、App Key → 空间映射（公司级多租户）。 */
public final class SpaceService {
    public static final String DEFAULT_SPACE = "default";
    public static final String HEADER_SPACE = "X-GISO-Space";

    private final HikariDataSource ds;
    private final String dbSchema;
    private final Map<String, String> appKeyToSpace = new ConcurrentHashMap<>();

    private SpaceService(HikariDataSource ds, String dbSchema) {
        this.ds = ds;
        this.dbSchema = dbSchema;
    }

    public static SpaceService create(HikariDataSource ds, GatewayConfig config) throws SQLException {
        SpaceService svc = new SpaceService(ds, config.dbSchema);
        svc.reloadAppKeys();
        svc.syncConfigAppKeys(config);
        return svc;
    }

    public void reloadAppKeys() throws SQLException {
        appKeyToSpace.clear();
        String sql = "SELECT app_key, space_key FROM %s.space_app_keys".formatted(dbSchema);
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                appKeyToSpace.put(rs.getString(1), rs.getString(2));
            }
        }
    }

    /** 将 Doppler/ConfigMap 中的 App Key 绑定到 default 空间（未显式映射时）。 */
    public void syncConfigAppKeys(GatewayConfig config) throws SQLException {
        if (config.appKeys == null || config.appKeys.isEmpty()) return;
        String sql = """
                INSERT INTO %s.space_app_keys (app_key, space_key) VALUES (?, ?)
                ON CONFLICT (app_key) DO NOTHING
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (String key : config.appKeys) {
                if (key == null || key.isBlank()) continue;
                String space = guessSpaceFromAppKey(key);
                ps.setString(1, key.trim());
                ps.setString(2, space);
                ps.executeUpdate();
                appKeyToSpace.putIfAbsent(key.trim(), space);
            }
        }
    }

    /** Phase 0：App Key 命名前缀推断空间。 */
    public static String guessSpaceFromAppKey(String appKey) {
        if (appKey == null) return DEFAULT_SPACE;
        String k = appKey.toLowerCase();
        if (k.startsWith("sports-") || k.startsWith("sport-")) return "sports";
        if (k.startsWith("video-") || k.startsWith("longvideo-")) return "longvideo";
        return DEFAULT_SPACE;
    }

    public String resolveSpaceForAppKey(String appKey) {
        if (appKey == null || appKey.isBlank()) return DEFAULT_SPACE;
        String mapped = appKeyToSpace.get(appKey.trim());
        if (mapped != null) return mapped;
        return guessSpaceFromAppKey(appKey);
    }

    public boolean hasAppKey(String appKey) {
        return appKey != null && !appKey.isBlank() && appKeyToSpace.containsKey(appKey.trim());
    }

    public List<Map<String, Object>> listSpaces() throws SQLException {
        String sql = """
                SELECT space_key, display_name, status, created_at::text
                FROM %s.spaces ORDER BY space_key
                """.formatted(dbSchema);
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("space_key", rs.getString(1));
                row.put("display_name", rs.getString(2));
                row.put("status", rs.getString(3));
                row.put("created_at", rs.getString(4));
                out.add(row);
            }
        }
        return out;
    }

    public String createSpace(String spaceKey, String displayName) throws SQLException {
        if (spaceKey == null || !spaceKey.matches("[a-z][a-z0-9_]{1,31}")) {
            return "space_key 须为小写 snake_case（2-32 字符）";
        }
        String sql = """
                INSERT INTO %s.spaces (space_key, display_name) VALUES (?, ?)
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            ps.setString(2, displayName == null || displayName.isBlank() ? spaceKey : displayName);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) return "空间已存在";
            throw e;
        }
        return null;
    }

    public List<Map<String, Object>> listMembers(String spaceKey) throws SQLException {
        String sql = """
                SELECT m.username, m.role, m.created_at::text, u.display_name
                FROM %s.space_members m
                LEFT JOIN %s.admin_users u ON u.username = m.username AND u.disabled_at IS NULL
                WHERE m.space_key = ? ORDER BY m.username
                """.formatted(dbSchema, dbSchema);
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("username", rs.getString(1));
                    row.put("role", rs.getString(2));
                    row.put("created_at", rs.getString(3));
                    row.put("display_name", rs.getString(4));
                    out.add(row);
                }
            }
        }
        return out;
    }

    public String saveMember(String spaceKey, String username, String role) throws SQLException {
        if (!List.of(AdminUser.ROLE_SPACE_ADMIN, AdminUser.ROLE_EDITOR, AdminUser.ROLE_VIEWER).contains(role)) {
            return "role 非法（space_admin/editor/viewer）";
        }
        String sql = """
                INSERT INTO %s.space_members (username, space_key, role) VALUES (?, ?, ?)
                ON CONFLICT (username, space_key) DO UPDATE SET role = EXCLUDED.role
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, spaceKey);
            ps.setString(3, role);
            ps.executeUpdate();
        }
        return null;
    }

    public String removeMember(String spaceKey, String username) throws SQLException {
        String sql = "DELETE FROM %s.space_members WHERE space_key = ? AND username = ?".formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            ps.setString(2, username);
            if (ps.executeUpdate() == 0) return "成员不存在";
        }
        return null;
    }

    public String bindAppKey(String spaceKey, String appKey) throws SQLException {
        if (appKey == null || appKey.isBlank()) return "app_key 不能为空";
        String sql = """
                INSERT INTO %s.space_app_keys (app_key, space_key) VALUES (?, ?)
                ON CONFLICT (app_key) DO UPDATE SET space_key = EXCLUDED.space_key
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, appKey.trim());
            ps.setString(2, spaceKey);
            ps.executeUpdate();
        }
        appKeyToSpace.put(appKey.trim(), spaceKey);
        return null;
    }

    public List<Map<String, Object>> listAppKeys(String spaceKey) throws SQLException {
        String sql = "SELECT app_key FROM %s.space_app_keys WHERE space_key = ? ORDER BY app_key"
                .formatted(dbSchema);
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(Map.of("app_key", rs.getString(1)));
                }
            }
        }
        return out;
    }

    /** 用户可访问的空间及在该空间的角色。 */
    public List<Map<String, Object>> spacesForUser(String username, String globalRole) throws SQLException {
        if (AdminUser.ROLE_SYSTEM_ADMIN.equals(globalRole)) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> sp : listSpaces()) {
                if (!"active".equals(sp.get("status"))) continue;
                Map<String, Object> row = new LinkedHashMap<>(sp);
                row.put("role", AdminUser.ROLE_SPACE_ADMIN);
                out.add(row);
            }
            return out;
        }
        String sql = """
                SELECT s.space_key, s.display_name, s.status, m.role
                FROM %s.space_members m
                JOIN %s.spaces s ON s.space_key = m.space_key
                WHERE m.username = ? AND s.status = 'active'
                ORDER BY s.space_key
                """.formatted(dbSchema, dbSchema);
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("space_key", rs.getString(1));
                    row.put("display_name", rs.getString(2));
                    row.put("status", rs.getString(3));
                    row.put("role", rs.getString(4));
                    out.add(row);
                }
            }
        }
        return out;
    }

    public String spaceRole(String username, String globalRole, String spaceKey) throws SQLException {
        if (AdminUser.ROLE_SYSTEM_ADMIN.equals(globalRole)) return AdminUser.ROLE_SPACE_ADMIN;
        String sql = """
                SELECT role FROM %s.space_members WHERE username = ? AND space_key = ?
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, spaceKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public boolean canAccessSpace(String username, String globalRole, String spaceKey) throws SQLException {
        return spaceRole(username, globalRole, spaceKey) != null;
    }

    public boolean spaceExists(String spaceKey) throws SQLException {
        String sql = "SELECT 1 FROM %s.spaces WHERE space_key = ? AND status = 'active'".formatted(dbSchema);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
