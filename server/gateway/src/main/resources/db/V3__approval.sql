-- 审批流：pending 状态 + approve 审计；editor 角色

ALTER TABLE giso.registry_entries DROP CONSTRAINT IF EXISTS registry_entries_status_check;
ALTER TABLE giso.registry_entries ADD CONSTRAINT registry_entries_status_check
    CHECK (status IN ('draft', 'dev', 'testing', 'pending', 'live', 'deprecated'));

ALTER TABLE giso.registry_audit DROP CONSTRAINT IF EXISTS registry_audit_action_check;
ALTER TABLE giso.registry_audit ADD CONSTRAINT registry_audit_action_check
    CHECK (action IN ('create', 'update', 'delete', 'publish', 'deprecate', 'approve', 'reject'));

ALTER TABLE giso.admin_users DROP CONSTRAINT IF EXISTS admin_users_role_check;
ALTER TABLE giso.admin_users ADD CONSTRAINT admin_users_role_check
    CHECK (role IN ('admin', 'editor', 'viewer'));

CREATE INDEX IF NOT EXISTS idx_registry_pending
    ON giso.registry_entries (updated_at DESC)
    WHERE status = 'pending' AND deleted_at IS NULL;
