-- 已有集群补 env 列（与 ClickHouse ods_events.env 对齐）
USE tracking;
ALTER TABLE ods_events ADD COLUMN env VARCHAR(16) DEFAULT 'prod' COMMENT 'prod/test' AFTER channel;
