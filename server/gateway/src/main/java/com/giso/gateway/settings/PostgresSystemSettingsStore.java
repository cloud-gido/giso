package com.giso.gateway.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** PostgreSQL 持久化系统设置（platform system_admin 可写）。 */
public final class PostgresSystemSettingsStore implements SystemSettingsStore {
    private static final ObjectMapper M = new ObjectMapper();
    private final DataSource ds;
    private final String schema;

    public PostgresSystemSettingsStore(DataSource ds, String schema) {
        this.ds = ds;
        this.schema = schema;
    }

    @Override
    public Optional<JsonNode> get(String key) throws Exception {
        String sql = "SELECT setting_value::text FROM " + q(schema) + ".system_settings WHERE setting_key = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(M.readTree(rs.getString(1)));
            }
        }
    }

    @Override
    public Map<String, JsonNode> getAll() throws Exception {
        String sql = "SELECT setting_key, setting_value::text FROM " + q(schema) + ".system_settings";
        Map<String, JsonNode> out = new LinkedHashMap<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), M.readTree(rs.getString(2)));
            }
        }
        return out;
    }

    @Override
    public void put(String key, JsonNode value, String operator) throws Exception {
        String sql = """
                INSERT INTO %s.system_settings (setting_key, setting_value, updated_by)
                VALUES (?, ?::jsonb, ?)
                ON CONFLICT (setting_key) DO UPDATE SET
                  setting_value = EXCLUDED.setting_value,
                  updated_at = now(),
                  updated_by = EXCLUDED.updated_by
                """.formatted(q(schema));
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value.toString());
            ps.setString(3, operator);
            ps.executeUpdate();
        }
    }

    private static String q(String schema) {
        if (schema == null || schema.isBlank() || "public".equals(schema)) return "public";
        return "\"" + schema.replace("\"", "") + "\"";
    }
}
