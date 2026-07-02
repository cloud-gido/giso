-- 注册表按空间隔离；账号全局角色改为 system_admin / user

ALTER TABLE ${schema}.registry_entries
    ADD COLUMN IF NOT EXISTS space_key TEXT NOT NULL DEFAULT 'default';

ALTER TABLE ${schema}.registry_audit
    ADD COLUMN IF NOT EXISTS space_key TEXT NOT NULL DEFAULT 'default';

ALTER TABLE ${schema}.registry_entries DROP CONSTRAINT IF EXISTS registry_entries_pkey;
ALTER TABLE ${schema}.registry_entries
    ADD PRIMARY KEY (space_key, kind, entry_key);

DROP INDEX IF EXISTS idx_registry_kind_status;
CREATE INDEX IF NOT EXISTS idx_registry_kind_status
    ON ${schema}.registry_entries (space_key, kind, status)
    WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS idx_registry_updated;
CREATE INDEX IF NOT EXISTS idx_registry_updated
    ON ${schema}.registry_entries (space_key, updated_at DESC);

DROP INDEX IF EXISTS idx_registry_pending;
CREATE INDEX IF NOT EXISTS idx_registry_pending
    ON ${schema}.registry_entries (space_key, updated_at DESC)
    WHERE status = 'pending' AND deleted_at IS NULL;

DROP INDEX IF EXISTS idx_audit_entry;
CREATE INDEX IF NOT EXISTS idx_audit_entry
    ON ${schema}.registry_audit (space_key, kind, entry_key, created_at DESC);

-- 全局 admin → system_admin；editor/viewer 迁入 default 空间成员
INSERT INTO ${schema}.space_members (username, space_key, role)
SELECT username, 'default', role
FROM ${schema}.admin_users
WHERE role IN ('editor', 'viewer') AND disabled_at IS NULL
ON CONFLICT (username, space_key) DO NOTHING;

UPDATE ${schema}.admin_users SET role = 'system_admin' WHERE role = 'admin';
UPDATE ${schema}.admin_users SET role = 'user' WHERE role IN ('editor', 'viewer');

ALTER TABLE ${schema}.admin_users DROP CONSTRAINT IF EXISTS admin_users_role_check;
ALTER TABLE ${schema}.admin_users ADD CONSTRAINT admin_users_role_check
    CHECK (role IN ('system_admin', 'user'));

-- 默认空间管理员：现有 system_admin 加入各空间 space_admin（便于管理）
INSERT INTO ${schema}.space_members (username, space_key, role)
SELECT u.username, s.space_key, 'space_admin'
FROM ${schema}.admin_users u
CROSS JOIN ${schema}.spaces s
WHERE u.role = 'system_admin' AND u.disabled_at IS NULL AND s.status = 'active'
ON CONFLICT (username, space_key) DO NOTHING;
