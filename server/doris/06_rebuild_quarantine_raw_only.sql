-- ============================================================
-- 隔离区表重建：raw-only 入库（全量重灌 Kafka 历史）
-- 适用：ods_events_quarantine 大量 errorRows / loadedRows=0
-- 执行前请替换 MSK broker / 账号（本地可删 SASL 相关 property）
-- ============================================================
USE tracking;

-- 1. 停掉旧 Routine Load（名称以 SHOW ROUTINE LOAD 为准）
-- STOP ROUTINE LOAD FOR tracking.load_ods_quarantine;

-- 2. 删表重建（会清空 Doris 内已有隔离区数据，从 Kafka 从头消费补回）
DROP TABLE IF EXISTS ods_events_quarantine;

CREATE TABLE ods_events_quarantine (
    event_date   DATE         NOT NULL COMMENT '分区日期',
    stime        DATETIME(3)  NOT NULL COMMENT '事件时间',
    raw          STRING       NOT NULL COMMENT '完整 Kafka JSON 原文'
)
DUPLICATE KEY(event_date, stime)
PARTITION BY RANGE(event_date) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-365",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "replication_num" = "1"
);

-- 3. 新 consumer group + OFFSET_BEGINNING，避免沿用旧 offset
-- 只映射整包 JSON；分区时间用入库时刻，避免 stime/ctime=0 落到 1970 分区失败
CREATE ROUTINE LOAD tracking.load_ods_quarantine ON ods_events_quarantine
COLUMNS(
    raw,
    stime = now(),
    event_date = curdate()
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "10000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "<b-1.host:9096,b-2.host:9096,b-3.host:9096>",
    "kafka_topic" = "giso_events_quarantine",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_giso_ods_quarantine_v8",
    "property.security.protocol" = "SASL_SSL",
    "property.sasl.mechanism" = "SCRAM-SHA-512",
    "property.sasl.username" = "<MSK_USERNAME>",
    "property.sasl.password" = "<MSK_PASSWORD>"
);

-- 4. 验证
-- SHOW ROUTINE LOAD FOR tracking.load_ods_quarantine;
-- SELECT count(*) FROM ods_events_quarantine;
-- SELECT stime, left(raw, 120) FROM ods_events_quarantine ORDER BY stime DESC LIMIT 5;
-- SELECT get_json_string(raw, '$.event'), get_json_string(raw, '$._issues[0].msg'), count(*)
--   FROM ods_events_quarantine GROUP BY 1, 2 ORDER BY 3 DESC LIMIT 20;
