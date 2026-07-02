-- 为已有 Doris 集群追加 space_key 列（多租户分析口径）
USE tracking;

ALTER TABLE ods_events ADD COLUMN IF NOT EXISTS space_key VARCHAR(32) DEFAULT 'default' COMMENT '业务空间（common.space）';

ALTER TABLE ods_events_quarantine ADD COLUMN IF NOT EXISTS space_key VARCHAR(32) DEFAULT 'default' COMMENT '业务空间';
