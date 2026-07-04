package com.giso.gateway;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * PostgreSQL 结构迁移：按版本号严格顺序执行，每条脚本仅运行一次。
 * <p>
 * 顺序：V1 注册表 → V2 账号 → V3 审批 → V4 空间 → V5 注册表按空间隔离。
 * 数据类同步（如 default→longvideo 注册表）在 bootstrap 之后单独执行，不占用版本号。
 */
public final class DbMigrator {
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "registry", "/db/V1__registry.sql"),
            new Migration(2, "admin_users", "/db/V2__admin_users.sql"),
            new Migration(3, "approval", "/db/V3__approval.sql"),
            new Migration(4, "spaces", "/db/V4__spaces.sql"),
            new Migration(5, "registry_space", "/db/V5__registry_space.sql"),
            new Migration(6, "system_settings", "/db/V6__system_settings.sql"),
            new Migration(7, "admin_sessions", "/db/V7__admin_sessions.sql"),
            new Migration(8, "login_security", "/db/V8__login_security.sql"));

    /** default → longvideo 注册表复制（幂等）；须在 V5 之后且 default 有数据时执行。 */
    public static final String SYNC_LONGVIDEO_SQL = "/db/sync_longvideo_registry.sql";

    private final DataSource ds;
    private final String dbSchema;
    private final Class<?> resourceAnchor;

    public DbMigrator(DataSource ds, String dbSchema, Class<?> resourceAnchor) {
        this.ds = ds;
        this.dbSchema = dbSchema;
        this.resourceAnchor = resourceAnchor;
    }

    public void migrate() throws IOException, SQLException {
        ensureSchema();
        ensureMigrationsTable();
        backfillIfLegacyDatabase();
        for (Migration m : MIGRATIONS) {
            if (isApplied(m.version())) continue;
            runScript(m.resourcePath());
            recordApplied(m.version(), m.description());
            System.out.println("[giso-db] applied migration V" + m.version() + " (" + m.description() + ")");
        }
    }

    public void syncDefaultRegistryToLongvideo() throws IOException, SQLException {
        if (!isApplied(5)) {
            throw new SQLException("cannot sync longvideo registry before V5 (registry_space) is applied");
        }
        runScript(SYNC_LONGVIDEO_SQL);
    }

    private void ensureMigrationsTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS %s.schema_migrations (
                    version     INT PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """.formatted(DbMigrationSql.quoteIdent(dbSchema));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * 已用旧逻辑（每次启动重跑 SQL）建好的库：按现有对象回填版本号，避免 V3/V5 等重复执行。
     */
    private void backfillIfLegacyDatabase() throws SQLException {
        if (migrationCount() > 0) return;
        if (!tableExists("registry_meta")) return;

        stampIf(1, tableExists("registry_entries"));
        stampIf(2, tableExists("admin_users"));
        stampIf(3, tableExists("admin_users") && adminUsersAllowEditor());
        stampIf(4, tableExists("spaces"));
        stampIf(5, tableExists("registry_entries") && columnExists("registry_entries", "space_key"));
        stampIf(6, tableExists("system_settings"));
        stampIf(7, tableExists("admin_sessions"));
        stampIf(8, tableExists("admin_users") && columnExists("admin_users", "locked_until"));

        if (migrationCount() > 0) {
            System.out.println("[giso-db] backfilled schema_migrations for legacy database ("
                    + migrationCount() + " versions)");
        }
    }

    private void stampIf(int version, boolean ok) throws SQLException {
        if (!ok) return;
        Migration m = MIGRATIONS.stream().filter(x -> x.version() == version).findFirst().orElse(null);
        if (m == null) return;
        if (isApplied(version)) return;
        recordApplied(version, m.description() + " (backfill)");
    }

    private int migrationCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM %s.schema_migrations".formatted(DbMigrationSql.quoteIdent(dbSchema));
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean isApplied(int version) throws SQLException {
        String sql = "SELECT 1 FROM %s.schema_migrations WHERE version = ?"
                .formatted(DbMigrationSql.quoteIdent(dbSchema));
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void recordApplied(int version, String description) throws SQLException {
        String sql = """
                INSERT INTO %s.schema_migrations (version, description) VALUES (?, ?)
                ON CONFLICT (version) DO NOTHING
                """.formatted(DbMigrationSql.quoteIdent(dbSchema));
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setString(2, description);
            ps.executeUpdate();
        }
    }

    private boolean tableExists(String table) throws SQLException {
        String sql = """
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dbSchema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        String sql = """
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dbSchema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** V3 将 admin_users.role 扩为 admin/editor/viewer。 */
    private boolean adminUsersAllowEditor() throws SQLException {
        String sql = """
                SELECT 1 FROM information_schema.check_constraints cc
                JOIN information_schema.constraint_column_usage ccu
                  ON cc.constraint_name = ccu.constraint_name AND cc.constraint_schema = ccu.constraint_schema
                WHERE ccu.table_schema = ? AND ccu.table_name = 'admin_users'
                  AND ccu.column_name = 'role' AND cc.check_clause LIKE '%editor%'
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dbSchema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void runScript(String resourcePath) throws IOException, SQLException {
        String sql = DbMigrationSql.load(resourceAnchor, resourcePath, dbSchema);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new SQLException("migration failed (" + resourcePath + "): " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> appliedVersions() throws SQLException {
        String sql = """
                SELECT version, description, applied_at::text
                FROM %s.schema_migrations ORDER BY version
                """.formatted(DbMigrationSql.quoteIdent(dbSchema));
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("version", rs.getInt(1));
                row.put("description", rs.getString(2));
                row.put("applied_at", rs.getString(3));
                out.add(row);
            }
        }
        return out;
    }

    private record Migration(int version, String description, String resourcePath) { }

    private void ensureSchema() throws SQLException {
        if (DbMigrationSql.isBuiltinPublic(dbSchema)) return;
        String ident = DbMigrationSql.quoteIdent(dbSchema);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + ident);
        } catch (SQLException e) {
            if (schemaExists()) return;
            throw new SQLException(
                    "schema '" + dbSchema + "' missing and DB user cannot CREATE SCHEMA; "
                            + "set GISO_DB_SCHEMA=public (GIDO default) or run tools/registry/bootstrap_schema.sql",
                    e);
        }
    }

    private boolean schemaExists() throws SQLException {
        String sql = "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dbSchema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
