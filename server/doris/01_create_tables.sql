-- ============================================================
-- 埋点数据 Doris 落地层（ODS）
-- 链路：SDK → 网关（校验）→ Kafka(events_raw / events_quarantine) → Routine Load → Doris
-- ============================================================

CREATE DATABASE IF NOT EXISTS tracking;
USE tracking;

-- ── 事件明细表（ODS）────────────────────────────────────────
-- 明细模型（DUPLICATE KEY），高频公共字段拉平成列做过滤/聚合，
-- 长尾参数保留 JSON 列，新增参数无需改表（注册表加字段即可查 params['xxx']）。
CREATE TABLE IF NOT EXISTS ods_events (
    event_date   DATE         NOT NULL COMMENT '分区日期（stime）',
    event        VARCHAR(32)  NOT NULL COMMENT '标准事件名',
    stime        DATETIME(3)  NOT NULL COMMENT '服务端接收时间（分析口径）',
    ctime        BIGINT       COMMENT '客户端毫秒时间戳（排序用）',
    log_id       VARCHAR(64)  COMMENT '事件唯一ID（去重）',

    -- 公共参数（高频过滤列）
    app_id       VARCHAR(32),
    platform     VARCHAR(16)  COMMENT 'android/ios/web/server',
    app_vrsn     VARCHAR(32),
    did          VARCHAR(64),
    uid          VARCHAR(64),
    session_id   VARCHAR(64),
    channel      VARCHAR(64),
    env          VARCHAR(16)  DEFAULT 'prod' COMMENT 'prod/test，与 ClickHouse 一致',

    -- 页面上下文
    pgid         VARCHAR(64),
    ref_pgid     VARCHAR(64),
    ref_eid      VARCHAR(64),
    pg_stay      BIGINT       COMMENT '页面停留ms（仅page_exit）',
    pg_params    JSON         COMMENT '页面参数',

    -- 元素上下文（仅元素事件）
    eid          VARCHAR(64),
    mod          VARCHAR(64),
    pos          INT,
    exp_dur      BIGINT       COMMENT '曝光时长ms',
    exp_ratio    DOUBLE       COMMENT '最大可视比例',
    el_params    JSON         COMMENT '元素参数（含继承）',

    -- 业务事件（仅biz_event）
    biz_code     VARCHAR(64),
    biz_params   JSON,

    quality      VARCHAR(16)  COMMENT 'ok/missing',
    common_ext   JSON         COMMENT '其余公共参数（机型/网络/语言等）',
    pt           JSON         COMMENT '后台参数透传包（推荐trace/赔率版本等），原样落地不校验'
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
    "replication_num" = "1"   -- 生产按集群副本数调整
);

-- ── 隔离区表（错误事件，可回放/排障）────────────────────────
CREATE TABLE IF NOT EXISTS ods_events_quarantine (
    event_date   DATE         NOT NULL,
    stime        DATETIME(3)  NOT NULL,
    event        VARCHAR(32),
    app_id       VARCHAR(32),
    platform     VARCHAR(16),
    did          VARCHAR(64),
    issues       JSON         COMMENT '校验失败明细',
    raw          JSON         COMMENT '完整原始信封'
)
DUPLICATE KEY(event_date, stime)
PARTITION BY RANGE(event_date) ()
DISTRIBUTED BY HASH(did) BUCKETS AUTO
PROPERTIES (
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-30",   -- 隔离区保留30天
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "replication_num" = "1"
);
