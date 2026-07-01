-- =============================================================================
-- GISO schema 一次性授权（RDS 主账号执行，连接数据库 giso）
-- =============================================================================
-- 应用账号（Doppler INFRA_GISO_DB_SERVICE_USER，如 giso-user）通常只有 CONNECT，
-- 无法在库内 CREATE SCHEMA。须主账号先建 schema 并授权，Gateway 才能自动迁移建表。
--
-- 用法（psql 或 RDS Query Editor）：
--   \c giso
--   \i tools/registry/bootstrap_schema.sql
-- 或将下方 SQL 中的 giso-user 换成实际用户名后执行。
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS giso AUTHORIZATION "giso-user";

GRANT ALL ON SCHEMA giso TO "giso-user";
GRANT CREATE ON SCHEMA giso TO "giso-user";

ALTER DEFAULT PRIVILEGES IN SCHEMA giso
    GRANT ALL ON TABLES TO "giso-user";
ALTER DEFAULT PRIVILEGES IN SCHEMA giso
    GRANT ALL ON SEQUENCES TO "giso-user";
