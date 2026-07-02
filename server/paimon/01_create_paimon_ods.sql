-- Paimon ODS：与 Doris tracking.ods_events 字段对齐
-- 替换 ${WAREHOUSE} 为 s3://your-bucket/giso/paimon

CREATE DATABASE IF NOT EXISTS giso;

CREATE TABLE IF NOT EXISTS giso.ods_events (
  log_id           STRING,
  event            STRING,
  stime            TIMESTAMP(3),
  app_id           STRING,
  platform         STRING,
  did              STRING,
  env              STRING,
  space_key        STRING,
  app_vrsn         STRING,
  pgid             STRING,
  eid              STRING,
  biz_code         STRING,
  quality          STRING,
  payload          STRING,
  dt               STRING,
  PRIMARY KEY (log_id, dt) NOT ENFORCED
) PARTITIONED BY (dt)
WITH (
  'connector' = 'paimon',
  'path' = '${WAREHOUSE}/ods_events',
  'bucket' = '2',
  'changelog-producer' = 'input',
  'file.format' = 'parquet'
);

CREATE TABLE IF NOT EXISTS giso.ods_events_quarantine (
  log_id           STRING,
  event            STRING,
  stime            TIMESTAMP(3),
  did              STRING,
  space_key        STRING,
  payload          STRING,
  dt               STRING,
  PRIMARY KEY (log_id, dt) NOT ENFORCED
) PARTITIONED BY (dt)
WITH (
  'connector' = 'paimon',
  'path' = '${WAREHOUSE}/ods_events_quarantine',
  'bucket' = '2',
  'file.format' = 'parquet'
);
