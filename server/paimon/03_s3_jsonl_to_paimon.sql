-- S3 Bronze JSONL → Paimon 批量回补（Gateway S3Sink 输出）
-- 路径：s3://bucket/giso/raw/dt=YYYY-MM-DD/*.jsonl

CREATE TABLE giso_s3_bronze (
  line STRING
) WITH (
  'connector' = 'filesystem',
  'path' = 's3://${S3_BUCKET}/${S3_PREFIX}raw/',
  'format' = 'text'
);

INSERT INTO giso.ods_events
SELECT
  JSON_VALUE(line, '$.log_id'),
  JSON_VALUE(line, '$.event'),
  CAST(JSON_VALUE(line, '$.stime') AS TIMESTAMP(3)),
  JSON_VALUE(line, '$.common.app_id'),
  JSON_VALUE(line, '$.common.platform'),
  JSON_VALUE(line, '$.common.did'),
  COALESCE(JSON_VALUE(line, '$.common.env'), 'prod'),
  COALESCE(JSON_VALUE(line, '$.common.space'), 'default'),
  JSON_VALUE(line, '$.common.app_vrsn'),
  JSON_VALUE(line, '$.page.pgid'),
  JSON_VALUE(line, '$.element.eid'),
  JSON_VALUE(line, '$.biz.code'),
  COALESCE(JSON_VALUE(line, '$._quality'), 'ok'),
  line,
  DATE_FORMAT(CAST(JSON_VALUE(line, '$.stime') AS TIMESTAMP(3)), 'yyyy-MM-dd')
FROM giso_s3_bronze
WHERE line IS NOT NULL AND line <> '';
