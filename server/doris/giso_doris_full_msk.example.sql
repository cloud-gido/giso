-- =============================================================================
-- GISO · Doris 全量初始化（建库建表 + MSK Routine Load）
-- 测试环境 MSK：SASL_SSL，端口 9096，Topic 前缀 giso_
--
-- 用法：
--   1. 替换下方 <MSK_USERNAME>、<MSK_PASSWORD>
--   2. 确认 kafka_broker_list 与 MSK 一致
--   3. 在 Doris FE 执行：
--      mysql -h <doris-fe> -P9030 -uroot < giso_doris_full_msk.sql
--
-- 前置：MSK 已创建 topic
--   giso_events_raw / giso_events_raw_test / giso_events_quarantine
-- =============================================================================

-- =============================================================================
-- 一、建库建表
-- =============================================================================

CREATE DATABASE IF NOT EXISTS tracking;
USE tracking;

CREATE TABLE IF NOT EXISTS ods_events (
    event_date   DATE         NOT NULL COMMENT '分区日期（stime）',
    event        VARCHAR(32)  NOT NULL COMMENT '标准事件名',
    stime        DATETIME(3)  NOT NULL COMMENT '服务端接收时间（分析口径）',
    ctime        BIGINT       COMMENT '客户端毫秒时间戳（排序用）',
    log_id       VARCHAR(64)  COMMENT '事件唯一ID（去重）',

    app_id       VARCHAR(32),
    platform     VARCHAR(16)  COMMENT 'android/ios/web/server',
    app_vrsn     VARCHAR(32),
    did          VARCHAR(64),
    uid          VARCHAR(64),
    session_id   VARCHAR(64),
    channel      VARCHAR(64),
    env          VARCHAR(16)  DEFAULT 'prod' COMMENT 'prod/test',

    pgid         VARCHAR(64),
    ref_pgid     VARCHAR(64),
    ref_eid      VARCHAR(64),
    pg_stay      BIGINT       COMMENT '页面停留ms（仅page_exit）',
    pg_params    JSON         COMMENT '页面参数',

    eid          VARCHAR(64),
    mod          VARCHAR(64),
    pos          INT,
    exp_dur      BIGINT       COMMENT '曝光时长ms',
    exp_ratio    DOUBLE       COMMENT '最大可视比例',
    el_params    JSON         COMMENT '元素参数（含继承）',

    biz_code     VARCHAR(64),
    biz_params   JSON,

    quality      VARCHAR(16)  COMMENT 'ok/missing',
    common_ext   JSON         COMMENT '其余公共参数',
    pt           JSON         COMMENT '后台参数透传包'
)
DUPLICATE KEY(event_date, event, stime)
PARTITION BY RANGE(event_date) ()
DISTRIBUTED BY HASH(did) BUCKETS AUTO
PROPERTIES (
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-365",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS ods_events_quarantine (
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

-- 若表是早期版本、没有 env 列，取消下面注释执行一次：
-- ALTER TABLE ods_events ADD COLUMN env VARCHAR(16) DEFAULT 'prod' COMMENT 'prod/test' AFTER channel;

-- =============================================================================
-- 二、停掉旧 Routine Load（重复执行时取消注释）
-- =============================================================================

-- STOP ROUTINE LOAD FOR tracking.load_ods_events;
-- STOP ROUTINE LOAD FOR tracking.load_ods_events_test;
-- STOP ROUTINE LOAD FOR tracking.load_ods_quarantine;

-- =============================================================================
-- 三、Routine Load（MSK SASL_SSL）
-- =============================================================================

CREATE ROUTINE LOAD tracking.load_ods_events ON ods_events
COLUMNS(
    stime_ms, ctime, log_id, event,
    app_id, platform, app_vrsn, did, uid, session_id, channel, env,
    pgid, ref_pgid, ref_eid, pg_stay, pg_params,
    eid, mod, pos, exp_dur, exp_ratio, el_params,
    biz_code, biz_params, quality, common_ext, pt,
    stime = from_unixtime(stime_ms / 1000),
    event_date = to_date(from_unixtime(stime_ms / 1000)),
    env = IFNULL(env, 'prod')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.stime\",\"$.ctime\",\"$.log_id\",\"$.event\",
        \"$.common.app_id\",\"$.common.platform\",\"$.common.app_vrsn\",\"$.common.did\",\"$.common.uid\",\"$.common.session_id\",\"$.common.channel\",\"$.common.env\",
        \"$.page.pgid\",\"$.page.ref_pgid\",\"$.page.ref_eid\",\"$.page.pg_stay\",\"$.page.pg_params\",
        \"$.element.eid\",\"$.element.mod\",\"$.element.pos\",\"$.element.exp_dur\",\"$.element.exp_ratio\",\"$.element.params\",
        \"$.biz.code\",\"$.biz.params\",\"$._quality\",\"$.common\",\"$.pt\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "b-1.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096,b-2.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096,b-3.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096",
    "kafka_topic" = "giso_events_raw",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_giso_ods_events",
    "property.security.protocol" = "SASL_SSL",
    "property.sasl.mechanism" = "SCRAM-SHA-512",
    "property.sasl.username" = "<MSK_USERNAME>",
    "property.sasl.password" = "<MSK_PASSWORD>"
);

CREATE ROUTINE LOAD tracking.load_ods_events_test ON ods_events
COLUMNS(
    stime_ms, ctime, log_id, event,
    app_id, platform, app_vrsn, did, uid, session_id, channel, env,
    pgid, ref_pgid, ref_eid, pg_stay, pg_params,
    eid, mod, pos, exp_dur, exp_ratio, el_params,
    biz_code, biz_params, quality, common_ext, pt,
    stime = from_unixtime(stime_ms / 1000),
    event_date = to_date(from_unixtime(stime_ms / 1000)),
    env = IFNULL(env, 'test')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.stime\",\"$.ctime\",\"$.log_id\",\"$.event\",
        \"$.common.app_id\",\"$.common.platform\",\"$.common.app_vrsn\",\"$.common.did\",\"$.common.uid\",\"$.common.session_id\",\"$.common.channel\",\"$.common.env\",
        \"$.page.pgid\",\"$.page.ref_pgid\",\"$.page.ref_eid\",\"$.page.pg_stay\",\"$.page.pg_params\",
        \"$.element.eid\",\"$.element.mod\",\"$.element.pos\",\"$.element.exp_dur\",\"$.element.exp_ratio\",\"$.element.params\",
        \"$.biz.code\",\"$.biz.params\",\"$._quality\",\"$.common\",\"$.pt\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "b-1.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096,b-2.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096,b-3.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096",
    "kafka_topic" = "giso_events_raw_test",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_giso_ods_events_test",
    "property.security.protocol" = "SASL_SSL",
    "property.sasl.mechanism" = "SCRAM-SHA-512",
    "property.sasl.username" = "<MSK_USERNAME>",
    "property.sasl.password" = "<MSK_PASSWORD>"
);

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
    "kafka_broker_list" = "b-1.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096,b-2.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096,b-3.gamelinelabdevkafkakaf.qfouvr.c2.kafka.sa-east-1.amazonaws.com:9096",
    "kafka_topic" = "giso_events_quarantine",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_giso_ods_quarantine_v8",
    "property.security.protocol" = "SASL_SSL",
    "property.sasl.mechanism" = "SCRAM-SHA-512",
    "property.sasl.username" = "<MSK_USERNAME>",
    "property.sasl.password" = "<MSK_PASSWORD>"
);

-- =============================================================================
-- 四、验证
-- =============================================================================
-- SHOW ROUTINE LOAD FROM tracking;
-- SELECT env, event, count(*) FROM tracking.ods_events GROUP BY env, event;
-- SELECT * FROM tracking.ods_events ORDER BY stime DESC LIMIT 20;
