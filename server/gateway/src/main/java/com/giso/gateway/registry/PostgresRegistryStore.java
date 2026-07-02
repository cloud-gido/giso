package com.giso.gateway.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giso.gateway.DbMigrationSql;
import com.giso.gateway.DbMigrator;
import com.giso.gateway.GatewayConfig;
import com.giso.gateway.space.SpaceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生产：注册表持久化到 PostgreSQL（与 GIDO/DataEase 共用 RDS，库名 giso）。 */
public final class PostgresRegistryStore implements RegistryStore {
    private static final ObjectMapper M = new ObjectMapper();

    private final HikariDataSource ds;
    private final String dbSchema;
    private final Path bootstrapYamlDir;

    private PostgresRegistryStore(HikariDataSource ds, String dbSchema, Path bootstrapYamlDir) {
        this.ds = ds;
        this.dbSchema = dbSchema;
        this.bootstrapYamlDir = bootstrapYamlDir;
    }

    public static PostgresRegistryStore create(GatewayConfig config) throws Exception {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.dbUser);
        hc.setPassword(config.dbPassword);
        hc.setMaximumPoolSize(4);
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(5_000);
        hc.setInitializationFailTimeout(5_000);
        hc.setPoolName("giso-registry");

        HikariDataSource ds = new HikariDataSource(hc);
        Path bootstrap = config.registryBootstrapFromYaml ? Path.of(config.schemaDir) : null;
        PostgresRegistryStore store = new PostgresRegistryStore(ds, config.dbSchema, bootstrap);
        DbMigrator migrator = new DbMigrator(ds, config.dbSchema, PostgresRegistryStore.class);
        migrator.migrate();
        store.bootstrapIfEmpty();
        migrator.syncDefaultRegistryToLongvideo();
        return store;
    }

    public String jdbcUrl() {
        return ds.getJdbcUrl();
    }

    public String dbSchema() {
        return dbSchema;
    }

    public HikariDataSource dataSource() {
        return ds;
    }

    public String dbUser() {
        return ds.getUsername();
    }

    public String dbPassword() {
        return ds.getPassword();
    }

    @Override
    public RegistrySnapshot load() throws Exception {
        Map<String, Map<String, Map<String, Map<String, Object>>>> bySpace = new LinkedHashMap<>();
        String sql = """
                SELECT space_key, kind, entry_key, body::text
                FROM %s.registry_entries
                WHERE deleted_at IS NULL
                ORDER BY space_key, kind, entry_key
                """.formatted(dbSchema);
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String spaceKey = rs.getString(1);
                String kind = rs.getString(2);
                String key = rs.getString(3);
                Map<String, Object> body = M.readValue(rs.getString(4), new TypeReference<>() { });
                bySpace.computeIfAbsent(spaceKey, k -> RegistryKinds.emptyTables())
                        .get(kind).put(key, body);
            }
        }
        return new RegistrySnapshot(bySpace, fetchGlobalRevision());
    }

    @Override
    public long fetchGlobalRevision() throws SQLException {
        String sql = "SELECT value->>'revision' FROM %s.registry_meta WHERE key = 'global_revision'"
                .formatted(dbSchema);
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) return 0;
            String rev = rs.getString(1);
            return rev == null ? 0 : Long.parseLong(rev);
        }
    }

    @Override
    public WriteResult upsert(String spaceKey, String kind, Map<String, Object> item, String operator)
            throws Exception {
        String idField = RegistryKinds.idField(kind);
        String key = String.valueOf(item.get(idField));
        String status = resolveStatus(item);
        String bodyJson = M.writeValueAsString(item);

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> before = fetchBody(c, spaceKey, kind, key);
                String action = before == null ? "create" : "update";
                String upsert = """
                        INSERT INTO %s.registry_entries
                            (space_key, kind, entry_key, body, status, revision, created_by, updated_by)
                        VALUES (?, ?, ?, ?::jsonb, ?, 1, ?, ?)
                        ON CONFLICT (space_key, kind, entry_key) DO UPDATE SET
                            body = EXCLUDED.body,
                            status = EXCLUDED.status,
                            revision = %s.registry_entries.revision + 1,
                            updated_at = now(),
                            updated_by = EXCLUDED.updated_by,
                            deleted_at = NULL
                        """.formatted(dbSchema, dbSchema);
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setString(1, spaceKey);
                    ps.setString(2, kind);
                    ps.setString(3, key);
                    ps.setString(4, bodyJson);
                    ps.setString(5, status);
                    ps.setString(6, operator);
                    ps.setString(7, operator);
                    ps.executeUpdate();
                }
                insertAudit(c, spaceKey, kind, key, action, before, item, operator);
                if (SpaceService.DEFAULT_SPACE.equals(spaceKey)) {
                    mirrorUpsert(c, "longvideo", kind, key, bodyJson, status, operator);
                }
                long revision = bumpRevision(c);
                notifyReload(c, revision);
                c.commit();
                return WriteResult.ok(revision);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Override
    public WriteResult delete(String spaceKey, String kind, String key, String operator) throws Exception {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> before = fetchBody(c, spaceKey, kind, key);
                if (before == null) {
                    c.rollback();
                    return WriteResult.fail("不存在: " + key);
                }
                String del = """
                        UPDATE %s.registry_entries
                        SET deleted_at = now(), updated_at = now(), updated_by = ?
                        WHERE space_key = ? AND kind = ? AND entry_key = ? AND deleted_at IS NULL
                        """.formatted(dbSchema);
                try (PreparedStatement ps = c.prepareStatement(del)) {
                    ps.setString(1, operator);
                    ps.setString(2, spaceKey);
                    ps.setString(3, kind);
                    ps.setString(4, key);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return WriteResult.fail("不存在: " + key);
                    }
                }
                insertAudit(c, spaceKey, kind, key, "delete", before, null, operator);
                if (SpaceService.DEFAULT_SPACE.equals(spaceKey)) {
                    mirrorDelete(c, "longvideo", kind, key, operator);
                }
                long revision = bumpRevision(c);
                notifyReload(c, revision);
                c.commit();
                return WriteResult.ok(revision);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Override
    public String backendName() {
        return "postgres";
    }

    @Override
    public boolean ping() {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> meta() throws Exception {
        long revision = fetchGlobalRevision();
        int entries = 0;
        String countSql = "SELECT COUNT(*) FROM %s.registry_entries WHERE deleted_at IS NULL".formatted(dbSchema);
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(countSql)) {
            rs.next();
            entries = rs.getInt(1);
        }
        return Map.of(
                "backend", "postgres",
                "revision", revision,
                "entries", entries,
                "schema", dbSchema);
    }

    @Override
    public List<Map<String, Object>> audit(String spaceKey, String kind, String key, int limit) throws Exception {
        int lim = Math.min(Math.max(limit, 1), 200);
        String sql = """
                SELECT id, kind, entry_key, action, before_body::text, after_body::text,
                       operator, comment, created_at
                FROM %s.registry_audit
                WHERE space_key = ?
                  AND (? = '' OR kind = ?) AND (? = '' OR entry_key = ?)
                ORDER BY id DESC
                LIMIT ?
                """.formatted(dbSchema);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            ps.setString(2, kind == null ? "" : kind);
            ps.setString(3, kind == null ? "" : kind);
            ps.setString(4, key == null ? "" : key);
            ps.setString(5, key == null ? "" : key);
            ps.setInt(6, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong(1));
                    row.put("kind", rs.getString(2));
                    row.put("entry_key", rs.getString(3));
                    row.put("action", rs.getString(4));
                    row.put("before", parseJsonOrNull(rs.getString(5)));
                    row.put("after", parseJsonOrNull(rs.getString(6)));
                    row.put("operator", rs.getString(7));
                    row.put("comment", rs.getString(8));
                    row.put("created_at", rs.getTimestamp(9).toInstant().toString());
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Override
    public WriteResult publish(String spaceKey, String kind, String key, String operator) throws Exception {
        return transitionStatus(spaceKey, kind, key, operator, "live", "publish", null);
    }

    @Override
    public WriteResult deprecate(String spaceKey, String kind, String key, String operator) throws Exception {
        return transitionStatus(spaceKey, kind, key, operator, "deprecated", "deprecate", null);
    }

    @Override
    public WriteResult approve(String spaceKey, String kind, String key, String operator) throws Exception {
        return transitionStatus(spaceKey, kind, key, operator, "live", "approve", "pending");
    }

    @Override
    public WriteResult reject(String spaceKey, String kind, String key, String operator) throws Exception {
        return transitionStatus(spaceKey, kind, key, operator, "draft", "reject", "pending");
    }

    private WriteResult transitionStatus(String spaceKey, String kind, String key, String operator,
            String targetStatus, String action, String requiredFrom) throws Exception {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> before = fetchBody(c, spaceKey, kind, key);
                if (before == null) {
                    c.rollback();
                    return WriteResult.fail("不存在: " + key);
                }
                String current = String.valueOf(before.getOrDefault("status", "live"));
                if (requiredFrom != null && !requiredFrom.equals(current)) {
                    c.rollback();
                    return WriteResult.fail("状态不符：需要 " + requiredFrom + "，当前 " + current);
                }
                Map<String, Object> after = new LinkedHashMap<>(before);
                after.put("status", targetStatus);
                String bodyJson = M.writeValueAsString(after);
                String upd = """
                        UPDATE %s.registry_entries
                        SET body = ?::jsonb, status = ?, revision = revision + 1,
                            updated_at = now(), updated_by = ?
                        WHERE space_key = ? AND kind = ? AND entry_key = ? AND deleted_at IS NULL
                        """.formatted(dbSchema);
                try (PreparedStatement ps = c.prepareStatement(upd)) {
                    ps.setString(1, bodyJson);
                    ps.setString(2, targetStatus);
                    ps.setString(3, operator);
                    ps.setString(4, spaceKey);
                    ps.setString(5, kind);
                    ps.setString(6, key);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return WriteResult.fail("不存在: " + key);
                    }
                }
                insertAudit(c, spaceKey, kind, key, action, before, after, operator);
                long revision = bumpRevision(c);
                notifyReload(c, revision);
                c.commit();
                return WriteResult.ok(revision);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private Map<String, Object> parseJsonOrNull(String json) throws Exception {
        if (json == null || json.isBlank()) return null;
        return M.readValue(json, new TypeReference<>() { });
    }

    private void bootstrapIfEmpty() throws Exception {
        if (bootstrapYamlDir == null || !Files.isDirectory(bootstrapYamlDir)) return;
        String countSql = "SELECT COUNT(*) FROM %s.registry_entries".formatted(dbSchema);
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(countSql)) {
            rs.next();
            if (rs.getLong(1) > 0) return;
        }
        Yaml yaml = new Yaml();
        for (var e : RegistryKinds.FILES.entrySet()) {
            String kind = e.getKey();
            Path file = bootstrapYamlDir.resolve(e.getValue()[0]);
            if (!Files.exists(file)) continue;
            Map<String, Object> doc = yaml.load(Files.readString(file, StandardCharsets.UTF_8));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get(e.getValue()[1]);
            for (Map<String, Object> item : items) {
                upsert(SpaceService.DEFAULT_SPACE, kind, item, "bootstrap");
            }
        }
    }

    private Map<String, Object> fetchBody(Connection c, String spaceKey, String kind, String key) throws Exception {
        String sql = """
                SELECT body::text FROM %s.registry_entries
                WHERE space_key = ? AND kind = ? AND entry_key = ? AND deleted_at IS NULL
                """.formatted(dbSchema);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            ps.setString(2, kind);
            ps.setString(3, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return M.readValue(rs.getString(1), new TypeReference<>() { });
            }
        }
    }

    private void insertAudit(Connection c, String spaceKey, String kind, String key, String action,
            Map<String, Object> before, Map<String, Object> after, String operator) throws Exception {
        String sql = """
                INSERT INTO %s.registry_audit
                    (space_key, kind, entry_key, action, before_body, after_body, operator)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """.formatted(dbSchema);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spaceKey);
            ps.setString(2, kind);
            ps.setString(3, key);
            ps.setString(4, action);
            ps.setString(5, before == null ? null : M.writeValueAsString(before));
            ps.setString(6, after == null ? null : M.writeValueAsString(after));
            ps.setString(7, operator);
            ps.executeUpdate();
        }
    }

    private long bumpRevision(Connection c) throws SQLException {
        String sql = """
                UPDATE %s.registry_meta
                SET value = jsonb_set(
                    value, '{revision}',
                    to_jsonb((COALESCE((value->>'revision')::bigint, 0) + 1))::jsonb)
                WHERE key = 'global_revision'
                RETURNING (value->>'revision')::bigint
                """.formatted(dbSchema);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) throw new SQLException("global_revision missing");
            return rs.getLong(1);
        }
    }

    private void notifyReload(Connection c, long revision) throws SQLException {
        String payload = "{\"revision\":" + revision + "}";
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_notify('giso_registry', ?)")) {
            ps.setString(1, payload);
            ps.execute();
        }
    }

    private static String resolveStatus(Map<String, Object> item) {
        Object st = item.get("status");
        if (st == null || String.valueOf(st).isBlank()) return "live";
        return String.valueOf(st);
    }

    private void mirrorUpsert(Connection c, String targetSpace, String kind, String key,
            String bodyJson, String status, String operator) throws SQLException {
        String upsert = """
                INSERT INTO %s.registry_entries
                    (space_key, kind, entry_key, body, status, revision, created_by, updated_by)
                VALUES (?, ?, ?, ?::jsonb, ?, 1, ?, ?)
                ON CONFLICT (space_key, kind, entry_key) DO UPDATE SET
                    body = EXCLUDED.body,
                    status = EXCLUDED.status,
                    revision = %s.registry_entries.revision + 1,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by,
                    deleted_at = NULL
                """.formatted(dbSchema, dbSchema);
        try (PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, targetSpace);
            ps.setString(2, kind);
            ps.setString(3, key);
            ps.setString(4, bodyJson);
            ps.setString(5, status);
            ps.setString(6, operator);
            ps.setString(7, operator);
            ps.executeUpdate();
        }
    }

    private void mirrorDelete(Connection c, String targetSpace, String kind, String key, String operator)
            throws SQLException {
        String del = """
                UPDATE %s.registry_entries
                SET deleted_at = now(), updated_at = now(), updated_by = ?
                WHERE space_key = ? AND kind = ? AND entry_key = ? AND deleted_at IS NULL
                """.formatted(dbSchema);
        try (PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, operator);
            ps.setString(2, targetSpace);
            ps.setString(3, kind);
            ps.setString(4, key);
            ps.executeUpdate();
        }
    }
}
