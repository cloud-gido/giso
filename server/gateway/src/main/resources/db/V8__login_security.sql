-- 管理台登录防暴力破解：账号锁定 + IP 限流
ALTER TABLE ${schema}.admin_users
    ADD COLUMN IF NOT EXISTS login_failed_count INT NOT NULL DEFAULT 0;
ALTER TABLE ${schema}.admin_users
    ADD COLUMN IF NOT EXISTS login_failed_at TIMESTAMPTZ;
ALTER TABLE ${schema}.admin_users
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS ${schema}.admin_login_ip_throttle (
    ip_key         TEXT PRIMARY KEY,
    window_start   TIMESTAMPTZ NOT NULL,
    attempt_count  INT NOT NULL DEFAULT 0,
    blocked_until  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_admin_users_locked
    ON ${schema}.admin_users (locked_until) WHERE locked_until IS NOT NULL;
