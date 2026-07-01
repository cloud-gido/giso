package com.giso.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 将 classpath 迁移脚本中的 ${schema} 替换为实际 schema（默认 public，对齐 GIDO）。 */
public final class DbMigrationSql {
    private DbMigrationSql() { }

    public static String load(Class<?> anchor, String resourcePath, String dbSchema) throws IOException {
        String sql;
        try (var in = anchor.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException(resourcePath + " not found on classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return applySchema(sql, dbSchema);
    }

    public static String applySchema(String sql, String dbSchema) {
        return sql.replace("${schema}", quoteIdent(dbSchema));
    }

    public static String quoteIdent(String name) {
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid schema name: " + name);
        }
        return name;
    }

    public static boolean isBuiltinPublic(String dbSchema) {
        return dbSchema != null && dbSchema.equalsIgnoreCase("public");
    }
}
